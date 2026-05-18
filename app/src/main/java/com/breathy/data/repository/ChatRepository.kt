package com.breathy.data.repository

import com.breathy.data.models.Chat
import com.breathy.data.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Chat repository — Firestore CRUD for direct chats and messages.
 *
 * Features:
 * - Deterministic chat IDs: sorted userIds joined by "_"
 * - Get-or-create chat pattern
 * - Typing indicators with [Timestamp] expiry (3-second window)
 * - Real-time message observation with Flow
 * - Unread message counting and batch mark-as-read
 * - 30-second timeout on all network operations
 * - Proper listener cleanup via Flow's awaitClose
 */
class ChatRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "ChatRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val CHATS_COLLECTION = "chats"
        private const val MESSAGES_SUBCOLLECTION = "messages"
        private const val TYPING_EXPIRY_SECONDS = 3L
        private const val MESSAGE_PREVIEW_LENGTH = 100
        private const val DEFAULT_MESSAGE_LIMIT = 50L
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // ═══════════════════════════════════════════════════════════════════════════
    //  Get or create chat
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get an existing chat or create a new one with the other user.
     * Uses a deterministic chat ID (sorted UIDs joined by "_") so that
     * any two users always share exactly one chat document.
     */
    suspend fun getOrCreateChat(otherUserId: String): Result<Chat> = runCatching {
        val uid = currentUserId
        val chatId = Chat.chatId(uid, otherUserId)

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val chatRef = firestore.collection(CHATS_COLLECTION).document(chatId)

            // Try DEFAULT first (allows cache fallback for offline support),
            // then fall back to CACHE if server is unreachable.
            val chatDoc = try {
                chatRef.get(Source.DEFAULT).await()
            } catch (e: Exception) {
                Timber.w(e, "DEFAULT source failed for chat %s — trying CACHE", chatId)
                try {
                    chatRef.get(Source.CACHE).await()
                } catch (cacheEx: Exception) {
                    // Cache also failed — treat as non-existent so we create it
                    null
                }
            }

            if (chatDoc != null && chatDoc.exists()) {
                return@withTimeoutOrNull Chat.fromFirestoreMap(chatId, chatDoc.data ?: emptyMap())
            }

            // Chat document doesn't exist yet — create a new one.
            // If the document was created by the other user in the meantime,
            // Firestore's set() will overwrite, which is safe since the data
            // is identical (deterministic ID guarantees same participants).
            val chatData = mapOf(
                "participants" to listOf(uid, otherUserId).sorted(),
                "lastMessage" to "",
                "lastUpdated" to FieldValue.serverTimestamp(),
                "typing" to emptyMap<String, Any>()
            )
            chatRef.set(chatData).await()
            Unit

            Chat(
                id = chatId,
                participants = listOf(uid, otherUserId).sorted(),
                lastMessage = "",
                lastUpdated = com.google.firebase.Timestamp.now(),
                typing = emptyMap()
            )
        } ?: throw IllegalStateException("Get or create chat timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get or create chat with: %s", otherUserId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Get chats
    // ═══════════════════════════════════════════════════════════════════════════

    /** Get all chats for the current user, ordered by last update. */
    suspend fun getChats(): Result<List<Chat>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = firestore.collection(CHATS_COLLECTION)
                .whereArrayContains("participants", currentUserId)
                .orderBy("lastUpdated", Query.Direction.DESCENDING)
                .get(Source.DEFAULT)
                .await()
                Unit
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Chat.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get chats timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get chats")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Send message
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Send a message in a chat.
     * Updates the parent chat document with the message preview and clears
     * the sender's typing indicator.
     */
    suspend fun sendMessage(chatId: String, text: String): Result<Message> = runCatching {
        val uid = currentUserId
        if (text.isBlank()) throw IllegalArgumentException("Message cannot be empty")

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val messageData = mapOf(
                "senderId" to uid,
                "text" to text,
                "timestamp" to FieldValue.serverTimestamp(),
                "read" to false
            )

            val msgRef = firestore.collection(CHATS_COLLECTION).document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .add(messageData)
                .await()
                Unit

            // Update the parent chat document with preview and clear typing
            val previewText = text.take(MESSAGE_PREVIEW_LENGTH)
            firestore.collection(CHATS_COLLECTION).document(chatId).update(mapOf(
                "lastMessage" to previewText,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "typing.$uid" to FieldValue.delete()
            )).await()
            Unit

            Message(
                id = msgRef.id,
                senderId = uid,
                text = text,
                timestamp = com.google.firebase.Timestamp.now(),
                read = false
            )
        } ?: throw IllegalStateException("Send message timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to send message in chat: %s", chatId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Mark as read
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Mark all unread messages in a chat as read for the current user.
     * Uses a batch write for efficiency.
     */
    suspend fun markAsRead(chatId: String): Result<Unit> = runCatching {
        val uid = currentUserId

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Simplified query: only filter by "read == false" to avoid
            // requiring a composite index. We filter senderId client-side.
            val unreadSnapshot = firestore.collection(CHATS_COLLECTION).document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .whereEqualTo("read", false)
                .get(Source.DEFAULT)
                .await()
                Unit

            // Filter out the current user's own messages client-side
            val otherUserUnread = unreadSnapshot.documents.filter { doc ->
                doc.getString("senderId") != uid
            }

            if (otherUserUnread.isEmpty()) return@withTimeoutOrNull

            val batch = firestore.batch()
            for (doc in otherUserUnread) {
                batch.update(doc.reference, "read", true)
            }
            batch.commit().await()
            Unit
        } ?: throw IllegalStateException("Mark as read timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to mark messages as read in chat: %s", chatId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Typing indicator
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Set or clear the current user's typing indicator.
     * When [isTyping] is true, sets a Timestamp 3 seconds in the future.
     * The client is responsible for periodically refreshing the typing indicator.
     * When [isTyping] is false, deletes the typing entry.
     */
    suspend fun setTyping(chatId: String, isTyping: Boolean): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val chatRef = firestore.collection(CHATS_COLLECTION).document(chatId)
            if (isTyping) {
                // Set typing timestamp 3 seconds in the future
                val expiryTime = System.currentTimeMillis() / 1000 + TYPING_EXPIRY_SECONDS
                val typingTimestamp = com.google.firebase.Timestamp(expiryTime, 0)
                chatRef.update("typing.$currentUserId", typingTimestamp).await()
                Unit
            } else {
                chatRef.update("typing.$currentUserId", FieldValue.delete()).await()
                Unit
            }
        } ?: throw IllegalStateException("Set typing timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to set typing status in chat: %s", chatId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Get messages (one-time fetch)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Fetch messages for a chat, ordered by timestamp ascending. */
    suspend fun getMessages(
        chatId: String,
        limit: Int = DEFAULT_MESSAGE_LIMIT.toInt()
    ): Result<List<Message>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = firestore.collection(CHATS_COLLECTION).document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limitToLast(limit.toLong())
                .get(Source.DEFAULT)
                .await()
                Unit
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Message.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get messages timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get messages for chat: %s", chatId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Unread count
    // ═══════════════════════════════════════════════════════════════════════════

    /** Count unread messages from other users in a specific chat. */
    suspend fun getUnreadCountForChat(chatId: String): Result<Int> = runCatching {
        val uid = currentUserId
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val unread = firestore.collection(CHATS_COLLECTION).document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .whereEqualTo("read", false)
                .get(Source.DEFAULT)
                .await()
            // Only count messages from other users
            unread.documents.count { it.getString("senderId") != uid }
        } ?: 0
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get unread count for chat: %s", chatId)
    }

    /** Count the total number of unread messages across all chats. */
    suspend fun getUnreadCount(): Result<Int> = runCatching {
        val uid = currentUserId
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val chats = firestore.collection(CHATS_COLLECTION)
                .whereArrayContains("participants", uid)
                .get(Source.DEFAULT)
                .await()
                Unit

            var totalUnread = 0
            for (chatDoc in chats.documents) {
                // Use simple query (no composite index needed), filter client-side
                val unread = chatDoc.reference.collection(MESSAGES_SUBCOLLECTION)
                    .whereEqualTo("read", false)
                    .get()
                    .await()
                    Unit
                // Only count messages from other users
                totalUnread += unread.documents.count { it.getString("senderId") != uid }
            }
            totalUnread
        } ?: throw IllegalStateException("Get unread count timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get unread message count")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time observers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Observe all chats for the current user in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeChats(): Flow<List<Chat>> = callbackFlow {
        val uid = currentUserId
        val registration = firestore.collection(CHATS_COLLECTION)
            .whereArrayContains("participants", uid)
            .orderBy("lastUpdated", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeChats error")
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render. The listener stays
                    // active and will retry on the next Firestore sync.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val chats = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { Chat.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(chats)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Observe messages in a specific chat in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMessages(
        chatId: String,
        limit: Int = DEFAULT_MESSAGE_LIMIT.toInt()
    ): Flow<List<Message>> = callbackFlow {
        val registration = firestore.collection(CHATS_COLLECTION).document(chatId)
            .collection(MESSAGES_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeMessages error for chat: %s", chatId)
                    // Log the Firestore index creation URL if the error is due to
                    // a missing composite index. Firestore includes this URL in the
                    // error message, making it easy to create the index in the console.
                    val errorMsg = error.message ?: ""
                    if (errorMsg.contains("index")) {
                        Timber.e("Firestore index required for observeMessages. " +
                            "Check the Firebase Console for the auto-generated index creation URL. " +
                            "Error: %s", errorMsg)
                    }
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { Message.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(messages)
                }
            }
        awaitClose { registration.remove() }
    }
}

package breathy.com.data.repository

import breathy.com.data.models.CoachMessage
import breathy.com.data.models.MessageRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * AI Coach repository — manages conversational AI powered by Firebase Cloud Functions.
 *
 * The chat flow:
 * 1. User message is saved to Firestore (users/{uid}/coach_chats)
 * 2. The "openAIChat" Cloud Function is called with the message
 * 3. The assistant response is saved back to Firestore
 *
 * Features:
 * - Rate limiting: 5 messages per minute per user
 * - "openAIChat" callable Cloud Function for OpenAI API integration
 * - Real-time conversation observation via Flow
 * - Conversation history retrieval and clearing
 * - 30-second timeout on all network operations
 * - Proper listener cleanup via Flow's awaitClose
 */
class CoachRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions
) {

    companion object {
        private const val TAG = "CoachRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val CLOUD_FUNCTION_NAME = "openAIChat"
        private const val COACH_CHATS_SUBCOLLECTION = "coach_chats"
        private const val USERS_COLLECTION = "users"

        // Rate limiting: 5 messages per minute
        private const val RATE_LIMIT_MAX_MESSAGES = 5
        private const val RATE_LIMIT_WINDOW_MS = 60_000L // 1 minute
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    private val coachChatsCollection
        get() = firestore.collection(USERS_COLLECTION).document(currentUserId)
            .collection(COACH_CHATS_SUBCOLLECTION)

    // ═══════════════════════════════════════════════════════════════════════════
    //  Rate limiting
    // ═══════════════════════════════════════════════════════════════════════════

    /** Track message timestamps for client-side rate limiting. */
    private val messageTimestamps = mutableListOf<Long>()

    /**
     * Check if the user is within the rate limit.
     * @throws IllegalStateException if the rate limit is exceeded.
     */
    private fun checkRateLimit() {
        val now = System.currentTimeMillis()
        // Remove timestamps older than the rate limit window
        messageTimestamps.removeAll { it < now - RATE_LIMIT_WINDOW_MS }

        if (messageTimestamps.size >= RATE_LIMIT_MAX_MESSAGES) {
            val oldestInWindow = messageTimestamps.first()
            val retryAfterMs = RATE_LIMIT_WINDOW_MS - (now - oldestInWindow)
            val retryAfterSeconds = retryAfterMs / 1000
            throw IllegalStateException(
                "Rate limit exceeded. Please wait ${retryAfterSeconds}s before sending another message."
            )
        }
    }

    /** Record a message timestamp for rate limiting. */
    private fun recordMessageTimestamp() {
        messageTimestamps.add(System.currentTimeMillis())
        // Keep the list bounded
        if (messageTimestamps.size > RATE_LIMIT_MAX_MESSAGES * 2) {
            val cutoff = System.currentTimeMillis() - RATE_LIMIT_WINDOW_MS
            messageTimestamps.removeAll { it < cutoff }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Send message
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Send a message to the AI Coach.
     *
     * 1. Checks client-side rate limit (5 messages per minute)
     * 2. Saves the user message to Firestore
     * 3. Calls the "openAIChat" Cloud Function
     * 4. Saves the assistant response to Firestore
     * 5. Returns the assistant response
     *
     * @param content The user's message text.
     * @return Result containing the assistant's [CoachMessage] response.
     */
    suspend fun sendMessage(content: String): Result<CoachMessage> = runCatching {
        val uid = currentUserId
        if (content.isBlank()) throw IllegalArgumentException("Message cannot be empty")

        // Check rate limit before proceeding
        checkRateLimit()

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // 1. Save user message to Firestore
            val userMessageData = mapOf(
                "role" to MessageRole.USER.value,
                "content" to content,
                "timestamp" to FieldValue.serverTimestamp()
            )
            coachChatsCollection.add(userMessageData).await()
            Unit

            // Record the message timestamp for rate limiting
            recordMessageTimestamp()

            // 2. Call Cloud Function for AI response
            val functionData = mapOf(
                "message" to content,
                "userId" to uid
            )

            // Determine the assistant's response content
            val assistantContent: String = try {
                val result = functions.getHttpsCallable(CLOUD_FUNCTION_NAME)
                    .call(functionData)
                    .await()
                val responseData = result.getData() as? Map<*, *> ?: emptyMap<Any, Any?>()
                responseData["content"] as? String
                    ?: "I'm here for you. Keep going — you're stronger than you think!"
            } catch (e: Exception) {
                Timber.e(e, "Cloud Function call failed for user: %s", uid)
                // Fallback response when Cloud Function is unavailable
                "I'm having trouble connecting right now. Please try again in a moment. Remember, every craving you resist makes you stronger!"
            }

            // 3. Save assistant response to Firestore (ALWAYS, even for fallback)
            // This is critical: the UI relies on the Firestore snapshot listener
            // to display new messages. Without saving, the message won't appear.
            val assistantMessageData = mapOf(
                "role" to MessageRole.ASSISTANT.value,
                "content" to assistantContent,
                "timestamp" to FieldValue.serverTimestamp()
            )
            coachChatsCollection.add(assistantMessageData).await()
            Unit

            CoachMessage(
                role = MessageRole.ASSISTANT,
                content = assistantContent,
                timestamp = com.google.firebase.Timestamp.now()
            )
        } ?: throw IllegalStateException("AI Coach message timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to send coach message")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Chat history
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the conversation history for the current user.
     * @param limit Maximum number of messages to return (most recent).
     */
    suspend fun getChatHistory(limit: Int = 50): Result<List<CoachMessage>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = coachChatsCollection
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(limit.toLong())
                .get(Source.SERVER)
                .await()
                Unit
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { CoachMessage.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get chat history timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get conversation history")
    }

    /**
     * Clear the entire conversation history for the current user.
     * Deletes all documents in the coach_chats subcollection using batch writes.
     */
    suspend fun clearHistory(): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = coachChatsCollection.get().await()
            Unit
            if (snapshot.isEmpty) return@withTimeoutOrNull

            // Firestore batches support up to 500 operations
            val documents = snapshot.documents
            var batch = firestore.batch()
            var operationCount = 0

            for (doc in documents) {
                batch.delete(doc.reference)
                operationCount++
                if (operationCount >= 499) {
                    batch.commit().await()
                    Unit
                    batch = firestore.batch()
                    operationCount = 0
                }
            }

            if (operationCount > 0) {
                batch.commit().await()
                Unit
            }

            // Clear rate limit tracking
            messageTimestamps.clear()

            Timber.i("Coach chat history cleared for user: %s", currentUserId)
        } ?: throw IllegalStateException("Clear history timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to clear conversation")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time observers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Observe the conversation in real-time, ordered by timestamp ascending. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeConversation(limit: Int = 50): Flow<List<CoachMessage>> = callbackFlow {
        val uid = currentUserId
        val registration = firestore.collection(USERS_COLLECTION).document(uid)
            .collection(COACH_CHATS_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeConversation error for user: %s", uid)
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { CoachMessage.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(messages)
                }
            }
        awaitClose { registration.remove() }
    }
}

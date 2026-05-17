package com.breathy.data.repository

import com.breathy.data.models.Reply
import com.breathy.data.models.Story
import com.breathy.data.models.User
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
 * Story repository — Firestore CRUD for community stories and replies.
 *
 * Features:
 * - Paged story fetching with cursor-based pagination
 * - Like/unlike with Firestore transactions (atomic like count + likedBy)
 * - Real-time listener for individual story detail
 * - 30-second timeout on all network operations
 * - Proper listener cleanup via Flow's awaitClose
 */
class StoryRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "StoryRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val STORIES_COLLECTION = "stories"
        private const val REPLIES_SUBCOLLECTION = "replies"
        private const val USERS_COLLECTION = "users"
        private const val DEFAULT_PAGE_SIZE = 20
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ═══════════════════════════════════════════════════════════════════════════
    //  Get stories (paged)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch a page of stories, ordered by creation date descending.
     *
     * @param limit Maximum number of stories to return.
     * @param lastDocumentId The ID of the last document from the previous page,
     *                       or null for the first page.
     */
    suspend fun getStories(
        limit: Int = DEFAULT_PAGE_SIZE,
        lastDocumentId: String? = null
    ): Result<List<Story>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            var query = firestore.collection(STORIES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            if (lastDocumentId != null) {
                try {
                    val lastDoc = firestore.collection(STORIES_COLLECTION)
                        .document(lastDocumentId)
                        .get()
                        .await()
                    if (lastDoc.exists()) {
                        query = query.startAfter(lastDoc)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get cursor document for pagination — starting fresh")
                    // If cursor fetch fails, just start from the beginning
                }
            }

            val snapshot = query.get().await()
            Unit
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Story.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get stories timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get stories")
    }

    /** Fetch stories by a specific user. */
    suspend fun getStoriesByUser(
        userId: String,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Result<List<Story>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = firestore.collection(STORIES_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                Unit
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Story.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get stories by user timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get stories for user: %s", userId)
    }

    /** Fetch a single story by ID. */
    suspend fun getStory(storyId: String): Result<Story> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val document = try {
                firestore.collection(STORIES_COLLECTION)
                    .document(storyId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed for story %s — trying cache", storyId)
                firestore.collection(STORIES_COLLECTION)
                    .document(storyId)
                    .get(Source.CACHE)
                    .await()
            }
            if (!document.exists()) {
                throw NoSuchElementException("Story not found: $storyId")
            }
            Story.fromFirestoreMap(document.id, document.data ?: emptyMap())
        } ?: throw IllegalStateException("Get story timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get story: %s", storyId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Create / delete story
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new community story.
     * Denormalizes user info (nickname, photoURL, daysSmokeFree) from the user document.
     */
    suspend fun createStory(
        content: String,
        lifeChanges: List<String>
    ): Result<Story> = runCatching {
        val uid = currentUserId ?: throw IllegalStateException("Not authenticated")

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Fetch current user data for denormalized fields
            val userDoc = firestore.collection(USERS_COLLECTION).document(uid)
                .get(Source.SERVER)
                .await()
                Unit
            val user = User.fromFirestoreMap(userDoc.data ?: emptyMap())

            val storyData = mapOf(
                "userId" to uid,
                "nickname" to user.nickname,
                "photoURL" to user.photoURL,
                "content" to content,
                "lifeChanges" to lifeChanges,
                "daysSmokeFree" to user.daysSmokeFree,
                "likes" to 0L,
                "likedBy" to emptyList<String>(),
                "replyCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val docRef = firestore.collection(STORIES_COLLECTION).add(storyData).await()
            Unit

            Story(
                id = docRef.id,
                userId = uid,
                nickname = user.nickname,
                photoURL = user.photoURL,
                content = content,
                lifeChanges = lifeChanges,
                daysSmokeFree = user.daysSmokeFree,
                createdAt = com.google.firebase.Timestamp.now()
            )
        } ?: throw IllegalStateException("Create story timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to create story")
    }

    /**
     * Delete a story and all its replies.
     * Only the story author can delete their own story.
     */
    suspend fun deleteStory(storyId: String): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw IllegalStateException("Not authenticated")

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Verify ownership
            val storyDoc = firestore.collection(STORIES_COLLECTION)
                .document(storyId)
                .get(Source.SERVER)
                .await()
                Unit
            if (!storyDoc.exists()) {
                throw NoSuchElementException("Story not found: $storyId")
            }
            val storyUserId = storyDoc.getString("userId")
            if (storyUserId != uid) {
                throw SecurityException("You can only delete your own stories")
            }

            // Delete the story document and all replies subcollection
            val batch = firestore.batch()
            batch.delete(firestore.collection(STORIES_COLLECTION).document(storyId))

            val replies = firestore.collection(STORIES_COLLECTION).document(storyId)
                .collection(REPLIES_SUBCOLLECTION)
                .get()
                .await()
                Unit
            for (reply in replies.documents) {
                batch.delete(reply.reference)
            }
            batch.commit().await(); Unit
        } ?: throw IllegalStateException("Delete story timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to delete story: %s", storyId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Like / unlike — Firestore transactions for atomic updates
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Like a story. Uses a Firestore transaction to atomically:
     * 1. Increment the like count
     * 2. Add the user to likedBy
     * 3. Record the like in the user's givenLikes
     *
     * Idempotent — if already liked, does nothing.
     */
    suspend fun likeStory(storyId: String, userId: String): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val storyRef = firestore.collection(STORIES_COLLECTION).document(storyId)
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)

            firestore.runTransaction { transaction ->
                val storySnapshot = transaction.get(storyRef)
                if (!storySnapshot.exists()) {
                    throw NoSuchElementException("Story not found: $storyId")
                }

                val currentLikedBy = (storySnapshot.get("likedBy") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

                // Idempotent: already liked — do nothing
                if (currentLikedBy.contains(userId)) return@runTransaction

                transaction.update(storyRef, mapOf(
                    "likes" to FieldValue.increment(1),
                    "likedBy" to FieldValue.arrayUnion(userId)
                ))
                transaction.update(userRef, "givenLikes", FieldValue.arrayUnion(storyId))
            }.await()
            Unit
        } ?: throw IllegalStateException("Like story timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to like story: %s", storyId)
    }

    /**
     * Unlike a story. Uses a Firestore transaction to atomically:
     * 1. Decrement the like count
     * 2. Remove the user from likedBy
     * 3. Remove the like from the user's givenLikes
     *
     * Idempotent — if not liked, does nothing.
     */
    suspend fun unlikeStory(storyId: String, userId: String): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val storyRef = firestore.collection(STORIES_COLLECTION).document(storyId)
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)

            firestore.runTransaction { transaction ->
                val storySnapshot = transaction.get(storyRef)
                if (!storySnapshot.exists()) return@runTransaction

                val currentLikedBy = (storySnapshot.get("likedBy") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

                // Idempotent: not liked — do nothing
                if (!currentLikedBy.contains(userId)) return@runTransaction

                transaction.update(storyRef, mapOf(
                    "likes" to FieldValue.increment(-1),
                    "likedBy" to FieldValue.arrayRemove(userId)
                ))
                transaction.update(userRef, "givenLikes", FieldValue.arrayRemove(storyId))
            }.await()
            Unit
        } ?: throw IllegalStateException("Unlike story timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to unlike story: %s", storyId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Replies — paged
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch a page of replies for a story, ordered chronologically.
     *
     * @param storyId The parent story ID.
     * @param limit Maximum number of replies to return.
     * @param lastDocumentId The ID of the last reply from the previous page,
     *                       or null for the first page.
     */
    suspend fun getReplies(
        storyId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        lastDocumentId: String? = null
    ): Result<List<Reply>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            var query = firestore.collection(STORIES_COLLECTION).document(storyId)
                .collection(REPLIES_SUBCOLLECTION)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(limit.toLong())

            if (lastDocumentId != null) {
                try {
                    val lastDoc = firestore.collection(STORIES_COLLECTION).document(storyId)
                        .collection(REPLIES_SUBCOLLECTION)
                        .document(lastDocumentId)
                        .get()
                        .await()
                    if (lastDoc.exists()) {
                        query = query.startAfter(lastDoc)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get cursor document for replies pagination — starting fresh")
                }
            }

            val snapshot = query.get().await()
            Unit
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Reply.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get replies timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get replies for story: %s", storyId)
    }

    /**
     * Add a reply to a story.
     * Increments the replyCount on the parent story atomically.
     */
    suspend fun createReply(
        storyId: String,
        content: String,
        parentReplyId: String? = null
    ): Result<Reply> = runCatching {
        val uid = currentUserId ?: throw IllegalStateException("Not authenticated")

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Fetch current user data for denormalized fields
            val userDoc = firestore.collection(USERS_COLLECTION).document(uid)
                .get(Source.SERVER)
                .await()
                Unit
            val user = User.fromFirestoreMap(userDoc.data ?: emptyMap())

            val replyData = mutableMapOf<String, Any?>(
                "storyId" to storyId,
                "userId" to uid,
                "nickname" to user.nickname,
                "photoURL" to user.photoURL,
                "content" to content,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (parentReplyId != null) {
                replyData["parentReplyId"] = parentReplyId
            }

            val docRef = firestore.collection(STORIES_COLLECTION).document(storyId)
                .collection(REPLIES_SUBCOLLECTION)
                .add(replyData)
                .await()
                Unit

            // Increment reply count on the story
            firestore.collection(STORIES_COLLECTION).document(storyId)
                .update("replyCount", FieldValue.increment(1))
                .await()
                Unit

            Reply(
                id = docRef.id,
                storyId = storyId,
                userId = uid,
                nickname = user.nickname,
                photoURL = user.photoURL,
                content = content,
                parentReplyId = parentReplyId,
                createdAt = com.google.firebase.Timestamp.now()
            )
        } ?: throw IllegalStateException("Create reply timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to add reply to story: %s", storyId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time observers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Observe a list of stories in real-time, ordered by creation date. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStories(limit: Int = DEFAULT_PAGE_SIZE): Flow<List<Story>> = callbackFlow {
        val registration = firestore.collection(STORIES_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeStories error")
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val stories = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { Story.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(stories)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Observe a single story in real-time (for story detail screen). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStory(storyId: String): Flow<Story> = callbackFlow {
        val registration = firestore.collection(STORIES_COLLECTION)
            .document(storyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeStory error for %s", storyId)
                    // Don't close the flow on transient errors — the listener stays
                    // active and will retry on the next Firestore sync.
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val story = Story.fromFirestoreMap(snapshot.id, snapshot.data ?: emptyMap())
                    trySend(story)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Observe replies for a story in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReplies(
        storyId: String,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Flow<List<Reply>> = callbackFlow {
        val registration = firestore.collection(STORIES_COLLECTION).document(storyId)
            .collection(REPLIES_SUBCOLLECTION)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeReplies error for story: %s", storyId)
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val replies = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { Reply.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(replies)
                }
            }
        awaitClose { registration.remove() }
    }
}

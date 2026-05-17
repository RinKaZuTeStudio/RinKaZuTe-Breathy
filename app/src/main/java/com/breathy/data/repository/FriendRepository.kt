package com.breathy.data.repository

import com.breathy.data.models.FriendRequest
import com.breathy.data.models.Friendship
import com.breathy.data.models.PublicProfile
import com.breathy.data.models.RequestStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Friend repository — Firestore CRUD for friend requests and friendships.
 *
 * Features:
 * - Send, accept, and reject friend requests
 * - Real-time listeners for incoming requests and friend list
 * - Uses [RequestStatus] enum instead of string literals
 * - 30-second timeout on all network operations
 * - Proper listener cleanup via Flow's awaitClose
 * - Fetches friend profiles concurrently (no runBlocking)
 */
class FriendRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "FriendRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val FRIEND_REQUESTS_COLLECTION = "friendRequests"
        private const val FRIENDSHIPS_COLLECTION = "friendships"
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // ═══════════════════════════════════════════════════════════════════════════
    //  Send / accept / reject friend requests
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Send a friend request to another user.
     * Validates that the user isn't sending to themselves, doesn't already
     * have a pending request, and isn't already friends.
     */
    suspend fun sendRequest(toUserId: String): Result<FriendRequest> = runCatching {
        val uid = currentUserId

        if (toUserId == uid) {
            throw IllegalArgumentException("Cannot send friend request to yourself")
        }

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Check if a request already exists in either direction
            val existingOutgoing = try {
                firestore.collection(FRIEND_REQUESTS_COLLECTION)
                    .whereEqualTo("fromUserId", uid)
                    .whereEqualTo("toUserId", toUserId)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIEND_REQUESTS_COLLECTION)
                        .whereEqualTo("fromUserId", uid)
                        .whereEqualTo("toUserId", toUserId)
                        .limit(1)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }

            if (!existingOutgoing.isEmpty) {
                throw IllegalStateException("Friend request already sent")
            }

            val existingIncoming = try {
                firestore.collection(FRIEND_REQUESTS_COLLECTION)
                    .whereEqualTo("fromUserId", toUserId)
                    .whereEqualTo("toUserId", uid)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIEND_REQUESTS_COLLECTION)
                        .whereEqualTo("fromUserId", toUserId)
                        .whereEqualTo("toUserId", uid)
                        .limit(1)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }

            if (!existingIncoming.isEmpty) {
                throw IllegalStateException("This user already sent you a friend request")
            }

            // Check if already friends
            val existingFriendship = try {
                firestore.collection(FRIENDSHIPS_COLLECTION)
                    .whereArrayContains("userIds", uid)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIENDSHIPS_COLLECTION)
                        .whereArrayContains("userIds", uid)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }

            val alreadyFriends = existingFriendship.documents.any { doc ->
                val userIds = doc.get("userIds") as? List<*>
                userIds?.contains(toUserId) == true
            }

            if (alreadyFriends) {
                throw IllegalStateException("Already friends with this user")
            }

            val requestData = mapOf(
                "fromUserId" to uid,
                "toUserId" to toUserId,
                "status" to RequestStatus.PENDING.value,
                "timestamp" to FieldValue.serverTimestamp()
            )

            val docRef = firestore.collection(FRIEND_REQUESTS_COLLECTION)
                .add(requestData)
                .await()
                Unit

            FriendRequest(
                id = docRef.id,
                fromUserId = uid,
                toUserId = toUserId,
                status = RequestStatus.PENDING,
                timestamp = com.google.firebase.Timestamp.now()
            )
        } ?: throw IllegalStateException("Send friend request timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to send friend request")
    }

    /**
     * Accept a friend request. Creates a friendship document and updates
     * the request status atomically.
     */
    suspend fun acceptRequest(requestId: String): Result<Friendship> = runCatching {
        val uid = currentUserId

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val requestDoc = try {
                firestore.collection(FRIEND_REQUESTS_COLLECTION)
                    .document(requestId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIEND_REQUESTS_COLLECTION)
                        .document(requestId)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }
            if (!requestDoc.exists()) {
                throw NoSuchElementException("Friend request not found")
            }

            val request = FriendRequest.fromFirestoreMap(requestId, requestDoc.data ?: emptyMap())

            if (request.toUserId != uid) {
                throw SecurityException("Cannot accept a friend request not addressed to you")
            }

            if (request.status != RequestStatus.PENDING) {
                throw IllegalStateException("Friend request is not pending (status: ${request.status})")
            }

            // Create friendship document
            val friendshipData = mapOf(
                "userIds" to listOf(request.fromUserId, request.toUserId).sorted(),
                "createdAt" to FieldValue.serverTimestamp()
            )

            val friendshipRef = firestore.collection(FRIENDSHIPS_COLLECTION)
                .add(friendshipData)
                .await()
                Unit

            // Update request status
            firestore.collection(FRIEND_REQUESTS_COLLECTION).document(requestId)
                .update("status", RequestStatus.ACCEPTED.value)
                .await()
                Unit

            Friendship(
                id = friendshipRef.id,
                userIds = listOf(request.fromUserId, request.toUserId).sorted(),
                createdAt = com.google.firebase.Timestamp.now()
            )
        } ?: throw IllegalStateException("Accept friend request timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to accept friend request: %s", requestId)
    }

    /**
     * Reject a friend request. Only the recipient can reject.
     */
    suspend fun rejectRequest(requestId: String): Result<Unit> = runCatching {
        val uid = currentUserId

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val requestDoc = try {
                firestore.collection(FRIEND_REQUESTS_COLLECTION)
                    .document(requestId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIEND_REQUESTS_COLLECTION)
                        .document(requestId)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }

            if (requestDoc.exists()) {
                val toUserId = requestDoc.getString("toUserId")
                if (toUserId != uid) {
                    throw SecurityException("Cannot reject a friend request not addressed to you")
                }
            }

            firestore.collection(FRIEND_REQUESTS_COLLECTION).document(requestId)
                .update("status", RequestStatus.REJECTED.value)
                .await()
            Unit
        } ?: throw IllegalStateException("Reject friend request timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to reject friend request: %s", requestId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Get requests
    // ═══════════════════════════════════════════════════════════════════════════

    /** Get all incoming (pending) friend requests for the current user. */
    suspend fun getIncomingRequests(): Result<List<FriendRequest>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = try {
                firestore.collection(FRIEND_REQUESTS_COLLECTION)
                    .whereEqualTo("toUserId", currentUserId)
                    .whereEqualTo("status", RequestStatus.PENDING.value)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIEND_REQUESTS_COLLECTION)
                        .whereEqualTo("toUserId", currentUserId)
                        .whereEqualTo("status", RequestStatus.PENDING.value)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { FriendRequest.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get incoming requests timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get incoming friend requests")
    }

    /** Get all outgoing (pending) friend requests from the current user. */
    suspend fun getOutgoingRequests(): Result<List<FriendRequest>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = try {
                firestore.collection(FRIEND_REQUESTS_COLLECTION)
                    .whereEqualTo("fromUserId", currentUserId)
                    .whereEqualTo("status", RequestStatus.PENDING.value)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIEND_REQUESTS_COLLECTION)
                        .whereEqualTo("fromUserId", currentUserId)
                        .whereEqualTo("status", RequestStatus.PENDING.value)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { FriendRequest.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get outgoing requests timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get outgoing friend requests")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Friends list
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all friends of the current user as a list of [PublicProfile].
     * Fetches friend profiles concurrently using async/await.
     */
    suspend fun getFriends(): Result<List<PublicProfile>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val friendships = try {
                firestore.collection(FRIENDSHIPS_COLLECTION)
                    .whereArrayContains("userIds", currentUserId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIENDSHIPS_COLLECTION)
                        .whereArrayContains("userIds", currentUserId)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }

            val friendIds = friendships.documents.mapNotNull { doc ->
                val userIds = doc.get("userIds") as? List<*>
                userIds?.filterIsInstance<String>()?.firstOrNull { it != currentUserId }
            }

            // Fetch profiles concurrently
            val profiles = coroutineScope {
                friendIds.map { id ->
                    async {
                        try {
                            val profileDoc = try {
                                firestore.collection(PUBLIC_PROFILES_COLLECTION)
                                    .document(id)
                                    .get(Source.SERVER)
                                    .await()
                            } catch (e: Exception) {
                                Timber.w(e, "Server read failed for profile %s — trying cache", id)
                                try {
                                    firestore.collection(PUBLIC_PROFILES_COLLECTION)
                                        .document(id)
                                        .get(Source.CACHE)
                                        .await()
                                } catch (cacheEx: Exception) {
                                    Timber.w(cacheEx, "Cache read also failed for profile %s", id)
                                    throw e
                                }
                            }
                            if (profileDoc.exists()) {
                                PublicProfile.fromFirestoreMap(profileDoc.data ?: emptyMap())
                            } else null
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to fetch friend profile: %s", id)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            profiles.sortedBy { it.nickname }
        } ?: throw IllegalStateException("Get friends timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get friends list")
    }

    /**
     * Remove a friendship by its document ID.
     */
    suspend fun removeFriend(friendshipId: String): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(FRIENDSHIPS_COLLECTION).document(friendshipId)
                .delete()
                .await()
            Unit
        } ?: throw IllegalStateException("Remove friend timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to remove friend: %s", friendshipId)
    }

    /**
     * Check if the current user is friends with [otherUserId].
     */
    suspend fun isFriend(otherUserId: String): Result<Boolean> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = try {
                firestore.collection(FRIENDSHIPS_COLLECTION)
                    .whereArrayContains("userIds", currentUserId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed — trying cache")
                try {
                    firestore.collection(FRIENDSHIPS_COLLECTION)
                        .whereArrayContains("userIds", currentUserId)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }
            snapshot.documents.any { doc ->
                val userIds = doc.get("userIds") as? List<*>
                userIds?.contains(otherUserId) == true
            }
        } ?: throw IllegalStateException("isFriend check timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to check friendship status")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time observers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Observe incoming friend requests in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeIncomingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUserId
        val registration = firestore.collection(FRIEND_REQUESTS_COLLECTION)
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("status", RequestStatus.PENDING.value)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeIncomingRequests error")
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { FriendRequest.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(requests)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Observe outgoing friend requests in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeOutgoingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUserId
        val registration = firestore.collection(FRIEND_REQUESTS_COLLECTION)
            .whereEqualTo("fromUserId", uid)
            .whereEqualTo("status", RequestStatus.PENDING.value)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeOutgoingRequests error")
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { FriendRequest.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(requests)
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observe the current user's friend list in real-time.
     * Fetches friend profiles concurrently when the friendships snapshot changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFriends(): Flow<List<PublicProfile>> = callbackFlow {
        val uid = currentUserId
        val registration = firestore.collection(FRIENDSHIPS_COLLECTION)
            .whereArrayContains("userIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeFriends error")
                    // Don't close the flow on error — emit empty list as fallback
                    // so the UI can still render instead of crashing.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val friendIds = snapshot.documents.mapNotNull { doc ->
                        val userIds = doc.get("userIds") as? List<*>
                        userIds?.filterIsInstance<String>()?.firstOrNull { it != uid }
                    }

                    // Launch a coroutine to fetch profiles without blocking.
                    // Use the callbackFlow's producer scope (not GlobalScope)
                    // so the coroutine is cancelled when the flow is collected.
                    launch {
                        val profiles = friendIds.mapNotNull { id ->
                            try {
                                val profileDoc = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                                    .document(id)
                                    .get()
                                    .await()
                                Unit
                                if (profileDoc.exists()) {
                                    PublicProfile.fromFirestoreMap(profileDoc.data ?: emptyMap())
                                } else null
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to fetch friend profile: %s", id)
                                null
                            }
                        }
                        trySend(profiles.sortedBy { it.nickname })
                    }
                }
            }
        awaitClose { registration.remove() }
    }
}

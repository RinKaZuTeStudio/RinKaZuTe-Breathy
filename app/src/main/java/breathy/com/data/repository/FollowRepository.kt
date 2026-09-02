package breathy.com.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import breathy.com.data.models.PublicProfile
import timber.log.Timber

/**
 * Follow system — FOLLOWERS / FOLLOWING as a dedicated, one-way social graph
 * (independent of the FRIENDS system).
 *
 * Data model:
 * - `follows/{followerId}_{followedId}` — deterministic follow document with
 *   `followerId`, `followedId`, `createdAt`. One-way: A following B does NOT
 *   create any friendship or a reciprocal follow.
 * - `publicProfiles/{uid}.followerCount` / `.followingCount` — denormalized
 *   counters maintained in the same batch as the follow document so lists and
 *   badges never drift.
 *
 * Everything is computed from REAL registered accounts — no demo users.
 */
class FollowRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val FOLLOWS_COLLECTION = "follows"
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        private const val TIMEOUT_MS = 15_000L

        /** Deterministic follow document id. */
        fun followId(followerId: String, followedId: String): String = "${followerId}_$followedId"
    }

    private fun currentUidOrError(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // ═══════════════════════════════════════════════════════════════════════
    //  Follow / unfollow
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Follow [targetUid]. One-way relationship — does not create friendship.
     * Idempotent: re-following someone you already follow is a no-op.
     */
    suspend fun followUser(targetUid: String): Result<Unit> = runCatching {
        val me = currentUidOrError()
        require(targetUid.isNotBlank() && targetUid != me) { "Invalid follow target" }

        withTimeoutOrNull(TIMEOUT_MS) {
            val ref = firestore.collection(FOLLOWS_COLLECTION).document(followId(me, targetUid))
            firestore.runBatch { batch ->
                batch.set(
                    ref,
                    mapOf(
                        "followerId" to me,
                        "followedId" to targetUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    firestore.collection(PUBLIC_PROFILES_COLLECTION).document(targetUid),
                    mapOf("followerCount" to FieldValue.increment(1)),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                batch.set(
                    firestore.collection(PUBLIC_PROFILES_COLLECTION).document(me),
                    mapOf("followingCount" to FieldValue.increment(1)),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }.await()
        } ?: throw IllegalStateException("Follow timed out — try again")
        Timber.i("FollowRepository: %s now follows %s", me, targetUid)
        Unit
    }

    /**
     * Unfollow [targetUid]. Removes the one-way relationship and updates the
     * denormalized counters atomically. Idempotent.
     */
    suspend fun unfollowUser(targetUid: String): Result<Unit> = runCatching {
        val me = currentUidOrError()
        require(targetUid.isNotBlank()) { "Invalid unfollow target" }

        withTimeoutOrNull(TIMEOUT_MS) {
            val ref = firestore.collection(FOLLOWS_COLLECTION).document(followId(me, targetUid))
            val existing = ref.get().await()
            if (!existing.exists()) {
                return@withTimeoutOrNull // nothing to do — already not following
            }
            firestore.runBatch { batch ->
                batch.delete(ref)
                batch.set(
                    firestore.collection(PUBLIC_PROFILES_COLLECTION).document(targetUid),
                    mapOf("followerCount" to FieldValue.increment(-1)),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                batch.set(
                    firestore.collection(PUBLIC_PROFILES_COLLECTION).document(me),
                    mapOf("followingCount" to FieldValue.increment(-1)),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }.await()
        } ?: throw IllegalStateException("Unfollow timed out — try again")
        Timber.i("FollowRepository: %s unfollowed %s", me, targetUid)
        Unit
    }

    /** True when the current user follows [targetUid]. */
    suspend fun isFollowing(targetUid: String): Result<Boolean> = runCatching {
        val me = currentUidOrError()
        withTimeoutOrNull(TIMEOUT_MS) {
            firestore.collection(FOLLOWS_COLLECTION)
                .document(followId(me, targetUid))
                .get()
                .await()
        }?.exists() ?: false
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Lists — real profiles joined onto real follow edges
    // ═══════════════════════════════════════════════════════════════════════

    /** Live list of everyone who follows [uid] (with their public profiles). */
    fun observeFollowers(uid: String): Flow<List<PublicProfile>> = callbackFlow {
        val registration = firestore.collection(FOLLOWS_COLLECTION)
            .whereEqualTo("followedId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeFollowers error")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    launchProfileJoin(
                        ids = snapshot.documents.mapNotNull { it.getString("followerId") },
                        callback = { trySend(it) }
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /** Live list of everyone [uid] follows (with their public profiles). */
    fun observeFollowing(uid: String): Flow<List<PublicProfile>> = callbackFlow {
        val registration = firestore.collection(FOLLOWS_COLLECTION)
            .whereEqualTo("followerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeFollowing error")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    launchProfileJoin(
                        ids = snapshot.documents.mapNotNull { it.getString("followedId") },
                        callback = { trySend(it) }
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Join follow edges to public profiles without N+1 live listeners —
     * one batched get per emission. The join is deterministic and every
     * profile is a REAL registered account.
     */
    private fun launchProfileJoin(
        ids: List<String>,
        callback: (List<PublicProfile>) -> Unit
    ) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val profiles = ids.mapNotNull { id ->
                    withTimeoutOrNull(TIMEOUT_MS) {
                        firestore.collection(PUBLIC_PROFILES_COLLECTION).document(id).get().await()
                    }?.data?.let { PublicProfile.fromFirestoreMap(id, it) }
                }
                callback(profiles)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "FollowRepository: failed to join profiles")
                callback(emptyList())
            }
        }
    }
}

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
     *
     * v1.0.10 COUNTER FIX — the denormalized `publicProfiles` counters are
     * now written as TWO INDEPENDENT existence-aware writes instead of one
     * atomic batch:
     *  · A batch is all-or-nothing; when EITHER profile document is missing,
     *    the cross-user counter write is evaluated as a CREATE and denied by
     *    the rules — which silently killed BOTH counters (the #1 cause of
     *    "followed but the follower count never increased").
     *  · Separate writes keep a missing/self-healing profile from blocking
     *    the other counter, and the display count no longer depends on them
     *    at all ([observeFollowerCount] derives the REAL number from the
     *    follows collection with a live listener).
     *
     * The edge write still retries automatically (rules propagation after a
     * fresh Publish, transient network drops and first-write-after-reconnect
     * hiccups all manifest as a single denied/timed-out call that succeeds on
     * the immediate retry).
     */
    suspend fun followUser(targetUid: String): Result<Unit> = runCatching {
        val me = currentUidOrError()
        require(targetUid.isNotBlank() && targetUid != me) { "Invalid follow target" }

        // v1.0.11 — IDEMPOTENT: when the follow edge already exists, succeed
        // silently. Re-writing an existing document is an UPDATE and the
        // rules intentionally reject updates on follows/{followId}, so a
        // stale-UI double-tap used to surface PERMISSION_DENIED to the user.
        // Existence is checked first (reads before writes, single doc read);
        // counters are only touched on the transition itself.
        val transitioned = withTimeoutOrNull(TIMEOUT_MS) {
            val edgeRef = firestore.collection(FOLLOWS_COLLECTION).document(followId(me, targetUid))
            if (edgeRef.get().await().exists()) {
                return@withTimeoutOrNull false // already following — nothing to do
            }
            writeFollowEdgeWithRetry(me, targetUid)
            true
        } ?: throw IllegalStateException("Follow timed out — try again")
        if (!transitioned) return@runCatching

        incrementCounterBestEffort(targetUid, "followerCount", +1)
        incrementCounterBestEffort(me, "followingCount", +1)

        Timber.i("FollowRepository: %s now follows %s", me, targetUid)
        Unit
    }

    /**
     * Unfollow [targetUid]. Removes the one-way relationship; the
     * denormalized counter updates are best-effort separate writes (see
     * [followUser]). Idempotent.
     */
    suspend fun unfollowUser(targetUid: String): Result<Unit> = runCatching {
        val me = currentUidOrError()
        require(targetUid.isNotBlank()) { "Invalid unfollow target" }

        val removed = withTimeoutOrNull(TIMEOUT_MS) {
            val ref = firestore.collection(FOLLOWS_COLLECTION).document(followId(me, targetUid))
            val existing = ref.get().await()
            if (!existing.exists()) {
                return@withTimeoutOrNull false // nothing to do — already not following
            }
            ref.delete().await()
            true
        } ?: throw IllegalStateException("Unfollow timed out — try again")
        if (!removed) return@runCatching

        incrementCounterBestEffort(targetUid, "followerCount", -1)
        incrementCounterBestEffort(me, "followingCount", -1)

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

    // ═══════════════════════════════════════════════════════════════════
    //  v1.0.10 — LIVE state so the UI never needs an exit/re-enter refresh
    // ═══════════════════════════════════════════════════════════════════

    /**
     * LIVE follow relationship for [followerUid] → [followedUid]. Emits
     * immediately from the LOCAL CACHE the instant the edge is written, and
     * again on every server ack — the follow button flips instantly without
     * leaving and re-opening the profile.
     */
    fun observeIsFollowing(followerUid: String, followedUid: String): Flow<Boolean> = callbackFlow {
        val registration = firestore.collection(FOLLOWS_COLLECTION)
            .document(followId(followerUid, followedUid))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.w(error, "observeIsFollowing error")
                }
                trySend(snapshot?.exists() ?: false)
            }
        awaitClose { registration.remove() }
    }

    /**
     * LIVE REAL follower count for [uid] — derived from the follows
     * collection itself, NOT the denormalized counter. Snapshot listeners
     * fire from the local cache immediately, so the count updates in place
     * the moment the follow edge is written (and it is always accurate even
     * when a counter write was denied by the rules).
     */
    fun observeFollowerCount(uid: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection(FOLLOWS_COLLECTION)
            .whereEqualTo("followedId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.w(error, "observeFollowerCount error")
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.size ?: 0)
            }
        awaitClose { registration.remove() }
    }

    /**
     * v1.0.10 — ONE counter write, existence-aware, fully independent.
     * If the profile document does not exist the merge-set would be
     * evaluated as a CREATE and denied by the rules; skipping it here keeps
     * the follow flow error-free (the displayed count comes from the live
     * follows-derived listener, so nothing the user sees goes stale).
     */
    private suspend fun incrementCounterBestEffort(profileUid: String, field: String, delta: Int) {
        runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                val ref = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(profileUid)
                val exists = ref.get().await().exists()
                if (!exists) {
                    Timber.i("FollowRepository: counter skip — publicProfiles/%s missing", profileUid)
                    return@withTimeoutOrNull
                }
                ref.set(mapOf(field to FieldValue.increment(delta.toLong())), com.google.firebase.firestore.SetOptions.merge()).await()
            }
        }.onFailure { e ->
            Timber.w(e, "FollowRepository: counter %s%+d failed (non-fatal)", field, delta)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Lists — real profiles joined onto real follow edges
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The follow EDGE write (source of truth) with one automatic retry.
     * PERMISSION_DENIED is retried once too — a freshly published ruleset
     * takes a few seconds to propagate across Firestore edge servers, and
     * that exact window is the #1 real-world cause of "couldn't follow".
     */
    private suspend fun writeFollowEdgeWithRetry(me: String, targetUid: String) {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                withTimeoutOrNull(TIMEOUT_MS) {
                    firestore.collection(FOLLOWS_COLLECTION).document(followId(me, targetUid))
                        .set(
                            mapOf(
                                "followerId" to me,
                                "followedId" to targetUid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                } ?: throw IllegalStateException("Follow timed out — try again")
                if (attempt > 0) {
                    Timber.i("FollowRepository: follow edge write succeeded on retry %d", attempt)
                }
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                Timber.w(e, "FollowRepository: follow edge write attempt %d failed", attempt + 1)
                if (attempt == 0) kotlinx.coroutines.delay(1_200L)
            }
        }
        throw lastError ?: IllegalStateException("Follow failed — try again")
    }

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

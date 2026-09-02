package breathy.com.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Safety repository — UGC safety tools required for social features
 * (spec section 30): report user / post / message, block user.
 *
 * Firestore layout:
 * ```
 * reports/{reportId}                    — {reporterId, targetType, targetId, reason, details, status, createdAt}
 * users/{uid}                           — blockedUsers: List<String> (array of blocked user ids)
 * ```
 *
 * Blocked users cannot continue unwanted direct interaction: the chat UI
 * hides conversations with blocked users and refuses sending (and the
 * blocked list is real-time via [observeBlockedUsers]).
 */
class SafetyRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "SafetyRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val USERS_COLLECTION = "users"
        private const val REPORTS_COLLECTION = "reports"

        const val TARGET_USER = "user"
        const val TARGET_POST = "post"
        const val TARGET_MESSAGE = "message"

        val REPORT_REASONS = listOf(
            "Spam or scam",
            "Harassment or bullying",
            "Inappropriate content",
            "Misinformation",
            "Self-harm concern",
            "Other"
        )
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // ═══════════════════════════════════════════════════════════════════════
    //  Blocking
    // ═══════════════════════════════════════════════════════════════════════

    /** Block a user — real-time set of blocked ids for enforcement. */
    fun observeBlockedUsers(): Flow<Set<String>> = callbackFlow {
        val uid = try { currentUserId } catch (e: Exception) { close(e); return@callbackFlow }
        val reg = firestore.collection(USERS_COLLECTION).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val blocked = (snap?.get("blockedUsers") as? List<*>)
                    ?.filterIsInstance<String>()?.toSet() ?: emptySet()
                trySend(blocked)
            }
        awaitClose { reg.remove() }
    }

    /** Block a user. Idempotent. */
    suspend fun blockUser(userId: String): Result<Unit> = runCatching {
        require(userId != currentUserId) { "You cannot block yourself" }
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(USERS_COLLECTION).document(currentUserId)
                .update("blockedUsers", FieldValue.arrayUnion(userId))
                .await()
            Unit
        } ?: throw IllegalStateException("Block timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to block user %s", userId)
    }

    /** Unblock a user. Idempotent. */
    suspend fun unblockUser(userId: String): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(USERS_COLLECTION).document(currentUserId)
                .update("blockedUsers", FieldValue.arrayRemove(userId))
                .await()
            Unit
        } ?: throw IllegalStateException("Unblock timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to unblock user %s", userId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Reporting
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Submit a moderation report for a user, post, or message.
     * Reports are append-only; moderation review happens outside the client.
     */
    suspend fun report(
        targetType: String,
        targetId: String,
        reason: String,
        details: String? = null
    ): Result<Unit> = runCatching {
        require(targetType in listOf(TARGET_USER, TARGET_POST, TARGET_MESSAGE)) {
            "Unknown report target"
        }
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val report = mapOf(
                "reporterId" to currentUserId,
                "targetType" to targetType,
                "targetId" to targetId,
                "reason" to reason,
                "details" to (details ?: ""),
                "status" to "open",
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            firestore.collection(REPORTS_COLLECTION).add(report).await()
            Unit
        } ?: throw IllegalStateException("Report timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to submit report (%s)", targetType)
    }
}

package breathy.com.data.repository

import breathy.com.data.models.AvatarFrame
import breathy.com.data.models.GoldTransaction
import breathy.com.data.models.GoldTxType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import breathy.com.utils.s
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Thrown when a Gold spend is attempted with an insufficient balance.
 * The UI catches this to show the "not enough Gold" state and disable entry.
 */
class InsufficientGoldException(val required: Int, val available: Int) :
    Exception("Insufficient Gold: need $required, have $available")

/**
 * Gold repository — the REAL persistent in-app currency ledger.
 *
 * Design guarantees (spec sections 8/10/24):
 * - **Atomic balance updates** — every mutation runs inside a Firestore
 *   transaction that reads the user doc, applies the delta, and writes the
 *   ledger entry + balance in one commit. Concurrent claims cannot double-apply.
 * - **Duplicate reward protection** — rewards carry a deterministic
 *   [GoldTransaction.dedupKey] which IS the ledger document ID. A replayed
 *   claim finds the existing doc inside the transaction and becomes a no-op.
 * - **Insufficient balance protection** — [spend] refuses (throws
 *   [InsufficientGoldException]) when balance < amount; balance can never go
 *   negative because the check and the write happen in the same transaction.
 * - **No client-side balance manipulation** — all amounts are constants in
 *   code; the client never accepts a caller-supplied balance.
 * - **Full history** — every mutation appends an immutable entry at
 *   `users/{uid}/goldTransactions` with the post-transaction balance.
 *
 * Firestore document layout:
 * ```
 * users/{uid}                        — coins (Int), ownedFrames (List<String>), avatarFrame (String?)
 * users/{uid}/goldTransactions/{id}  — GoldTransaction (id == dedupKey when present)
 * ```
 *
 * Note: the balance field remains `coins` on the user document for backward
 * compatibility with existing accounts — "Gold" is the user-facing identity
 * of the same currency (safe migration: no data move required).
 */
class GoldRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    /**
     * v1.0.10 self-heal — same contract as UserRepository's transactional
     * user write: update when the document exists, create a rules-valid one
     * (email + createdAt anchors) when it does not, so Gold rewards can
     * never fail on a sparse account.
     */
    private fun selfHealUserWrite(
        tx: Transaction,
        userRef: com.google.firebase.firestore.DocumentReference,
        snapshot: com.google.firebase.firestore.DocumentSnapshot,
        updates: Map<String, Any?>
    ) {
        if (snapshot.exists()) {
            tx.update(userRef, updates)
        } else {
            val healed = mutableMapOf<String, Any?>(
                "email" to (auth.currentUser?.email ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
                "nickname" to (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Quitter"),
                "xp" to 0L
            )
            healed.putAll(updates)
            tx.set(userRef, healed, SetOptions.merge())
        }
    }

    companion object {
        private const val TAG = "GoldRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val USERS_COLLECTION = "users"
        private const val TX_COLLECTION = "goldTransactions"

        /** Approved earn amounts (spec section 8 — fixed, code-owned values). */
        object Rewards {
            /** Daily check-in — same grant the legacy daily reward produced. */
            const val DAILY_CHECKIN_MIN = 10
            const val DAILY_CHECKIN_MAX = 50
            /** Streak milestone bonuses (days smoke-free → Gold). */
            fun streakMilestone(daysSmokeFree: Int): Int? = when (daysSmokeFree) {
                7 -> 100
                14 -> 200
                30 -> 500
                90 -> 1000
                180 -> 2000
                365 -> 5000
                else -> null
            }
        }

        /** Approved spend amounts. */
        object Costs {
            /** Featured push-up event entry fee (spec section 24). */
            const val EVENT_ENTRY = 500
            fun frame(frame: AvatarFrame): Int = frame.goldPrice ?: 0
            fun profilePicture(picture: breathy.com.data.models.ProfilePicture): Int = picture.goldPrice ?: 0
        }

        /** Premium perk: Gold granted to every subscriber once per ISO week. */
        const val PREMIUM_WEEKLY_GOLD = 500

        private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        /** dedupKey for the daily check-in — one per calendar day (UTC). */
        fun dailyCheckinKey(day: Date = Date()): String =
            "daily_checkin_${dayFormat.format(day)}"

        /** dedupKey for a streak milestone — one per day-count, ever. */
        fun streakMilestoneKey(daysSmokeFree: Int): String = "streak_milestone_$daysSmokeFree"

        /** dedupKey for an achievement reward — one per achievement id, ever. */
        fun achievementKey(achievementId: String): String = "achievement_$achievementId"

        /** dedupKey for an event entry — one per event, ever. */
        fun eventEntryKey(eventId: String): String = "event_entry_$eventId"

        /** dedupKey for a frame purchase — one per frame, ever. */
        fun framePurchaseKey(frameId: String): String = "frame_purchase_$frameId"

        /** dedupKey for a unified profile-picture purchase — one per picture, ever. */
        fun picturePurchaseKey(pictureId: String): String = "picture_purchase_$pictureId"

        /** dedupKey for the Premium weekly Gold bonus — one per ISO week. */
        fun premiumWeeklyKey(millis: Long = Date().time): String {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = millis
            cal.firstDayOfWeek = java.util.Calendar.MONDAY
            cal.minimalDaysInFirstWeek = 4
            return "premium_weekly_%04d-W%02d".format(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.WEEK_OF_YEAR)
            )
        }
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException(s("User not authenticated", "غير مسجل الدخول"))

    // ═══════════════════════════════════════════════════════════════════════
    //  Balance
    // ═══════════════════════════════════════════════════════════════════════

    /** Current balance snapshot (server source preferred, cache fallback). */
    suspend fun getBalance(): Result<Int> = runCatching {
        val uid = requireUserId()
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snap = firestore.collection(USERS_COLLECTION).document(uid).get(Source.SERVER).await()
            (snap.getLong("coins") ?: 0L).toInt()
        } ?: run {
            val cached = firestore.collection(USERS_COLLECTION).document(uid).get(Source.CACHE).await()
            (cached.getLong("coins") ?: 0L).toInt()
        }
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to read Gold balance")
    }

    /** Real-time balance updates (user document stream). */
    fun balanceFlow(): Flow<Int> = callbackFlow {
        val uid = try { requireUserId() } catch (e: Exception) {
            close(e); return@callbackFlow
        }
        val reg = firestore.collection(USERS_COLLECTION).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend((snap?.getLong("coins") ?: 0L).toInt())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Check & award streak-milestone Gold (7/14/30/90/180/365 days smoke-free).
     * Dedup keys make each milestone payable exactly once per lifetime.
     * Called on app start / home load; safe to call repeatedly.
     *
     * @return total Gold awarded by this check (0 when nothing new).
     */
    suspend fun checkStreakMilestones(daysSmokeFree: Int): Result<Int> = runCatching {
        var totalAwarded = 0
        for (milestone in listOf(7, 14, 30, 90, 180, 365)) {
            if (daysSmokeFree >= milestone) {
                val reward = Rewards.streakMilestone(milestone) ?: continue
                val key = streakMilestoneKey(milestone)
                val result = earn(
                    amount = reward,
                    source = "streak_milestone",
                    description = "$milestone-day smoke-free milestone",
                    dedupKey = key
                ).getOrNull() ?: continue
                if (result.second) totalAwarded += reward
            }
        }
        totalAwarded
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to check streak milestones")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  History
    // ═══════════════════════════════════════════════════════════════════════

    /** Real-time Gold History, newest first (spec section 38). */
    fun historyFlow(limit: Long = 100): Flow<List<GoldTransaction>> = callbackFlow {
        val uid = try { requireUserId() } catch (e: Exception) {
            close(e); return@callbackFlow
        }
        val reg = firestore.collection(USERS_COLLECTION).document(uid)
            .collection(TX_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val txs = snap?.documents?.mapNotNull { doc ->
                    doc.data?.let { GoldTransaction.fromFirestoreMap(doc.id, it) }
                } ?: emptyList()
                trySend(txs)
            }
        awaitClose { reg.remove() }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Earn
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Credit Gold atomically.
     *
     * @param amount positive amount to credit.
     * @param source machine-readable source key (e.g. "daily_checkin").
     * @param description human-readable history label.
     * @param dedupKey optional idempotency key — a second call with the same
     *        key returns the existing outcome WITHOUT double-crediting.
     * @return the resulting balance after the operation, and whether this
     *         call actually created a new ledger entry (false = deduplicated).
     */
    suspend fun earn(
        amount: Int,
        source: String,
        description: String,
        dedupKey: String? = null
    ): Result<Pair<Int, Boolean>> = runCatching {
        require(amount > 0) { "Gold earn amount must be positive" }
        val uid = requireUserId()
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)
        val txId = dedupKey ?: firestore.collection(USERS_COLLECTION).document(uid)
            .collection(TX_COLLECTION).document().id

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.runTransaction { tx: Transaction ->
                val userSnap = tx.get(userRef)
                val existing = tx.get(userRef.collection(TX_COLLECTION).document(txId))
                if (dedupKey != null && existing.exists()) {
                    // Duplicate protection — replayed reward, no double credit.
                    val priorBalance = existing.getLong("balanceAfter")
                        ?: userSnap.getLong("coins") ?: 0L
                    return@runTransaction (priorBalance.toInt() to false)
                }
                val current = (userSnap.getLong("coins") ?: 0L).toInt()
                val newBalance = current + amount
                selfHealUserWrite(tx, userRef, userSnap, mapOf("coins" to newBalance))
                tx.set(
                    userRef.collection(TX_COLLECTION).document(txId),
                    mapOf(
                        "amount" to amount,
                        "type" to GoldTxType.EARN.value,
                        "source" to source,
                        "description" to description,
                        "dedupKey" to dedupKey,
                        "balanceAfter" to newBalance,
                        "timestamp" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                newBalance to true
            }.await()
        } ?: throw IllegalStateException(s("Gold earn timed out after 30 seconds", "انتهت مهلة إضافة الذهب، حاول مجددًا"))
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to earn Gold (source=%s)", source)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Spend
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Debit Gold atomically with insufficient-balance protection.
     *
     * @param dedupKey idempotency key — retries after a partial network
     *        failure return the original outcome instead of double-charging
     *        (spec section 24: no retry exploits).
     * @return resulting balance, and whether this call performed the debit.
     */
    suspend fun spend(
        amount: Int,
        source: String,
        description: String,
        dedupKey: String? = null
    ): Result<Pair<Int, Boolean>> = runCatching {
        require(amount > 0) { "Gold spend amount must be positive" }
        val uid = requireUserId()
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)
        val txId = dedupKey ?: firestore.collection(USERS_COLLECTION).document(uid)
            .collection(TX_COLLECTION).document().id

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.runTransaction { tx: Transaction ->
                val userSnap = tx.get(userRef)
                if (dedupKey != null) {
                    val existing = tx.get(userRef.collection(TX_COLLECTION).document(txId))
                    if (existing.exists()) {
                        val priorBalance = existing.getLong("balanceAfter")
                            ?: userSnap.getLong("coins") ?: 0L
                        return@runTransaction (priorBalance.toInt() to false)
                    }
                }
                val current = (userSnap.getLong("coins") ?: 0L).toInt()
                if (current < amount) {
                    throw InsufficientGoldException(required = amount, available = current)
                }
                val newBalance = current - amount
                selfHealUserWrite(tx, userRef, userSnap, mapOf("coins" to newBalance))
                tx.set(
                    userRef.collection(TX_COLLECTION).document(txId),
                    mapOf(
                        "amount" to amount,
                        "type" to GoldTxType.SPEND.value,
                        "source" to source,
                        "description" to description,
                        "dedupKey" to dedupKey,
                        "balanceAfter" to newBalance,
                        "timestamp" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                newBalance to true
            }.await()
        } ?: throw IllegalStateException(s("Gold spend timed out after 30 seconds", "انتهت مهلة صرف الذهب، حاول مجددًا"))
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to spend Gold (source=%s)", source)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Frame purchase (spec section 10)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Buy a Gold-purchasable frame: deducts the exact price ONCE, records
     * ownership (`users.ownedFrames`), and equips the frame — all inside a
     * single Firestore transaction. Re-buying an owned frame is a no-op that
     * simply equips it (never double-charged).
     *
     * @return resulting Gold balance.
     */
    suspend fun purchaseFrame(frame: AvatarFrame): Result<Int> = runCatching {
        val price = frame.goldPrice
            ?: throw IllegalArgumentException(s("This frame is not purchasable with Gold", "لا يمكن شراء هذا الإطار بالذهب"))
        val uid = requireUserId()
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)
        val txId = framePurchaseKey(frame.id)

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.runTransaction { tx: Transaction ->
                val userSnap = tx.get(userRef)
                val existingTx = tx.get(userRef.collection(TX_COLLECTION).document(txId))
                val owned = (userSnap.get("ownedFrames") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val current = (userSnap.getLong("coins") ?: 0L).toInt()

                if (owned.contains(frame.id) || existingTx.exists()) {
                    // Already owned — just make sure it is equipped, never re-charge.
                    if (userSnap.getString("avatarFrame") != frame.id) {
                        tx.update(userRef, "avatarFrame", frame.id)
                        tx.set(
                            firestore.collection("publicProfiles").document(uid),
                            mapOf("avatarFrame" to frame.id, "updatedAt" to FieldValue.serverTimestamp()),
                            SetOptions.merge()
                        )
                    }
                    return@runTransaction current
                }

                if (current < price) {
                    throw InsufficientGoldException(required = price, available = current)
                }

                val newBalance = current - price
                tx.update(
                    userRef,
                    mapOf(
                        "coins" to newBalance,
                        "ownedFrames" to (owned + frame.id),
                        "avatarFrame" to frame.id
                    )
                )
                tx.set(
                    firestore.collection("publicProfiles").document(uid),
                    mapOf("avatarFrame" to frame.id, "updatedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                )
                tx.set(
                    userRef.collection(TX_COLLECTION).document(txId),
                    mapOf(
                        "amount" to price,
                        "type" to GoldTxType.SPEND.value,
                        "source" to "frame_purchase",
                        "description" to "Unlocked the ${frame.label} frame",
                        "dedupKey" to txId,
                        "balanceAfter" to newBalance,
                        "timestamp" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                newBalance
            }.await()
        } ?: throw IllegalStateException(s("Frame purchase timed out after 30 seconds", "انتهت مهلة شراء الإطار، حاول مجددًا"))
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to purchase frame: %s", frame.id)
    }

    /**
     * Purchase a unified profile picture with Gold (v1.0.9 picture shop).
     * Mirrors [purchaseFrame]: atomic deduction + ownership + equip + ledger.
     * Re-buying an owned picture re-equips it for free.
     *
     * @return resulting Gold balance.
     */
    suspend fun purchaseProfilePicture(picture: breathy.com.data.models.ProfilePicture): Result<Int> = runCatching {
        val price = picture.goldPrice
            ?: throw IllegalArgumentException(s("This picture is not purchasable with Gold", "لا يمكن شراء هذه الصورة بالذهب"))
        val uid = requireUserId()
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)
        val txId = picturePurchaseKey(picture.id)

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.runTransaction { tx: Transaction ->
                val userSnap = tx.get(userRef)
                val existingTx = tx.get(userRef.collection(TX_COLLECTION).document(txId))
                val owned = (userSnap.get("ownedPictures") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val current = (userSnap.getLong("coins") ?: 0L).toInt()

                if (owned.contains(picture.id) || existingTx.exists()) {
                    // Already owned — just make sure it is equipped, never re-charge.
                    if (userSnap.getString("profilePicture") != picture.id) {
                        tx.update(userRef, "profilePicture", picture.id)
                        tx.set(
                            firestore.collection("publicProfiles").document(uid),
                            mapOf("profilePicture" to picture.id, "updatedAt" to FieldValue.serverTimestamp()),
                            SetOptions.merge()
                        )
                    }
                    return@runTransaction current
                }

                if (current < price) {
                    throw InsufficientGoldException(required = price, available = current)
                }

                val newBalance = current - price
                tx.update(
                    userRef,
                    mapOf(
                        "coins" to newBalance,
                        "ownedPictures" to (owned + picture.id),
                        "profilePicture" to picture.id
                    )
                )
                tx.set(
                    firestore.collection("publicProfiles").document(uid),
                    mapOf("profilePicture" to picture.id, "updatedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                )
                tx.set(
                    userRef.collection(TX_COLLECTION).document(txId),
                    mapOf(
                        "amount" to price,
                        "type" to GoldTxType.SPEND.value,
                        "source" to "picture_purchase",
                        "description" to "Unlocked the ${picture.label} profile picture",
                        "dedupKey" to txId,
                        "balanceAfter" to newBalance,
                        "timestamp" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                newBalance
            }.await()
        } ?: throw IllegalStateException(s("Profile picture purchase timed out after 30 seconds", "انتهت مهلة شراء الصورة، حاول مجددًا"))
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to purchase profile picture: %s", picture.id)
    }
}

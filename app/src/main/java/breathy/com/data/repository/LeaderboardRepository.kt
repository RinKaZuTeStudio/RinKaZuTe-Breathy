package breathy.com.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Leaderboard repository — v1.0.11 WEEKLY / MONTHLY RESET + GOLD REWARDS.
 *
 * The Weekly and Monthly boards rank the auto-resetting `weeklyXp` /
 * `monthlyXp` mirrors on `publicProfiles`. Because this app has NO backend,
 * the period rollover and the prize payout run LAZILY on-device, rules-safe:
 *
 *  1. FINALIZE — the first client that opens the leaderboard (or Home) after
 *     a week/month rolled over snapshots the finished period's top players
 *     into a shared archive document `leaderboardArchive/{weekly|monthly}`
 *     (see firestore.rules v8 — any signed-in user may maintain it). The
 *     write happens inside a transaction keyed on `periodKey`, so concurrent
 *     clients cannot double-finalize: the loser re-reads inside the
 *     transaction, sees the doc already moved to the new period, and skips.
 *  2. SELF-CLAIM — every player's own app checks the archived ranking for
 *     THEIR uid and pays THEMSELVES the prize with a dedup-keyed ledger
 *     entry (`weekly_lb_<periodKey>`). Gold can only be credited to the
 *     signed-in user's own document (owner rule), so rewards are always
 *     self-distributed — no cross-user writes, ever.
 *
 * Reward table (product spec, 2026-09):
 *  - WEEKLY  : ranks 1–3 → 1000 Gold · ranks 4–10 → 500 · ranks 11–50 → 100
 *  - MONTHLY : ranks 1–3 → 5000 Gold · ranks 4–10 → 3000 · ranks 11–50 → 500
 */
class LeaderboardRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "LeaderboardRepository"
        private const val ARCHIVE_COLLECTION = "leaderboardArchive"
        private const val USERS_COLLECTION = "users"
        private const val PROFILES_COLLECTION = "publicProfiles"
        private const val TX_COLLECTION = "goldTransactions"
        private const val TIMEOUT_MS = 30_000L

        /** How deep the archived ranking goes (also the last paid place). */
        const val RANKED_SLOTS = 50

        /** Weekly prize table. Null = no prize for that rank. */
        fun weeklyReward(rank: Int): Int? = when {
            rank in 1..3 -> 1000
            rank in 4..10 -> 500
            rank in 11..RANKED_SLOTS -> 100
            else -> null
        }

        /** Monthly prize table. Null = no prize for that rank. */
        fun monthlyReward(rank: Int): Int? = when {
            rank in 1..3 -> 5000
            rank in 4..10 -> 3000
            rank in 11..RANKED_SLOTS -> 500
            else -> null
        }

        private const val DOC_WEEKLY = "weekly"
        private const val DOC_MONTHLY = "monthly"

        private fun archiveDocId(period: breathy.com.data.models.LeaderboardPeriod): String? =
            when (period) {
                breathy.com.data.models.LeaderboardPeriod.WEEKLY -> DOC_WEEKLY
                breathy.com.data.models.LeaderboardPeriod.MONTHLY -> DOC_MONTHLY
                breathy.com.data.models.LeaderboardPeriod.ALL_TIME -> null
            }
    }

    /** One archived ranking entry (kept deliberately small). */
    data class ArchivedEntry(
        val uid: String,
        val nickname: String,
        val xp: Int,
        val rank: Int
    )

    /** Outcome of a finalize+claim pass for the calling user. */
    data class FinalizeResult(
        /** The period that was just closed and paid out (null = nothing new). */
        val finalizedPeriodKey: String?,
        /** The caller's rank in that closed period (null = not ranked). */
        val myRank: Int?,
        /** Gold the caller earned for that closed period (0 = nothing). */
        val goldAwarded: Int,
        /** True when the gold was credited by THIS call (false = already paid). */
        val credited: Boolean
    )

    private fun currentUid(): String? = auth.currentUser?.uid

    /**
     * Roll the period over if needed and pay the caller their prize.
     * Safe to call on every leaderboard/home load — every step is idempotent
     * and every failure is swallowed (leaderboard must never block the UI).
     */
    suspend fun finalizeAndClaim(
        period: breathy.com.data.models.LeaderboardPeriod
    ): FinalizeResult? {
        val archiveId = archiveDocId(period) ?: return null
        val uid = currentUid() ?: return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val currentKey = when (period) {
                    breathy.com.data.models.LeaderboardPeriod.WEEKLY ->
                        UserRepository.weeklyXpPeriodKey()
                    else -> UserRepository.monthlyXpPeriodKey()
                }
                val archiveRef = firestore.collection(ARCHIVE_COLLECTION).document(archiveId)

                // ── Step 1: read the archive state ──────────────────────────
                val archiveSnap = archiveRef.get().await()
                val trackedKey = archiveSnap.getString("periodKey")

                if (archiveSnap.exists() && trackedKey == currentKey) {
                    // Same period already tracked — just make sure the caller
                    // got paid for the last finalized one.
                    return@withTimeoutOrNull claimFromArchive(
                        archiveRef, uid, period,
                        finalizedKey = archiveSnap.getString("finalizedPeriodKey"),
                        rankings = archiveSnap.get("rankings") as? List<*> ?: emptyList<Any>()
                    )
                }

                // ── Step 2: close out the previous period ───────────────────
                val closingKey = trackedKey ?: previousPeriodKey(period, currentKey)
                val rankings = if (trackedKey != null) {
                    snapshotRankings(period, closingKey)
                } else emptyList()

                if (trackedKey != null) {
                    val writeSucceeded = finalizeInTransaction(
                        archiveRef, currentKey, closingKey, rankings
                    )
                    if (!writeSucceeded) {
                        // Someone else finalized first — re-read their archive.
                        val fresh = archiveRef.get().await()
                        return@withTimeoutOrNull claimFromArchive(
                            archiveRef, uid, period,
                            finalizedKey = fresh.getString("finalizedPeriodKey"),
                            rankings = fresh.get("rankings") as? List<*> ?: emptyList<Any>()
                        )
                    }
                } else {
                    // First run ever — start tracking the current period.
                    archiveRef.set(
                        mapOf(
                            "periodKey" to currentKey,
                            "finalizedPeriodKey" to null,
                            "rankings" to emptyList<Any>(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                    return@withTimeoutOrNull FinalizeResult(null, null, 0, false)
                }

                // ── Step 3: pay the caller out of the fresh archive ─────────
                val fresh = archiveRef.get().await()
                claimFromArchive(
                    archiveRef, uid, period,
                    finalizedKey = fresh.getString("finalizedPeriodKey"),
                    rankings = fresh.get("rankings") as? List<*> ?: emptyList<Any>()
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "%s: finalizeAndClaim(%s) failed — non-fatal", TAG, period)
            null
        }
    }

    /** The period key immediately before [currentKey] for the given period. */
    private fun previousPeriodKey(
        period: breathy.com.data.models.LeaderboardPeriod,
        currentKey: String
    ): String = when (period) {
        breathy.com.data.models.LeaderboardPeriod.WEEKLY -> {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
            UserRepository.weeklyXpPeriodKey(cal.timeInMillis)
        }
        else -> {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.MONTH, -1)
            UserRepository.monthlyXpPeriodKey(cal.timeInMillis)
        }
    }

    /**
     * Snapshot the finished period's ranking: highest period-XP profiles
     * whose period stamp STILL equals the closing key (profiles already
     * updated in the new period are excluded by that client-side filter,
     * which is exactly the intended semantics — they moved on).
     * Single-field orderBy only → no composite index required.
     */
    private suspend fun snapshotRankings(
        period: breathy.com.data.models.LeaderboardPeriod,
        closingKey: String
    ): List<ArchivedEntry> {
        val (xpField, periodField) = when (period) {
            breathy.com.data.models.LeaderboardPeriod.WEEKLY -> "weeklyXp" to "weeklyXpPeriod"
            else -> "monthlyXp" to "monthlyXpPeriod"
        }
        val snapshot = firestore.collection(PROFILES_COLLECTION)
            .orderBy(xpField, Query.Direction.DESCENDING)
            .limit(200L)
            .get()
            .await()
        val entries = mutableListOf<ArchivedEntry>()
        for (doc in snapshot.documents) {
            if (doc.getString(periodField) != closingKey) continue
            val xp = (doc.getLong(xpField) ?: 0L).toInt()
            if (xp <= 0) continue
            entries.add(
                ArchivedEntry(
                    uid = doc.id,
                    nickname = doc.getString("nickname") ?: "Quitter",
                    xp = xp,
                    rank = entries.size + 1
                )
            )
            if (entries.size >= RANKED_SLOTS) break
        }
        return entries
    }

    /**
     * Atomically swap the archive doc to the new period inside a transaction.
     * Returns false when another client finalized first (caller re-reads).
     * ALL reads happen before ALL writes (Firestore transaction rule).
     */
    private suspend fun finalizeInTransaction(
        archiveRef: com.google.firebase.firestore.DocumentReference,
        currentKey: String,
        closingKey: String,
        rankings: List<ArchivedEntry>
    ): Boolean = try {
        withTimeoutOrNull(TIMEOUT_MS) {
            firestore.runTransaction { txn ->
                val snap = txn.get(archiveRef)          // READ — first and only
                val tracked = snap.getString("periodKey")
                if (snap.exists() && tracked != closingKey) {
                    // Another client already rolled the archive over.
                    return@runTransaction false
                }
                txn.set(                                 // WRITE — after the read
                    archiveRef,
                    mapOf(
                        "periodKey" to currentKey,
                        "finalizedPeriodKey" to closingKey,
                        "rankings" to rankings.map { e ->
                            mapOf(
                                "uid" to e.uid,
                                "nickname" to e.nickname,
                                "xp" to e.xp,
                                "rank" to e.rank
                            )
                        },
                        "finalizedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                true
            }.await() ?: false
        } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "%s: finalize transaction failed — non-fatal", TAG)
        false
    }

    /**
     * Pay the caller their archived rank prize, exactly once per period,
     * via a dedup-keyed Gold ledger entry. ALL reads before ALL writes.
     */
    private suspend fun claimFromArchive(
        archiveRef: com.google.firebase.firestore.DocumentReference,
        uid: String,
        period: breathy.com.data.models.LeaderboardPeriod,
        finalizedKey: String?,
        rankings: List<*>
    ): FinalizeResult {
        if (finalizedKey.isNullOrBlank() || rankings.isEmpty()) {
            return FinalizeResult(finalizedKey, null, 0, false)
        }

        @Suppress("UNCHECKED_CAST")
        val typed = rankings.filterIsInstance<Map<String, Any?>>()
        val mine = typed.firstOrNull { (it["uid"] as? String) == uid }
        val myRank = (mine?.get("rank") as? Long)?.toInt()
            ?: (mine?.get("rank") as? Int)
        val reward = when (period) {
            breathy.com.data.models.LeaderboardPeriod.WEEKLY ->
                myRank?.let { weeklyReward(it) }
            else -> myRank?.let { monthlyReward(it) }
        } ?: return FinalizeResult(finalizedKey, myRank, 0, false)

        // ── Self-claim transaction ────────────────────────────────────────
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)
        val ledgerId = when (period) {
            breathy.com.data.models.LeaderboardPeriod.WEEKLY -> "weekly_lb_$finalizedKey"
            else -> "monthly_lb_$finalizedKey"
        }
        val ledgerRef = userRef.collection(TX_COLLECTION).document(ledgerId)

        val credited = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                firestore.runTransaction { txn ->
                    // READS first
                    val userSnap = txn.get(userRef)
                    val ledgerSnap = txn.get(ledgerRef)
                    if (ledgerSnap.exists()) {
                        return@runTransaction false // already paid
                    }
                    // WRITES
                    val current = (userSnap.getLong("coins") ?: 0L).toInt()
                    val newBalance = current + reward
                    if (userSnap.exists()) {
                        txn.update(userRef, "coins", newBalance)
                    } else {
                        txn.set(
                            userRef,
                            mapOf(
                                "email" to (auth.currentUser?.email ?: ""),
                                "createdAt" to FieldValue.serverTimestamp(),
                                "nickname" to (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Quitter"),
                                "coins" to newBalance,
                                "xp" to 0L
                            ),
                            SetOptions.merge()
                        )
                    }
                    txn.set(
                        ledgerRef,
                        mapOf(
                            "amount" to reward,
                            "type" to "earn",
                            "source" to if (period == breathy.com.data.models.LeaderboardPeriod.WEEKLY)
                                "weekly_leaderboard" else "monthly_leaderboard",
                            "description" to (if (period == breathy.com.data.models.LeaderboardPeriod.WEEKLY)
                                "Weekly leaderboard" else "Monthly leaderboard") + " — rank #$myRank",
                            "dedupKey" to ledgerId,
                            "balanceAfter" to newBalance,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                    )
                    true
                }.await() ?: false
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "%s: leaderboard prize claim failed — will retry next load", TAG)
            false
        }

        if (credited) {
            Timber.i("%s: paid %s +%d Gold for %s rank #%d", TAG, uid, reward, period, myRank)
        }
        return FinalizeResult(finalizedKey, myRank, if (credited) reward else 0, credited)
    }
}

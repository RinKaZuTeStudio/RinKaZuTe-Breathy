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

        const val RANKED_SLOTS = 50

        fun weeklyReward(rank: Int): Int? = when {
            rank in 1..3 -> 1000
            rank in 4..10 -> 500
            rank in 11..RANKED_SLOTS -> 100
            else -> null
        }

        fun monthlyReward(rank: Int): Int? = when {
            rank in 1..3 -> 5000
            rank in 4..10 -> 3000
            rank in 11..RANKED_SLOTS -> 500
            else -> null
        }

        private const val DOC_WEEKLY = "weekly"
        private const val DOC_MONTHLY = "monthly"

        private fun archiveDocId(
            period: breathy.com.data.models.LeaderboardPeriod
        ): String? =
            when (period) {
                breathy.com.data.models.LeaderboardPeriod.WEEKLY -> DOC_WEEKLY
                breathy.com.data.models.LeaderboardPeriod.MONTHLY -> DOC_MONTHLY
                breathy.com.data.models.LeaderboardPeriod.ALL_TIME -> null
            }
    }

    data class ArchivedEntry(
        val uid: String,
        val nickname: String,
        val xp: Int,
        val rank: Int
    )

    data class FinalizeResult(
        val finalizedPeriodKey: String?,
        val myRank: Int?,
        val goldAwarded: Int,
        val credited: Boolean
    )

    private fun currentUid(): String? = auth.currentUser?.uid

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

                    breathy.com.data.models.LeaderboardPeriod.MONTHLY ->
                        UserRepository.monthlyXpPeriodKey()

                    breathy.com.data.models.LeaderboardPeriod.ALL_TIME ->
                        return@withTimeoutOrNull null
                }

                val archiveRef =
                    firestore.collection(ARCHIVE_COLLECTION).document(archiveId)

                val archiveSnap = archiveRef.get().await()
                val trackedKey = archiveSnap.getString("periodKey")

                if (archiveSnap.exists() && trackedKey == currentKey) {
                    return@withTimeoutOrNull claimFromArchive(
                        archiveRef = archiveRef,
                        uid = uid,
                        period = period,
                        finalizedKey = archiveSnap.getString("finalizedPeriodKey"),
                        rankings = archiveSnap.get("rankings") as? List<*>
                            ?: emptyList<Any>()
                    )
                }

                if (trackedKey == null) {
                    archiveRef.set(
                        mapOf(
                            "periodKey" to currentKey,
                            "finalizedPeriodKey" to null,
                            "rankings" to emptyList<Any>(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()

                    return@withTimeoutOrNull FinalizeResult(
                        finalizedPeriodKey = null,
                        myRank = null,
                        goldAwarded = 0,
                        credited = false
                    )
                }

                val rankings = snapshotRankings(
                    period = period,
                    closingKey = trackedKey
                )

                val finalized = finalizeInTransaction(
                    archiveRef = archiveRef,
                    currentKey = currentKey,
                    closingKey = trackedKey,
                    rankings = rankings
                )

                val fresh = if (finalized) {
                    archiveRef.get().await()
                } else {
                    archiveRef.get().await()
                }

                claimFromArchive(
                    archiveRef = archiveRef,
                    uid = uid,
                    period = period,
                    finalizedKey = fresh.getString("finalizedPeriodKey"),
                    rankings = fresh.get("rankings") as? List<*>
                        ?: emptyList<Any>()
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(
                e,
                "$TAG: finalizeAndClaim(%s) failed — non-fatal",
                period
            )
            null
        }
    }

    private suspend fun snapshotRankings(
        period: breathy.com.data.models.LeaderboardPeriod,
        closingKey: String
    ): List<ArchivedEntry> {
        val (xpField, periodField) = when (period) {
            breathy.com.data.models.LeaderboardPeriod.WEEKLY ->
                "weeklyXp" to "weeklyXpPeriod"

            breathy.com.data.models.LeaderboardPeriod.MONTHLY ->
                "monthlyXp" to "monthlyXpPeriod"

            breathy.com.data.models.LeaderboardPeriod.ALL_TIME ->
                return emptyList()
        }

        val snapshot = firestore
            .collection(PROFILES_COLLECTION)
            .orderBy(xpField, Query.Direction.DESCENDING)
            .limit(200L)
            .get()
            .await()

        val entries = mutableListOf<ArchivedEntry>()

        for (doc in snapshot.documents) {
            if (doc.getString(periodField) != closingKey) continue

            val xp = (doc.getLong(xpField) ?: 0L).toInt()

            if (xp <= 0) continue

            val nickname = doc.getString("nickname")
                ?.takeIf { it.isNotBlank() }
                ?: continue

            entries += ArchivedEntry(
                uid = doc.id,
                nickname = nickname,
                xp = xp,
                rank = entries.size + 1
            )

            if (entries.size >= RANKED_SLOTS) {
                break
            }
        }

        return entries
    }

    private suspend fun finalizeInTransaction(
        archiveRef: com.google.firebase.firestore.DocumentReference,
        currentKey: String,
        closingKey: String,
        rankings: List<ArchivedEntry>
    ): Boolean {
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                firestore.runTransaction { txn ->
                    val snap = txn.get(archiveRef)

                    val tracked = snap.getString("periodKey")

                    if (snap.exists() && tracked != closingKey) {
                        return@runTransaction false
                    }

                    txn.set(
                        archiveRef,
                        mapOf(
                            "periodKey" to currentKey,
                            "finalizedPeriodKey" to closingKey,
                            "rankings" to rankings.map { entry ->
                                mapOf(
                                    "uid" to entry.uid,
                                    "nickname" to entry.nickname,
                                    "xp" to entry.xp,
                                    "rank" to entry.rank
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
            Timber.w(
                e,
                "$TAG: finalize transaction failed"
            )
            false
        }
    }

    private suspend fun claimFromArchive(
        archiveRef: com.google.firebase.firestore.DocumentReference,
        uid: String,
        period: breathy.com.data.models.LeaderboardPeriod,
        finalizedKey: String?,
        rankings: List<*>
    ): FinalizeResult {
        if (finalizedKey.isNullOrBlank() || rankings.isEmpty()) {
            return FinalizeResult(
                finalizedPeriodKey = finalizedKey,
                myRank = null,
                goldAwarded = 0,
                credited = false
            )
        }

        val typed = rankings
            .filterIsInstance<Map<String, Any?>>()

        val mine = typed.firstOrNull {
            it["uid"] as? String == uid
        }

        val myRank =
            (mine?.get("rank") as? Long)?.toInt()
                ?: (mine?.get("rank") as? Int)

        val reward = when (period) {
            breathy.com.data.models.LeaderboardPeriod.WEEKLY ->
                myRank?.let(::weeklyReward)

            breathy.com.data.models.LeaderboardPeriod.MONTHLY ->
                myRank?.let(::monthlyReward)

            breathy.com.data.models.LeaderboardPeriod.ALL_TIME ->
                null
        } ?: return FinalizeResult(
            finalizedPeriodKey = finalizedKey,
            myRank = myRank,
            goldAwarded = 0,
            credited = false
        )

        val userRef =
            firestore.collection(USERS_COLLECTION).document(uid)

        val ledgerId = when (period) {
            breathy.com.data.models.LeaderboardPeriod.WEEKLY ->
                "weekly_lb_$finalizedKey"

            breathy.com.data.models.LeaderboardPeriod.MONTHLY ->
                "monthly_lb_$finalizedKey"

            breathy.com.data.models.LeaderboardPeriod.ALL_TIME ->
                return FinalizeResult(
                    finalizedPeriodKey = finalizedKey,
                    myRank = myRank,
                    goldAwarded = 0,
                    credited = false
                )
        }

        val ledgerRef =
            userRef.collection(TX_COLLECTION).document(ledgerId)

        val credited = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                firestore.runTransaction { txn ->
                    val userSnap = txn.get(userRef)
                    val ledgerSnap = txn.get(ledgerRef)

                    if (!userSnap.exists()) {
                        Timber.w(
                            "$TAG: refusing leaderboard payout for missing user document uid=%s",
                            uid
                        )
                        return@runTransaction false
                    }

                    if (ledgerSnap.exists()) {
                        return@runTransaction false
                    }

                    val currentBalance =
                        (userSnap.getLong("coins") ?: 0L).toInt()

                    val newBalance = currentBalance + reward

                    txn.update(
                        userRef,
                        "coins",
                        newBalance
                    )

                    txn.set(
                        ledgerRef,
                        mapOf(
                            "amount" to reward,
                            "type" to "earn",
                            "source" to when (period) {
                                breathy.com.data.models.LeaderboardPeriod.WEEKLY ->
                                    "weekly_leaderboard"

                                breathy.com.data.models.LeaderboardPeriod.MONTHLY ->
                                    "monthly_leaderboard"

                                else -> "leaderboard"
                            },
                            "description" to "Leaderboard — rank #$myRank",
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
            Timber.w(
                e,
                "$TAG: leaderboard prize claim failed"
            )
            false
        }

        if (credited) {
            Timber.i(
                "$TAG: paid uid=%s +%d Gold for %s rank #%d",
                uid,
                reward,
                period,
                myRank
            )
        }

        return FinalizeResult(
            finalizedPeriodKey = finalizedKey,
            myRank = myRank,
            goldAwarded = if (credited) reward else 0,
            credited = credited
        )
    }
}

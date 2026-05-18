package breathy.com.data.repository

import breathy.com.data.models.Achievement
import breathy.com.data.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Reward repository — XP/level calculations, achievement definitions,
 * achievement checking/unlocking, and money-saved calculations.
 *
 * All achievement conditions are checked client-side and unlocked atomically
 * using Firestore transactions. Achievement definitions are stored as constants.
 *
 * Features:
 * - Level/XP progress calculations from the PRD threshold table
 * - Achievement condition checking and atomic unlocking with XP rewards
 * - Money-saved calculations based on user smoking data
 * - Real-time achievement observation
 * - 30-second timeout on all network operations
 */
class RewardRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository
) {

    companion object {
        private const val TAG = "RewardRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val USERS_COLLECTION = "users"
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"

        // ═════════════════════════════════════════════════════════════════════
        //  Level thresholds — from PRD specification
        // ═════════════════════════════════════════════════════════════════════
        val LEVEL_THRESHOLDS = listOf(
            0,      // Level 1
            100,    // Level 2
            300,    // Level 3
            600,    // Level 4
            1000,   // Level 5
            1500,   // Level 6
            2200,   // Level 7
            3000,   // Level 8
            4000,   // Level 9
            5500    // Level 10
        )
        const val XP_PER_LEVEL_AFTER_10 = 1500

        // ═════════════════════════════════════════════════════════════════════
        //  Achievement definitions — PRD-specified
        // ═════════════════════════════════════════════════════════════════════
        val ACHIEVEMENT_DEFINITIONS = listOf(
            Achievement("first_day", "First Breath", "Complete Day 1 smoke-free", "\uD83C\uDF2C\uFE0F", 100),
            Achievement("one_week", "One Week Strong", "Reach 7 days smoke-free", "\uD83D\uDCAA", 200),
            Achievement("one_month", "One Month Free", "Reach 30 days smoke-free", "\uD83C\uDFC6", 500),
            Achievement("three_months", "Three Months Free", "Reach 90 days smoke-free", "\uD83C\uDFAF", 750),
            Achievement("six_months", "Six Months Strong", "Reach 180 days smoke-free", "\uD83C\uDF1F", 1000),
            Achievement("one_year", "One Year Clean", "Reach 365 days smoke-free", "\uD83D\uDC51", 5000),
            Achievement("money_saver_100", "Money Saver", "Save \$100 total", "\uD83D\uDCB0", 150),
            Achievement("money_saver_1000", "Big Saver", "Save \$1,000 total", "\uD83C\uDFE6", 500),
            Achievement("craving_crusher_10", "Craving Crusher", "Successfully resist 10 cravings", "\uD83D\uDD25", 150),
            Achievement("craving_crusher_50", "Craving Master", "Successfully resist 50 cravings", "\uD83D\uDEE1\uFE0F", 300),
            Achievement("breathing_master", "Breathing Master", "Complete 100 breathing exercises", "\uD83E\uDDD8", 300),
            Achievement("community_star", "Community Star", "Receive 50 total likes on stories", "\u2B50", 200),
            Achievement("storyteller", "Storyteller", "Post 10 community stories", "\uD83D\uDCDD", 200),
            Achievement("event_champion", "Event Champion", "Complete an event challenge", "\uD83C\uDFC5", 400),
            Achievement("social_butterfly", "Social Butterfly", "Add 10 friends", "\uD83E\uDD8B", 200),
            Achievement("streak_keeper", "Streak Keeper", "Claim daily reward 30 days in a row", "\uD83D\uDCC5", 300),
            Achievement("level_5", "Level 5", "Reach Level 5", "\uD83D\uDE80", 250),
            Achievement("level_10", "Level 10", "Reach Level 10", "\uD83D\uDC51", 500),
            Achievement("ai_explorer", "AI Explorer", "Have 50 AI Coach conversations", "\uD83E\uDD16", 300)
        )

        // ═════════════════════════════════════════════════════════════════════
        //  XP reward constants per activity
        // ═════════════════════════════════════════════════════════════════════
        const val XP_DAILY_CLAIM = 5
        const val XP_STORY_POSTED = 15
        const val XP_STORY_LIKED_RECEIVED = 2
        const val XP_CRAVING_RESISTED = 10
        const val XP_BREATHING_EXERCISE = 5
        const val XP_EVENT_CHECKIN = 20
        const val XP_FRIEND_ADDED = 10
        const val XP_AI_COACH_MESSAGE = 1

        // ═════════════════════════════════════════════════════════════════════
        //  Coin reward constants per activity
        // ═════════════════════════════════════════════════════════════════════
        const val COINS_DAILY_MIN = 10
        const val COINS_DAILY_MAX = 50
        const val COINS_STORY_POSTED = 5
        const val COINS_CRAVING_RESISTED = 3
        const val COINS_EVENT_CHECKIN = 10
        const val COINS_ACHIEVEMENT_MULTIPLIER = 1 // coins = xpReward * multiplier
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // ═══════════════════════════════════════════════════════════════════════════
    //  Level calculations
    // ═══════════════════════════════════════════════════════════════════════════

    /** Calculate level from total XP. */
    fun calculateLevel(xp: Int): Int {
        if (xp < 0) return 1

        // Check against defined thresholds
        for (i in LEVEL_THRESHOLDS.indices.reversed()) {
            if (xp >= LEVEL_THRESHOLDS[i]) {
                // Beyond level 10: each additional XP_PER_LEVEL_AFTER_10 = 1 level
                if (i == LEVEL_THRESHOLDS.lastIndex) {
                    val xpBeyondLast = xp - LEVEL_THRESHOLDS.last()
                    val additionalLevels = xpBeyondLast / XP_PER_LEVEL_AFTER_10
                    return LEVEL_THRESHOLDS.size + additionalLevels
                }
                return i + 1
            }
        }
        return 1
    }

    /** Get XP needed to reach the next level from the current XP. */
    fun getXPForNextLevel(currentXP: Int): Int {
        if (currentXP < 0) return LEVEL_THRESHOLDS[0]

        for (i in LEVEL_THRESHOLDS.indices) {
            if (currentXP < LEVEL_THRESHOLDS[i]) {
                return LEVEL_THRESHOLDS[i] - currentXP
            }
        }

        // Beyond level 10
        val lastThreshold = LEVEL_THRESHOLDS.last()
        val xpBeyondLast = currentXP - lastThreshold
        val completedSteps = xpBeyondLast / XP_PER_LEVEL_AFTER_10
        val nextThresholdXP = lastThreshold + (completedSteps + 1) * XP_PER_LEVEL_AFTER_10
        return nextThresholdXP - currentXP
    }

    /** Get XP earned within the current level. */
    fun getCurrentLevelXP(xp: Int): Int {
        if (xp < 0) return 0

        var currentLevelStartXP = 0
        for (threshold in LEVEL_THRESHOLDS) {
            if (xp >= threshold) {
                currentLevelStartXP = threshold
            } else {
                break
            }
        }

        val lastThreshold = LEVEL_THRESHOLDS.last()
        if (xp >= lastThreshold) {
            val xpBeyondLast = xp - lastThreshold
            val completedSteps = xpBeyondLast / XP_PER_LEVEL_AFTER_10
            currentLevelStartXP = lastThreshold + completedSteps * XP_PER_LEVEL_AFTER_10
        }

        return xp - currentLevelStartXP
    }

    /** Get level progress as a 0..1 float. */
    fun getLevelProgress(xp: Int): Float {
        val level = calculateLevel(xp)
        val currentLevelXP = getCurrentLevelXP(xp)
        val xpNeeded = if (level <= LEVEL_THRESHOLDS.size) {
            val nextThreshold = LEVEL_THRESHOLDS.getOrElse(level) { LEVEL_THRESHOLDS.last() + XP_PER_LEVEL_AFTER_10 }
            val currentThreshold = LEVEL_THRESHOLDS.getOrElse(level - 1) { 0 }
            nextThreshold - currentThreshold
        } else {
            XP_PER_LEVEL_AFTER_10
        }
        if (xpNeeded <= 0) return 1f
        return (currentLevelXP.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  XP for a specific day (based on activities)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculate XP earned for a specific day of being smoke-free.
     * The first day gives 100 XP (First Breath), then diminishing
     * rewards based on the day number.
     */
    fun calculateXpForDay(dayNumber: Int): Int {
        return when {
            dayNumber <= 0 -> 0
            dayNumber == 1 -> 100  // First Breath bonus
            dayNumber <= 7 -> 15   // Week 1: 15 XP/day
            dayNumber <= 30 -> 10  // Month 1: 10 XP/day
            dayNumber <= 90 -> 8   // Month 1-3: 8 XP/day
            dayNumber <= 365 -> 5  // Month 3-12: 5 XP/day
            else -> 3              // Year 1+: 3 XP/day
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Money saved calculations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculate money saved based on smoking data and quit date.
     * @return the total money saved as a Double.
     */
    fun calculateMoneySaved(
        quitDate: Date,
        cigarettesPerDay: Int,
        pricePerPack: Double,
        cigarettesPerPack: Int
    ): Double {
        if (cigarettesPerPack <= 0 || cigarettesPerDay <= 0 || pricePerPack <= 0) return 0.0
        val days = userRepository.calculateDaysSmokeFree(quitDate)
        val costPerCigarette = pricePerPack / cigarettesPerPack
        return cigarettesPerDay * costPerCigarette * days
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Achievement definitions & retrieval
    // ═══════════════════════════════════════════════════════════════════════════

    /** Get all achievement definitions (without unlock state). */
    fun getAchievements(): List<Achievement> = ACHIEVEMENT_DEFINITIONS

    /** Get all achievements with unlock state for a given user. */
    suspend fun getAchievements(userId: String): Result<List<Achievement>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userDoc = try {
                firestore.collection(USERS_COLLECTION).document(userId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed for achievements — trying cache")
                try {
                    firestore.collection(USERS_COLLECTION).document(userId)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed")
                    throw e
                }
            }
            if (!userDoc.exists()) throw NoSuchElementException("User not found: $userId")
            val unlockedIds = (userDoc.get("achievements") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()
            Achievement.withUnlockState(unlockedIds)
        } ?: throw IllegalStateException("Get achievements timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get achievements for user: %s", userId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Achievement checking & unlocking
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check all achievement conditions for a user and unlock any that are newly met.
     * Uses Firestore transactions for atomic unlock + XP reward.
     *
     * @return the list of newly unlocked achievements (empty if none).
     */
    suspend fun checkAndUnlockAchievement(userId: String): Result<List<Achievement>> =
        runCatching {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val userDoc = try {
                    firestore.collection(USERS_COLLECTION).document(userId)
                        .get(Source.SERVER)
                        .await()
                } catch (e: Exception) {
                    Timber.w(e, "Server read failed for achievement check — trying cache")
                    try {
                        firestore.collection(USERS_COLLECTION).document(userId)
                            .get(Source.CACHE)
                            .await()
                    } catch (cacheEx: Exception) {
                        Timber.w(cacheEx, "Cache read also failed")
                        throw e
                    }
                }
                if (!userDoc.exists()) throw NoSuchElementException("User not found: $userId")
                val user = User.fromFirestoreMap(userDoc.data ?: emptyMap())
                val newlyUnlocked = checkAndUnlockAchievementsInternal(userId, user)
                newlyUnlocked.map { it.copy(unlocked = true) }
            } ?: throw IllegalStateException("Achievement check timed out after 30 seconds")
        }.onFailure { e ->
            if (e !is CancellationException) Timber.e(e, "Failed to check achievements for user: %s", userId)
        }

    /**
     * Internal method that checks conditions and performs atomic unlocks.
     * Called from within a timeout block.
     */
    private suspend fun checkAndUnlockAchievementsInternal(
        userId: String,
        user: User
    ): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()
        val existingAchievements = user.achievements.toSet()
        val daysSmokeFree = user.daysSmokeFree
        val moneySaved = user.moneySaved()
        val currentLevel = calculateLevel(user.xp)
        val successfulCravings = userRepository.getSuccessfulCravingCount(userId)

        val conditions = mapOf<String, Boolean>(
            "first_day" to (daysSmokeFree >= 1),
            "one_week" to (daysSmokeFree >= 7),
            "one_month" to (daysSmokeFree >= 30),
            "three_months" to (daysSmokeFree >= 90),
            "six_months" to (daysSmokeFree >= 180),
            "one_year" to (daysSmokeFree >= 365),
            "money_saver_100" to (moneySaved >= 100.0),
            "money_saver_1000" to (moneySaved >= 1000.0),
            "craving_crusher_10" to (successfulCravings >= 10),
            "craving_crusher_50" to (successfulCravings >= 50),
            "level_5" to (currentLevel >= 5),
            "level_10" to (currentLevel >= 10)
        )

        for ((achievementId, conditionMet) in conditions) {
            if (conditionMet && achievementId !in existingAchievements) {
                val achievement = ACHIEVEMENT_DEFINITIONS.find { it.id == achievementId }
                if (achievement != null) {
                    try {
                        // Atomically add achievement and award XP using a transaction
                        val userRef = firestore.collection(USERS_COLLECTION).document(userId)
                        val profileRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)

                        firestore.runTransaction { transaction ->
                            val snapshot = transaction.get(userRef)
                            val currentAchievements = (snapshot.get("achievements") as? List<*>)
                                ?.filterIsInstance<String>()?.toSet() ?: emptySet()

                            // Double-check inside transaction to avoid race conditions
                            if (achievementId !in currentAchievements) {
                                val currentXp = (snapshot.getLong("xp") ?: 0L).toInt()
                                val newXp = currentXp + achievement.xpReward
                                transaction.update(userRef, mapOf(
                                    "achievements" to FieldValue.arrayUnion(achievementId),
                                    "xp" to newXp
                                ))
                                transaction.update(profileRef, "xp", newXp)
                            }
                        }.await()

                        newlyUnlocked.add(achievement)
                        Timber.i("Achievement unlocked: %s for user: %s", achievementId, userId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to unlock achievement %s for user: %s", achievementId, userId)
                    }
                }
            }
        }

        return newlyUnlocked
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Award helpers — Firestore transactions for consistency
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Grant XP to a user with an optional reason (for logging).
     * Updates both the user document and the public profile.
     * @return the new total XP after the addition.
     */
    suspend fun grantXp(userId: String, amount: Int, reason: String = ""): Result<Int> =
        runCatching {
            if (amount < 0) throw IllegalArgumentException("XP amount must be non-negative")
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val userRef = firestore.collection(USERS_COLLECTION).document(userId)
                val profileRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val currentXp = (snapshot.getLong("xp") ?: 0L).toInt()
                    val newXp = currentXp + amount
                    transaction.update(userRef, "xp", newXp)
                    transaction.update(profileRef, "xp", newXp)
                    newXp
                }.await()
            } ?: throw IllegalStateException("Grant XP timed out after 30 seconds")
        }.onFailure { e ->
            if (e !is CancellationException) Timber.e(e, "Failed to grant XP to user: %s (reason: %s)", userId, reason)
        }

    /**
     * Grant coins to a user with an optional reason (for logging).
     * @return the new total coins after the addition.
     */
    suspend fun grantCoins(userId: String, amount: Int, reason: String = ""): Result<Int> =
        runCatching {
            if (amount < 0) throw IllegalArgumentException("Coin amount must be non-negative")
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val userRef = firestore.collection(USERS_COLLECTION).document(userId)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val currentCoins = (snapshot.getLong("coins") ?: 0L).toInt()
                    val newCoins = currentCoins + amount
                    transaction.update(userRef, "coins", newCoins)
                    newCoins
                }.await()
            } ?: throw IllegalStateException("Grant coins timed out after 30 seconds")
        }.onFailure { e ->
            if (e !is CancellationException) Timber.e(e, "Failed to grant coins to user: %s (reason: %s)", userId, reason)
        }

    /**
     * Award XP to a user (alias for grantXp without return value).
     * Updates both user and public profile.
     */
    suspend fun awardXP(userId: String, amount: Int): Result<Unit> = runCatching {
        if (amount < 0) throw IllegalArgumentException("XP amount must be non-negative")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)
            val profileRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentXp = (snapshot.getLong("xp") ?: 0L).toInt()
                val newXp = currentXp + amount
                transaction.update(userRef, "xp", newXp)
                transaction.update(profileRef, "xp", newXp)
            }.await()
            Unit
        } ?: throw IllegalStateException("Award XP timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to award XP to user: %s", userId)
    }

    /**
     * Award coins to a user (alias for grantCoins without return value).
     */
    suspend fun awardCoins(userId: String, amount: Int): Result<Unit> = runCatching {
        if (amount < 0) throw IllegalArgumentException("Coin amount must be non-negative")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentCoins = (snapshot.getLong("coins") ?: 0L).toInt()
                val newCoins = currentCoins + amount
                transaction.update(userRef, "coins", newCoins)
            }.await()
            Unit
        } ?: throw IllegalStateException("Award coins timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to award coins to user: %s", userId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time observers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Observe unlocked achievements in real-time by watching the user document. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUnlockedAchievements(userId: String): Flow<List<Achievement>> =
        callbackFlow {
            val registration = firestore.collection(USERS_COLLECTION).document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "observeUnlockedAchievements error for %s", userId)
                        // Don't close the flow on transient errors — emit empty list
                        // as fallback so the UI can still render.
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val unlockedIds = (snapshot.get("achievements") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                        val achievements = ACHIEVEMENT_DEFINITIONS.filter { unlockedIds.contains(it.id) }
                        trySend(achievements)
                    }
                }
            awaitClose { registration.remove() }
        }
}

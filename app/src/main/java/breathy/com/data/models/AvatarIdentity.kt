package breathy.com.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rarity tier for avatar frames — drives presentation (labels, colors,
 * unlock animations) in the Avatar Collection.
 */
@Serializable
enum class FrameRarity(val label: String) {
    @SerialName("common") COMMON("Common"),
    @SerialName("uncommon") UNCOMMON("Uncommon"),
    @SerialName("rare") RARE("Rare"),
    @SerialName("epic") EPIC("Epic"),
    @SerialName("legendary") LEGENDARY("Legendary"),
    @SerialName("premium") PREMIUM("Premium");
}

/**
 * Avatar frame/border system for Breathy.
 *
 * Frames are the visual ring drawn around a user's avatar everywhere the
 * profile is displayed (profile page, leaderboard, community posts, event
 * participants, friends, chat). The artwork comes from the official
 * "Avatar Borders Collection" sprite sheet (res/drawable-nodpi/frame_*.png).
 *
 * Unlock rules — smoke-free DAY-based progression (v1.0.7):
 * - [NONE] (Classic, COMMON): owned from the moment the account is created.
 * - [NATURE] (COMMON): unlocks on Day 7 smoke-free.
 * - [LEAF] (UNCOMMON): unlocks on Day 30 smoke-free.
 * - [BRONZE] / [SILVER] / [GOLD]: one-time Gold purchases (no level unlock).
 * - [ACHIEVEMENT]: requires at least one unlocked achievement.
 * - [EVENT]: requires the `event_champion` achievement (completing an
 *   event challenge) — the app already awards that achievement.
 * - [PREMIUM]: requires a verified active Breathy Premium subscription.
 * - [RANK]: the living rank border — reach Tree rank (Level 9).
 *
 * The value persisted in Firestore (users.avatarFrame / publicProfiles.avatarFrame)
 * is the [id] string, so renaming display labels never breaks stored data.
 * Gold-purchased ownership is persisted in users.ownedFrames (list of [id]s).
 */
@Serializable
enum class AvatarFrame(
    val id: String,
    val label: String,
    val description: String,
    val rarity: FrameRarity,
    /** One-time Gold price when purchasable; null = cannot be bought with Gold. */
    val goldPrice: Int? = null
) {
    @SerialName("none") NONE("none", "Classic", "Clean · Simple · Timeless. Yours from day one.", FrameRarity.COMMON),
    @SerialName("nature") NATURE("nature", "Nature", "Natural · Organic · Calm. Unlocks on Day 7 smoke-free.", FrameRarity.COMMON),
    @SerialName("leaf") LEAF("leaf", "Leaf", "Fresh · Lively · Green. Unlocks on Day 30 smoke-free.", FrameRarity.UNCOMMON),
    @SerialName("bronze") BRONZE("bronze", "Bronze", "Strong · Solid · Bronze. Unlock instantly with Gold.", FrameRarity.RARE, goldPrice = 250),
    @SerialName("silver") SILVER("silver", "Silver", "Elegant · Refined · Silver. Unlock instantly with Gold.", FrameRarity.RARE, goldPrice = 600),
    @SerialName("gold") GOLD("gold", "Gold", "Prestigious · Rich · Gold. Unlock instantly with Gold.", FrameRarity.EPIC, goldPrice = 1200),
    @SerialName("achievement") ACHIEVEMENT("achievement", "Achievement", "Reward · Milestone · Honor. Unlock any achievement to wear.", FrameRarity.RARE),
    @SerialName("event") EVENT("event", "Event", "Exclusive · Time-limited · Complete an event challenge to wear.", FrameRarity.LEGENDARY),
    @SerialName("premium") PREMIUM("premium", "Premium", "Ultimate · Exclusive · For Breathy Premium members.", FrameRarity.PREMIUM),
    @SerialName("rank") RANK("rank", "Rank", "Competitive · Bold · Reach Tree rank (Level 9) to wear.", FrameRarity.EPIC);

    override fun toString(): String = id

    companion object {
        /** Resolve a Firestore string to the enum; unknown values fall back to [NONE]. */
        fun fromId(id: String?): AvatarFrame =
            entries.find { it.id == id } ?: NONE
    }

    /**
     * Whether this frame is unlocked for the given REAL progression state.
     *
     * v1.0.7 schedule: Classic from signup; Nature on Day 7; Leaf on Day 30.
     * Bronze/Silver/Gold are Gold-purchase only (ownedFrames) and return
     * false here. Premium requires the verified subscription entitlement
     * (never a local toggle). Rank requires Tree rank (Level 9).
     */
    fun isUnlockedFor(
        level: Int,
        hasAchievement: Boolean,
        hasEventWin: Boolean,
        isPremium: Boolean,
        daysSmokeFree: Int = 0
    ): Boolean = when (this) {
        NONE -> true
        NATURE -> daysSmokeFree >= 7
        LEAF -> daysSmokeFree >= 30
        RANK -> level >= 9
        BRONZE, SILVER, GOLD -> false // Gold purchase only (users.ownedFrames)
        ACHIEVEMENT -> hasAchievement
        EVENT -> hasEventWin
        PREMIUM -> isPremium
    }
}

/**
 * Nature-inspired visual rank identity, derived from the existing level system
 * ([User.computeLevel]). This is a presentation-layer mapping ONLY — it does
 * not change any rank/level/XP calculation.
 *
 * Progression: Seed → Sprout → Leaf → Plant → Tree → Forest → Evergreen.
 */
@Serializable
enum class RankTier(
    val label: String,
    val minLevel: Int,
    val icon: String
) {
    SEED("Seed", 1, "🌰"),
    SPROUT("Sprout", 3, "🌱"),
    LEAF("Leaf", 5, "🍃"),
    PLANT("Plant", 7, "🪴"),
    TREE("Tree", 9, "🌳"),
    FOREST("Forest", 12, "🌲"),
    EVERGREEN("Evergreen", 15, "🌿");

    companion object {
        /** Resolve the tier for a level; unknown levels clamp to the highest tier. */
        fun forLevel(level: Int): RankTier {
            var result = SEED
            for (tier in entries) {
                if (level >= tier.minLevel) result = tier
            }
            return result
        }
    }

    /** The next tier, or null when already at the top. */
    val next: RankTier?
        get() = entries.getOrNull(ordinal + 1)

    /** Progress (0f..1f) toward the next tier based on the given level. */
    fun progressToNext(level: Int): Float {
        val nextTier = next ?: return 1f
        val span = (nextTier.minLevel - minLevel).coerceAtLeast(1)
        val progress = (level - minLevel + 1).toFloat() / span
        return progress.coerceIn(0f, 1f)
    }
}

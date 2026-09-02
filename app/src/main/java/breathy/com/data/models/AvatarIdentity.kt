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
 * participants, friends, chat).
 *
 * Unlock rules — intentionally honest, tied to REAL progression data only:
 * - [NONE], [NATURE], [LEAF]: available to everyone.
 * - [BRONZE] / [SILVER] / [GOLD]: unlocked FREE by the user's real level
 *   (derived from XP via [User.computeLevel]) — levels 3 / 5 / 8 — or
 *   instantly via a one-time Gold purchase (see [goldPrice]).
 * - [ACHIEVEMENT]: requires at least one unlocked achievement.
 * - [EVENT]: requires the `event_champion` achievement (completing an
 *   event challenge) — the app already awards that achievement.
 * - [PREMIUM]: requires a verified active Breathy Premium subscription.
 * - [RANK]: always selectable — the border automatically reflects the
 *   user's current [RankTier] (nature-inspired progression identity).
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
    @SerialName("none") NONE("none", "Classic", "A clean, minimal ring.", FrameRarity.COMMON),
    @SerialName("nature") NATURE("nature", "Nature", "A soft sage ring inspired by new growth.", FrameRarity.COMMON),
    @SerialName("leaf") LEAF("leaf", "Leaf", "A botanical leaf-green ring.", FrameRarity.UNCOMMON),
    @SerialName("bronze") BRONZE("bronze", "Bronze", "Reach Level 3 — or unlock instantly with Gold.", FrameRarity.RARE, goldPrice = 250),
    @SerialName("silver") SILVER("silver", "Silver", "Reach Level 5 — or unlock instantly with Gold.", FrameRarity.RARE, goldPrice = 600),
    @SerialName("gold") GOLD("gold", "Gold", "Reach Level 8 — or unlock instantly with Gold.", FrameRarity.EPIC, goldPrice = 1200),
    @SerialName("achievement") ACHIEVEMENT("achievement", "Achievement", "Unlock any achievement to wear.", FrameRarity.RARE),
    @SerialName("event") EVENT("event", "Event", "Complete an event challenge to wear.", FrameRarity.LEGENDARY),
    @SerialName("premium") PREMIUM("premium", "Premium", "Exclusive for Breathy Premium members.", FrameRarity.PREMIUM),
    @SerialName("rank") RANK("rank", "Rank", "A living border that shows your current rank.", FrameRarity.EPIC);

    override fun toString(): String = id

    companion object {
        /** Resolve a Firestore string to the enum; unknown values fall back to [NONE]. */
        fun fromId(id: String?): AvatarFrame =
            entries.find { it.id == id } ?: NONE
    }

    /**
     * Whether this frame is unlocked for the given REAL progression state.
     * Level thresholds match the tier mapping below; premium requires the
     * verified subscription entitlement (never a local toggle).
     */
    fun isUnlockedFor(
        level: Int,
        hasAchievement: Boolean,
        hasEventWin: Boolean,
        isPremium: Boolean
    ): Boolean = when (this) {
        NONE, NATURE, LEAF, RANK -> true
        BRONZE -> level >= 3
        SILVER -> level >= 5
        GOLD -> level >= 8
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

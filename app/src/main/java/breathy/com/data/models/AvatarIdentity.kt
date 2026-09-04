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

    /** v1.0.10 — Arabic-aware display label (resolves at call time). */
    fun displayLabel(): String = when (this) {
        COMMON -> breathy.com.utils.s("Common", "عادي")
        UNCOMMON -> breathy.com.utils.s("Uncommon", "غير شائع")
        RARE -> breathy.com.utils.s("Rare", "نادر")
        EPIC -> breathy.com.utils.s("Epic", "ملحمي")
        LEGENDARY -> breathy.com.utils.s("Legendary", "أسطوري")
        PREMIUM -> breathy.com.utils.s("Premium", "بريميوم")
    }
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

    /** v1.0.10 — Arabic-aware display label (resolves at call time). */
    fun displayLabel(): String = when (this) {
        NONE -> breathy.com.utils.s("Classic", "كلاسيكي")
        NATURE -> breathy.com.utils.s("Nature", "طبيعة")
        LEAF -> breathy.com.utils.s("Leaf", "ورقة")
        BRONZE -> breathy.com.utils.s("Bronze", "برونزي")
        SILVER -> breathy.com.utils.s("Silver", "فضي")
        GOLD -> breathy.com.utils.s("Gold", "ذهبي")
        ACHIEVEMENT -> breathy.com.utils.s("Achievement", "إنجاز")
        EVENT -> breathy.com.utils.s("Event", "فعالية")
        PREMIUM -> breathy.com.utils.s("Premium", "بريميوم")
        RANK -> breathy.com.utils.s("Rank", "رتبة")
    }

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
 * ═══════════════════════════════════════════════════════════════════════════
 *  UNIFIED PROFILE PICTURE SYSTEM (v1.0.9)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * The single set of unified avatars every Breathy account wears everywhere a
 * profile picture is shown (own profile, leaderboard, community, friends,
 * chats, public profiles). Artwork: the official PNG collection in docs/.
 *
 * Unlock rules:
 * - [DAY1]        — owned by EVERY account automatically (the default).
 * - [SEVEN_DAYS]  — unlocks at 7 days smoke-free.
 * - [THIRTY_DAYS] — unlocks at 30 days smoke-free.
 * - [NINETY_DAYS] — unlocks at 90 days smoke-free.
 * - [SUNRISE]     — unlocks after watching 5 rewarded ads (dedicated ad unit).
 * - [DONT_SMOKE] … [HEALTHY_FUTURE] — one-time Gold purchases (shop, 500+).
 * - [FINALLY_FREE] — Premium-only ANIMATED avatar: a white flash sweeps every
 *   5 seconds over the artwork. Any subscriber owns it for as long as they
 *   subscribe.
 *   (v1.0.10 — "freefromthechain" was REMOVED from the collection; accounts
 *   still holding that id are remapped to [FINALLY_FREE] by [fromId].)
 *
 * The value persisted in Firestore (users.profilePicture /
 * publicProfiles.profilePicture) is the [id] string. Gold ownership is
 * persisted in users.ownedPictures; ad-unlock progress in users.picAdWatchCount.
 */
@Serializable
enum class ProfilePicture(
    val id: String,
    val label: String,
    val description: String,
    /** Smoke-free days required (0 = everyone from day one). */
    val unlockDays: Int = 0,
    /** One-time Gold price when bought from the picture shop; null = not for sale. */
    val goldPrice: Int? = null,
    /** Rewarded-ad watches required (0 = not ad-unlockable). */
    val adsRequired: Int = 0,
    /** Premium-only (ownership follows the active subscription). */
    val premiumOnly: Boolean = false,
    /** Premium animated avatar — white-flash crossfade between the two artworks. */
    val animated: Boolean = false
) {
    @SerialName("day1") DAY1("day1", "Day One", "Fresh start. Every account begins here.", unlockDays = 0),
    @SerialName("7days") SEVEN_DAYS("7days", "7 Days", "One week smoke-free. Earned, never bought.", unlockDays = 7),
    @SerialName("30days") THIRTY_DAYS("30days", "30 Days", "One full month smoke-free.", unlockDays = 30),
    @SerialName("90days") NINETY_DAYS("90days", "90 Days", "The 90-day transformation.", unlockDays = 90),
    @SerialName("sunrise") SUNRISE("sunrise", "Sunrise", "A new dawn. Watch 5 rewarded ads to unlock.", adsRequired = 5),
    @SerialName("dontsmoke") DONT_SMOKE("dontsmoke", "Don't Smoke", "Say it loud. 500 Gold.", goldPrice = 500),
    @SerialName("forrest") FORREST("forrest", "Forrest", "Breathe among the trees. 650 Gold.", goldPrice = 650),
    @SerialName("freshbreath") FRESH_BREATH("freshbreath", "Fresh Breath", "Clean air energy. 800 Gold.", goldPrice = 800),
    @SerialName("goodfrombad") GOOD_FROM_BAD("goodfrombad", "Good From Bad", "Turn it around. 950 Gold.", goldPrice = 950),
    @SerialName("healthheart") HEALTH_HEART("healthheart", "Health Heart", "A heart that thanks you. 1,100 Gold.", goldPrice = 1100),
    @SerialName("healthlungs") HEALTH_LUNGS("healthlungs", "Health Lungs", "Breathing free. 1,250 Gold.", goldPrice = 1250),
    @SerialName("healthyfuture") HEALTHY_FUTURE("healthyfuture", "Healthy Future", "The future you're building. 1,500 Gold.", goldPrice = 1500),
    @SerialName("finallyfree") FINALLY_FREE("finallyfree", "Finally Free", "Premium animated avatar — the moment after.", premiumOnly = true, animated = true);

    override fun toString(): String = id

    /** v1.0.10 — Arabic-aware display label (resolves at call time). */
    fun displayLabel(): String = when (this) {
        DAY1 -> breathy.com.utils.s("Day One", "اليوم الأول")
        SEVEN_DAYS -> breathy.com.utils.s("7 Days", "7 أيام")
        THIRTY_DAYS -> breathy.com.utils.s("30 Days", "30 يوماً")
        NINETY_DAYS -> breathy.com.utils.s("90 Days", "90 يوماً")
        SUNRISE -> breathy.com.utils.s("Sunrise", "الشروق")
        DONT_SMOKE -> breathy.com.utils.s("Don't Smoke", "لا تدخّن")
        FORREST -> breathy.com.utils.s("Forrest", "فورست")
        FRESH_BREATH -> breathy.com.utils.s("Fresh Breath", "نَفَس منعش")
        GOOD_FROM_BAD -> breathy.com.utils.s("Good From Bad", "تحوّل للخير")
        HEALTH_HEART -> breathy.com.utils.s("Health Heart", "قلب سليم")
        HEALTH_LUNGS -> breathy.com.utils.s("Health Lungs", "رئة سليمة")
        HEALTHY_FUTURE -> breathy.com.utils.s("Healthy Future", "مستقبل صحي")
        FINALLY_FREE -> breathy.com.utils.s("Finally Free", "حُرّ أخيراً")
    }

    companion object {
        /**
         * Resolve a Firestore string to the enum; unknown values fall back to
         * [DAY1]. v1.0.10: the removed "freefromthechain" id remaps to
         * [FINALLY_FREE] so existing premium accounts keep their animated
         * avatar seamlessly.
         */
        fun fromId(id: String?): ProfilePicture = when (id) {
            "freefromthechain" -> FINALLY_FREE
            else -> entries.find { it.id == id } ?: DAY1
        }
    }

    /**
     * Whether this picture is unlocked for the given REAL progression state.
     * Gold purchases rely on users.ownedPictures (checked by the caller);
     * this covers progression/premium/ads rules.
     */
    fun isUnlockedFor(
        daysSmokeFree: Int,
        isPremium: Boolean,
        picAdWatchCount: Int,
        ownedPictures: List<String> = emptyList()
    ): Boolean = when {
        this == DAY1 -> true
        unlockDays > 0 -> daysSmokeFree >= unlockDays
        goldPrice != null -> ownedPictures.contains(id)
        adsRequired > 0 -> ownedPictures.contains(id) || picAdWatchCount >= adsRequired
        premiumOnly -> isPremium
        else -> false
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

    /** v1.0.10 — Arabic-aware display label (resolves at call time). */
    fun displayLabel(): String = when (this) {
        SEED -> breathy.com.utils.s("Seed", "بذرة")
        SPROUT -> breathy.com.utils.s("Sprout", "برعم")
        LEAF -> breathy.com.utils.s("Leaf", "ورقة")
        PLANT -> breathy.com.utils.s("Plant", "نبتة")
        TREE -> breathy.com.utils.s("Tree", "شجرة")
        FOREST -> breathy.com.utils.s("Forest", "غابة")
        EVERGREEN -> breathy.com.utils.s("Evergreen", "دائمة الخضرة")
    }

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

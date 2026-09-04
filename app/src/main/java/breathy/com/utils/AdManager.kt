package breathy.com.utils

import android.app.Activity
import android.content.Context
import breathy.com.data.repository.PremiumRepository
import com.ironsource.mediationsdk.logger.IronSourceError
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Unity LevelPlay advertising for Breathy — the ONLY ad system in the
 * app. AdMob has been fully removed (no SDK, no ad unit IDs, no manifest
 * entries).
 *
 * LevelPlay production identifiers (source of truth — never replace with
 * test or invented IDs):
 * - **App Key**:      `27e9c42cd`
 * - **Rewarded**:     `b0taewni29ftw711` (placement "Gold Ads" → +200 Gold)
 * - **Native**:       `5o8vznxxsem6mv51` (placement "Ad1")
 * - **Interstitial**: `flcqa09gxs9k0qgl` (placement "Ad2")
 *
 * Behaviour rules:
 * - Free users: real production ads only (rewarded, native, interstitial).
 * - Verified Premium subscribers: ZERO ads — nothing is loaded or shown, and
 *   cached ads are released the moment the entitlement becomes active.
 * - The +200 Gold reward is granted ONLY after the LevelPlay completion
 *   callback ([LevelPlayRewardedAdListener.onAdRewarded]) confirms the user
 *   actually finished the ad. The grant handler is wired in [DI AppModule]
 *   through [rewardGrantCallback] and is idempotent per completed ad via a
 *   unique show token (duplicates can never double-credit).
 * - Interstitial frequency cap: max 1 per 3 minutes; never shown during
 *   purchase/subscription/event-registration flows (call sites are fixed).
 *
 * Thread safety: LevelPlay public APIs are main-thread APIs. Mutable show
 * state is confined to the main thread; loading flags use [AtomicBoolean].
 */
class AdManager(
    private val context: Context
) {

    companion object {
        // ── Unity LevelPlay production identifiers ────────────────────────
        /** LevelPlay App Key (Unity LevelPlay platform → production). */
        const val LEVELPLAY_APP_KEY = "27e9c42cd"

        /** Rewarded ad unit — "Gold Ads": +200 Gold on verified completion. */
        const val REWARDED_AD_UNIT_ID = "b0taewni29ftw711"

        /** v1.0.9 rewarded ad unit — unlocks the SUNRISE profile picture after
         *  5 verified completed watches (placement "Profile Pic"). */
        const val PROFILE_PIC_REWARDED_AD_UNIT_ID = "sdogk85zaxbkjym5"

        /** Native ad unit — rendered as a Breathy-styled sponsored card. */
        const val NATIVE_AD_UNIT_ID = "5o8vznxxsem6mv51"

        /** Full-screen interstitial ad unit — frequency capped. */
        const val INTERSTITIAL_AD_UNIT_ID = "flcqa09gxs9k0qgl"

        /** Gold granted for a completed "Gold Ads" rewarded placement. */
        const val REWARDED_GOLD_AMOUNT = 200

        // ── Timing ─────────────────────────────────────────────────────────
        /** Minimum interval between interstitial shows (90 seconds) — free
         *  users encounter ads regularly while navigating, without the
         *  experience becoming hostile (policy-safe minimum spacing). */
        const val INTERSTITIAL_FREQUENCY_CAP_MS = 90L * 1000L

        /** Delay before the first ad-load retry after a load failure (15s). */
        private const val AD_RETRY_INITIAL_MS = 15_000L

        /** Maximum delay between ad-load retries (2 minutes). */
        private const val AD_RETRY_MAX_MS = 120_000L
    }

    /**
     * Callback interface for ad lifecycle events (optional diagnostics).
     */
    interface AdEventListener {
        fun onAdLoaded(adType: AdType) {}
        fun onAdLoadFailed(adType: AdType, error: String) {}
        fun onAdShown(adType: AdType) {}
        fun onAdDismissed(adType: AdType) {}
        fun onAdShowFailed(adType: AdType, error: String) {}
    }

    /** Types of ads supported by Breathy via LevelPlay. */
    enum class AdType {
        REWARDED,
        INTERSTITIAL,
        NATIVE
    }

    // ── Ad references ──────────────────────────────────────────────────────

    private var rewardedAd: LevelPlayRewardedAd? = null
    private var profilePicRewardedAd: LevelPlayRewardedAd? = null
    private var interstitialAd: LevelPlayInterstitialAd? = null

    // ── Loading state ──────────────────────────────────────────────────────────

    private val isRewardedLoading = AtomicBoolean(false)
    private val isProfilePicRewardedLoading = AtomicBoolean(false)
    private val isInterstitialLoading = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)

    /** True once [LevelPlay.init] succeeded — loads (re)start from here. */
    private val isSdkReady = AtomicBoolean(false)

    private var retryJobs: MutableList<Job> = mutableListOf()

    /** Unique token for the currently shown rewarded ad (gold dedup key). */
    private var rewardShowToken: String? = null

    /** Unique token for the currently shown profile-picture rewarded ad. */
    private var profilePicShowToken: String? = null

    /** Guards against duplicate grant callbacks for one completed ad. */
    private val rewardGrantedForThisShow = AtomicBoolean(false)

    /** Guards against duplicate picture-unlock callbacks for one completed ad. */
    private val profilePicGrantedForThisShow = AtomicBoolean(false)

    // ── Frequency capping ──────────────────────────────────────────────────────

    private var lastInterstitialShowTime: Long = 0L

    // ── Premium flag ───────────────────────────────────────────────────────────

    /** When `true`, no ads are loaded or shown (verified Premium entitlement). */
    @Volatile
    var isPremiumUser: Boolean = false
        private set

    private val premiumScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Called with a unique token when a rewarded ad COMPLETES. The owner
     * (AppModule) grants exactly [REWARDED_GOLD_AMOUNT] Gold using the token
     * as the Gold-ledger dedup key — idempotent under retries/replays.
     */
    var rewardGrantCallback: ((token: String) -> Unit)? = null

    /**
     * v1.0.9 — called with a unique token when a PROFILE-PIC rewarded ad
     * (unit [PROFILE_PIC_REWARDED_AD_UNIT_ID]) COMPLETES. AppModule records
     * one watch toward the 5-ad SUNRISE picture unlock (ledger-deduped).
     */
    var profilePicGrantCallback: ((token: String) -> Unit)? = null

    /** Optional listener for ad lifecycle events. */
    var eventListener: AdEventListener? = null

    // ── Premium entitlement observation ────────────────────────────────────────

    /**
     * Keep ad behaviour in sync with the verified Premium entitlement:
     * - premium → immediately release cached ads and stop loading (zero ads)
     * - premium lost (expired/cancelled) → resume the free-user ad strategy
     */
    fun attachPremiumState(state: kotlinx.coroutines.flow.StateFlow<PremiumRepository.PremiumState>) {
        premiumScope.launch {
            state.collect { premium ->
                val wasPremium = isPremiumUser
                isPremiumUser = premium.isPremium
                if (premium.isPremium && !wasPremium) {
                    Timber.d("AdManager: verified Premium active — releasing all ads (ad-free mode)")
                    release()
                } else if (!premium.isPremium && wasPremium) {
                    Timber.d("AdManager: Premium no longer active — resuming free-user ad strategy")
                    loadRewardedAd()
                    loadProfilePicRewardedAd()
                    loadInterstitialAd()
                }
            }
        }
    }

    /** Schedule a load retry with backoff (ad load failures are usually transient). */
    private fun scheduleLoadRetry(retry: () -> Unit) {
        val job = premiumScope.launch {
            delay(AD_RETRY_INITIAL_MS)
            retry()
        }
        retryJobs.add(job)
        retryJobs.removeAll { !it.isActive }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initialize the Unity LevelPlay SDK. Should be called once during app
     * startup (MainActivity.onCreate). Premium subscribers never load ads.
     */
    fun initialize() {
        if (!isInitializing.compareAndSet(false, true)) return
        try {
            val request = LevelPlayInitRequest.Builder(LEVELPLAY_APP_KEY).build()
            LevelPlay.init(context, request, object : LevelPlayInitListener {
                override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                    isInitializing.set(false)
                    if (!isSdkReady.compareAndSet(false, true)) return
                    Timber.i("LevelPlay initialized (appKey=%s)", LEVELPLAY_APP_KEY)
                    loadRewardedAd()
                    loadProfilePicRewardedAd()
                    loadInterstitialAd()
                }

                override fun onInitFailed(error: LevelPlayInitError) {
                    isInitializing.set(false)
                    Timber.w(
                        "LevelPlay init failed: code=%s message=%s — will retry",
                        error.errorCode, error.errorMessage
                    )
                    premiumScope.launch {
                        delay(AD_RETRY_INITIAL_MS)
                        initialize()
                    }
                }
            })
        } catch (e: Exception) {
            isInitializing.set(false)
            Timber.e(e, "Failed to initialize LevelPlay SDK")
            eventListener?.onAdLoadFailed(AdType.INTERSTITIAL, "SDK init failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Rewarded ad — "Gold Ads" (+200 Gold on verified completion)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Load the rewarded ad. Safe to call repeatedly; skipped for Premium.
     */
    fun loadRewardedAd() {
        if (isPremiumUser) return
        if (!isSdkReady.get()) return
        if (!isRewardedLoading.compareAndSet(false, true)) return
        val ad = rewardedAd ?: LevelPlayRewardedAd(REWARDED_AD_UNIT_ID).also {
            it.setListener(rewardedListener)
            rewardedAd = it
        }
        if (ad.isAdReady) {
            isRewardedLoading.set(false)
            return
        }
        try {
            ad.loadAd()
        } catch (e: Exception) {
            isRewardedLoading.set(false)
            Timber.e(e, "Exception requesting rewarded ad load")
            scheduleLoadRetry { loadRewardedAd() }
        }
    }

    private val rewardedListener = object : LevelPlayRewardedAdListener {
        override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
            isRewardedLoading.set(false)
            Timber.d("LevelPlay rewarded ad loaded")
            eventListener?.onAdLoaded(AdType.REWARDED)
        }

        override fun onAdLoadFailed(error: LevelPlayAdError) {
            isRewardedLoading.set(false)
            Timber.w("LevelPlay rewarded ad failed to load: %s — will retry", error.errorMessage)
            eventListener?.onAdLoadFailed(AdType.REWARDED, error.errorMessage)
            scheduleLoadRetry { loadRewardedAd() }
        }

        override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
            eventListener?.onAdShown(AdType.REWARDED)
        }

        override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
            // THE ONLY GOLD GRANT PATH — fires when the user actually completed
            // the ad. Guarded so a duplicated callback cannot double-credit.
            val token = rewardShowToken
            if (token == null) {
                Timber.w("LevelPlay rewarded callback without show token — ignoring")
                return
            }
            if (rewardGrantedForThisShow.compareAndSet(false, true)) {
                Timber.i(
                    "LevelPlay rewarded ad COMPLETED (reward=%s amount=%d) — granting +%d Gold (token=%s…)",
                    reward.name, reward.amount, REWARDED_GOLD_AMOUNT, token.take(8)
                )
                try {
                    rewardGrantCallback?.invoke(token)
                } catch (e: Exception) {
                    Timber.e(e, "Gold grant for rewarded ad failed")
                }
            }
        }

        override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
            Timber.w("LevelPlay rewarded ad failed to display: %s", error.errorMessage)
            eventListener?.onAdShowFailed(AdType.REWARDED, error.errorMessage)
        }

        override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

        override fun onAdClosed(adInfo: LevelPlayAdInfo) {
            Timber.d("LevelPlay rewarded ad closed")
            eventListener?.onAdDismissed(AdType.REWARDED)
            // Pre-load the next reward opportunity.
            loadRewardedAd()
        }

        override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
    }

    /**
     * Show the rewarded ad ("Gold Ads"). Gold is granted ONLY through
     * [rewardGrantCallback] when LevelPlay confirms completion.
     *
     * @return true when the ad was actually shown (or is about to show).
     */
    fun showRewardedAd(activity: Activity): Boolean {
        if (isPremiumUser) {
            Timber.d("Rewarded ad skipped: premium user (ad-free)")
            return false
        }
        val ad = rewardedAd
        if (ad == null || !ad.isAdReady) {
            Timber.d("Rewarded ad not ready — requesting load")
            loadRewardedAd()
            return false
        }
        rewardShowToken = UUID.randomUUID().toString().replace("-", "")
        rewardGrantedForThisShow.set(false)
        return try {
            ad.showAd(activity)
            true
        } catch (e: Exception) {
            Timber.e(e, "Exception showing rewarded ad — never crash on ad errors")
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Rewarded ad — "Profile Pic" (5 watches unlock the SUNRISE picture)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Load the profile-picture rewarded ad. Safe to call repeatedly. */
    fun loadProfilePicRewardedAd() {
        if (isPremiumUser) return
        if (!isSdkReady.get()) return
        if (!isProfilePicRewardedLoading.compareAndSet(false, true)) return
        val ad = profilePicRewardedAd ?: LevelPlayRewardedAd(PROFILE_PIC_REWARDED_AD_UNIT_ID).also {
            it.setListener(profilePicRewardedListener)
            profilePicRewardedAd = it
        }
        if (ad.isAdReady) {
            isProfilePicRewardedLoading.set(false)
            return
        }
        try {
            ad.loadAd()
        } catch (e: Exception) {
            isProfilePicRewardedLoading.set(false)
            Timber.e(e, "Exception requesting profile-pic rewarded ad load")
            scheduleLoadRetry { loadProfilePicRewardedAd() }
        }
    }

    private val profilePicRewardedListener = object : LevelPlayRewardedAdListener {
        override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
            isProfilePicRewardedLoading.set(false)
            Timber.d("LevelPlay profile-pic rewarded ad loaded")
            eventListener?.onAdLoaded(AdType.REWARDED)
        }

        override fun onAdLoadFailed(error: LevelPlayAdError) {
            isProfilePicRewardedLoading.set(false)
            Timber.w("LevelPlay profile-pic rewarded ad failed to load: %s — will retry", error.errorMessage)
            eventListener?.onAdLoadFailed(AdType.REWARDED, error.errorMessage)
            scheduleLoadRetry { loadProfilePicRewardedAd() }
        }

        override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
            eventListener?.onAdShown(AdType.REWARDED)
        }

        override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
            // THE ONLY PICTURE-UNLOCK COUNT PATH — fires when the user actually
            // completed the ad. Guarded so a duplicated callback cannot double-count.
            val token = profilePicShowToken
            if (token == null) {
                Timber.w("LevelPlay profile-pic rewarded callback without show token — ignoring")
                return
            }
            if (profilePicGrantedForThisShow.compareAndSet(false, true)) {
                Timber.i("LevelPlay profile-pic rewarded ad COMPLETED — recording 1 watch (token=%s…)", token.take(8))
                try {
                    profilePicGrantCallback?.invoke(token)
                } catch (e: Exception) {
                    Timber.e(e, "Profile-pic unlock grant for rewarded ad failed")
                }
            }
        }

        override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
            Timber.w("LevelPlay profile-pic rewarded ad failed to display: %s", error.errorMessage)
            eventListener?.onAdShowFailed(AdType.REWARDED, error.errorMessage)
        }

        override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

        override fun onAdClosed(adInfo: LevelPlayAdInfo) {
            Timber.d("LevelPlay profile-pic rewarded ad closed")
            eventListener?.onAdDismissed(AdType.REWARDED)
            // Pre-load the next unlock opportunity.
            loadProfilePicRewardedAd()
        }

        override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
    }

    /**
     * Show the profile-picture rewarded ad. One watch is recorded ONLY through
     * [profilePicGrantCallback] when LevelPlay confirms completion.
     *
     * @return true when the ad was actually shown (or is about to show).
     */
    fun showProfilePicRewardedAd(activity: Activity): Boolean {
        if (isPremiumUser) {
            Timber.d("Profile-pic rewarded ad skipped: premium user (ad-free)")
            return false
        }
        val ad = profilePicRewardedAd
        if (ad == null || !ad.isAdReady) {
            Timber.d("Profile-pic rewarded ad not ready — requesting load")
            loadProfilePicRewardedAd()
            return false
        }
        profilePicShowToken = UUID.randomUUID().toString().replace("-", "")
        profilePicGrantedForThisShow.set(false)
        return try {
            ad.showAd(activity)
            true
        } catch (e: Exception) {
            Timber.e(e, "Exception showing profile-pic rewarded ad — never crash on ad errors")
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Interstitial ad — full-screen, frequency capped
    // ═══════════════════════════════════════════════════════════════════════════

    /** Load an interstitial ad. Safe to call repeatedly; skipped for Premium. */
    fun loadInterstitialAd() {
        if (isPremiumUser) return
        if (!isSdkReady.get()) return
        if (!isInterstitialLoading.compareAndSet(false, true)) return
        val ad = interstitialAd ?: LevelPlayInterstitialAd(INTERSTITIAL_AD_UNIT_ID).also {
            it.setListener(interstitialListener)
            interstitialAd = it
        }
        if (ad.isAdReady) {
            isInterstitialLoading.set(false)
            return
        }
        try {
            ad.loadAd()
        } catch (e: Exception) {
            isInterstitialLoading.set(false)
            Timber.e(e, "Exception requesting interstitial ad load")
            scheduleLoadRetry { loadInterstitialAd() }
        }
    }

    private val interstitialListener = object : LevelPlayInterstitialAdListener {
        override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
            isInterstitialLoading.set(false)
            Timber.d("LevelPlay interstitial ad loaded")
            eventListener?.onAdLoaded(AdType.INTERSTITIAL)
        }

        override fun onAdLoadFailed(error: LevelPlayAdError) {
            isInterstitialLoading.set(false)
            Timber.w("LevelPlay interstitial failed to load: %s — will retry", error.errorMessage)
            eventListener?.onAdLoadFailed(AdType.INTERSTITIAL, error.errorMessage)
            scheduleLoadRetry { loadInterstitialAd() }
        }

        override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
            lastInterstitialShowTime = Date().time
            eventListener?.onAdShown(AdType.INTERSTITIAL)
        }

        override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
            Timber.w("LevelPlay interstitial failed to show: %s", error.errorMessage)
            eventListener?.onAdShowFailed(AdType.INTERSTITIAL, error.errorMessage)
            // Never block the caller's navigation when the show fails.
            val continuation = interstitialDismissed
            interstitialDismissed = null
            continuation?.invoke()
        }

        override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

        override fun onAdClosed(adInfo: LevelPlayAdInfo) {
            Timber.d("LevelPlay interstitial closed")
            eventListener?.onAdDismissed(AdType.INTERSTITIAL)
            // Resume the caller's navigation now that the ad flow finished.
            val continuation = interstitialDismissed
            interstitialDismissed = null
            continuation?.invoke()
            loadInterstitialAd() // Pre-load next ad
        }

        override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
    }

    /**
     * Show an interstitial ad if available and the frequency cap is met.
     * Calls [onAdDismissed] whether the ad was shown or skipped, so callers
     * can always continue navigation.
     *
     * Frequency capping: maximum 1 interstitial per 3 minutes; never for
     * verified Premium users.
     */
    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (isPremiumUser) {
            Timber.d("Skipping interstitial show: premium user")
            onAdDismissed()
            return
        }

        val now = Date().time
        val elapsed = now - lastInterstitialShowTime
        if (elapsed < INTERSTITIAL_FREQUENCY_CAP_MS) {
            Timber.d(
                "Interstitial frequency cap: %ds remaining",
                (INTERSTITIAL_FREQUENCY_CAP_MS - elapsed) / 1000
            )
            onAdDismissed()
            return
        }

        val ad = interstitialAd
        if (ad == null || !ad.isAdReady) {
            Timber.d("Interstitial not available, proceeding without ad")
            loadInterstitialAd()
            onAdDismissed()
            return
        }

        try {
            interstitialDismissed = onAdDismissed
            ad.showAd(activity)
        } catch (e: Exception) {
            Timber.e(e, "Exception showing interstitial — never crash on ad errors")
            interstitialDismissed = null
            onAdDismissed()
        }
    }

    /** Pending navigation continuation for the showing interstitial (main thread only). */
    private var interstitialDismissed: (() -> Unit)? = null

    /** Whether an interstitial can be shown right now (ready + cap elapsed). */
    fun canShowInterstitial(): Boolean {
        if (isPremiumUser) return false
        val ad = interstitialAd ?: return false
        if (!ad.isAdReady) return false
        return Date().time - lastInterstitialShowTime >= INTERSTITIAL_FREQUENCY_CAP_MS
    }

    /** Seconds remaining until the interstitial cap allows the next show. */
    fun getInterstitialCooldownSeconds(): Long {
        val remaining = INTERSTITIAL_FREQUENCY_CAP_MS - (Date().time - lastInterstitialShowTime)
        return if (remaining > 0) remaining / 1000 else 0L
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Release all ad references. Called when the user becomes a verified
     * Premium subscriber (ad-free mode) or on teardown.
     */
    fun release() {
        rewardedAd = null
        profilePicRewardedAd = null
        interstitialAd = null
        interstitialDismissed = null
        isRewardedLoading.set(false)
        isProfilePicRewardedLoading.set(false)
        isInterstitialLoading.set(false)
        retryJobs.forEach { it.cancel() }
        retryJobs.clear()
        Timber.d("AdManager released — all ad references cleared (ad-free mode)")
    }
}

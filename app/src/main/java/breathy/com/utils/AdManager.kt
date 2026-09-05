package breathy.com.utils

import android.app.Activity
import android.content.Context
import breathy.com.data.repository.PremiumRepository
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages advertising for Breathy. Two production stacks, each with a strict
 * purpose (v1.0.11 rev 4):
 *
 * ── Unity Ads (standalone SDK) ────────────────────────────────────────────
 * Production identifiers (source of truth — never replace with test or
 * invented IDs):
 * - **Game ID**:   `800367613`
 * - **Rewarded**:  placement `Rewarded_Android`  → +200 Gold on completion
 * - **Interstitial**: placement `Interstitial_Android`
 *
 * ── Unity LevelPlay (mediation) ───────────────────────────────────────────
 * Kept ONLY for the placements that Unity Ads cannot serve:
 * - **App Key**:      `27e9c42cd`
 * - **Profile Pic**:  rewarded unit `sdogk85zaxbkjym5` (5 watches → SUNRISE)
 * - **Native**:       unit `5o8vznxxsem6mv51` (rendered sponsored card)
 *
 * ── Premium eligibility is checked PER AD FORMAT (never a global gate) ────
 * | Format       | Free     | Premium  |
 * |--------------|----------|----------|
 * | Native       | shown    | BLOCKED  |
 * | Interstitial | shown    | BLOCKED  |
 * | Rewarded     | allowed  | ALLOWED  |
 *
 * Rewarded Ads are an OPTIONAL REWARD MECHANIC, not a forced placement:
 * Premium subscribers may still voluntarily watch a rewarded ad and receive
 * the configured reward (+200 Gold). There is deliberately NO global
 * `if (isPremium) return` in this manager — only the per-format checks
 * above. Becoming Premium releases ONLY the interstitial; rewarded ads stay
 * loaded and usable.
 *
 * ── Gold reward integrity ────────────────────────────────────────────────
 * The +200 Gold grant fires ONLY from [IUnityAdsShowListener.onUnityAdsShowComplete]
 * when the completion state is [UnityAds.UnityAdsShowCompletionState.COMPLETED].
 * No reward if the ad fails, is closed early (SKIPPED), does not complete, or
 * the callback is invalid. Duplicate callbacks and duplicate Gold
 * transactions are prevented twice: per-show [AtomicBoolean] guard here, and
 * a unique show token used as the Gold-ledger dedup key in [DI AppModule].
 *
 * ── Interstitial frequency cap ───────────────────────────────────────────
 * Max 1 per [INTERSTITIAL_FREQUENCY_CAP_MS]; never shown during
 * purchase/subscription/event-registration flows (call sites are fixed).
 *
 * Thread safety: Unity Ads and LevelPlay public APIs are main-thread APIs.
 * Mutable show state is confined to the main thread; loading flags use
 * [AtomicBoolean].
 */
class AdManager(
    private val context: Context
) {

    companion object {
        // ── Unity Ads production identifiers (standalone SDK) ─────────────
        /** Unity Ads Game ID ("app ID") — Unity Dashboard → Monetization. */
        const val UNITY_GAME_ID = "800367613"

        /** Rewarded placement — +200 Gold ONLY on verified COMPLETED shows. */
        const val UNITY_REWARDED_PLACEMENT = "Rewarded_Android"

        /** Full-screen interstitial placement — frequency capped. */
        const val UNITY_INTERSTITIAL_PLACEMENT = "Interstitial_Android"

        // ── Unity LevelPlay production identifiers (native + profile pic) ─
        /** LevelPlay App Key (Unity LevelPlay platform → production). */
        const val LEVELPLAY_APP_KEY = "27e9c42cd"

        /** v1.0.9 rewarded ad unit — unlocks the SUNRISE profile picture after
         *  5 verified completed watches (placement "Profile Pic"). */
        const val PROFILE_PIC_REWARDED_AD_UNIT_ID = "sdogk85zaxbkjym5"

        /** Native ad unit — rendered as a Breathy-styled sponsored card. */
        const val NATIVE_AD_UNIT_ID = "5o8vznxxsem6mv51"

        /** Gold granted for a completed "Rewarded_Android" placement. */
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

    /** Types of ads supported by Breathy. */
    enum class AdType {
        REWARDED,
        INTERSTITIAL,
        NATIVE
    }

    // ── Ad references ──────────────────────────────────────────────────────

    private var profilePicRewardedAd: LevelPlayRewardedAd? = null

    // ── Loading state ──────────────────────────────────────────────────────

    private val isProfilePicRewardedLoading = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val isUnityInitializing = AtomicBoolean(false)
    private val isUnityRewardedLoading = AtomicBoolean(false)
    private val isUnityInterstitialLoading = AtomicBoolean(false)

    /** True once [LevelPlay.init] succeeded — LevelPlay loads start here. */
    private val isSdkReady = AtomicBoolean(false)

    /** True once [UnityAds.initialize] succeeded — Unity loads start here. */
    private val isUnitySdkReady = AtomicBoolean(false)

    /** True while a loaded Unity rewarded/interstitial ad is ready to show. */
    @Volatile
    private var unityRewardedReady = false

    @Volatile
    private var unityInterstitialReady = false

    private var retryJobs: MutableList<Job> = mutableListOf()

    /** Unique token for the currently shown rewarded ad (gold dedup key). */
    private var rewardShowToken: String? = null

    /** Unique token for the currently shown profile-picture rewarded ad. */
    private var profilePicShowToken: String? = null

    /** Guards against duplicate grant callbacks for one completed ad. */
    private val rewardGrantedForThisShow = AtomicBoolean(false)

    /** Guards against duplicate picture-unlock callbacks for one completed ad. */
    private val profilePicGrantedForThisShow = AtomicBoolean(false)

    // ── Frequency capping ──────────────────────────────────────────────────

    private var lastInterstitialShowTime: Long = 0L

    // ── Premium flag ───────────────────────────────────────────────────────

    /**
     * Verified Premium entitlement. NOT a global ad gate: per format,
     * native/interstitial check it to BLOCK, rewarded ads stay ALLOWED.
     */
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

    // ── Premium entitlement observation ────────────────────────────────────

    /**
     * Keep ad behaviour in sync with the verified Premium entitlement —
     * PER FORMAT (never a global gate):
     * - premium  → release ONLY the interstitial. Rewarded ads stay loaded
     *              and usable (voluntary reward mechanic, +200 Gold).
     * - lost     → resume loading the interstitial for the free experience.
     */
    fun attachPremiumState(state: kotlinx.coroutines.flow.StateFlow<PremiumRepository.PremiumState>) {
        premiumScope.launch {
            state.collect { premium ->
                val wasPremium = isPremiumUser
                isPremiumUser = premium.isPremium
                if (premium.isPremium && !wasPremium) {
                    Timber.d("AdManager: verified Premium active — blocking native/interstitial, rewarded stays available")
                    releaseInterstitialAds()
                } else if (!premium.isPremium && wasPremium) {
                    Timber.d("AdManager: Premium no longer active — resuming free-user interstitial strategy")
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

    // ═══════════════════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Initialize both ad stacks once during app startup (MainActivity.onCreate):
     * - Unity Ads (Game ID [UNITY_GAME_ID]) → interstitial + gold rewarded.
     * - LevelPlay (App Key [LEVELPLAY_APP_KEY]) → native + profile-pic rewarded.
     *
     * Rewarded ads must initialize for EVERYONE (Premium included) — rewarded
     * is the voluntary reward mechanic and stays available to subscribers.
     */
    fun initialize() {
        initUnityAds()
        initLevelPlay()
    }

    private fun initUnityAds() {
        if (!isUnityInitializing.compareAndSet(false, true)) return
        try {
            UnityAds.initialize(context, UNITY_GAME_ID, object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    isUnityInitializing.set(false)
                    if (!isUnitySdkReady.compareAndSet(false, true)) return
                    Timber.i("Unity Ads initialized (gameId=%s)", UNITY_GAME_ID)
                    loadRewardedAd()
                    loadInterstitialAd()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    isUnityInitializing.set(false)
                    Timber.w("Unity Ads init failed: %s — %s — will retry", error, message)
                    premiumScope.launch {
                        delay(AD_RETRY_INITIAL_MS)
                        initUnityAds()
                    }
                }
            })
        } catch (e: Exception) {
            isUnityInitializing.set(false)
            Timber.e(e, "Failed to initialize Unity Ads SDK")
            eventListener?.onAdLoadFailed(AdType.INTERSTITIAL, "SDK init failed: ${e.message}")
        }
    }

    private fun initLevelPlay() {
        if (!isInitializing.compareAndSet(false, true)) return
        try {
            val request = LevelPlayInitRequest.Builder(LEVELPLAY_APP_KEY).build()
            LevelPlay.init(context, request, object : LevelPlayInitListener {
                override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                    isInitializing.set(false)
                    if (!isSdkReady.compareAndSet(false, true)) return
                    Timber.i("LevelPlay initialized (appKey=%s)", LEVELPLAY_APP_KEY)
                    loadProfilePicRewardedAd()
                }

                override fun onInitFailed(error: LevelPlayInitError) {
                    isInitializing.set(false)
                    Timber.w(
                        "LevelPlay init failed: code=%s message=%s — will retry",
                        error.errorCode, error.errorMessage
                    )
                    premiumScope.launch {
                        delay(AD_RETRY_INITIAL_MS)
                        initLevelPlay()
                    }
                }
            })
        } catch (e: Exception) {
            isInitializing.set(false)
            Timber.e(e, "Failed to initialize LevelPlay SDK")
            eventListener?.onAdLoadFailed(AdType.REWARDED, "SDK init failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Rewarded ad — Unity Ads "Rewarded_Android" (+200 Gold on completion)
    //  Premium users: ALLOWED — rewarded is a voluntary reward mechanic.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Load the rewarded ad. Safe to call repeatedly. NO premium gate here —
     * Premium users keep full access to rewarded rewards.
     */
    fun loadRewardedAd() {
        if (!isUnitySdkReady.get()) return
        if (unityRewardedReady) return
        if (!isUnityRewardedLoading.compareAndSet(false, true)) return
        try {
            UnityAds.load(UNITY_REWARDED_PLACEMENT, unityRewardedLoadListener)
        } catch (e: Exception) {
            isUnityRewardedLoading.set(false)
            Timber.e(e, "Exception requesting rewarded ad load")
            scheduleLoadRetry { loadRewardedAd() }
        }
    }

    private val unityRewardedLoadListener = object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String) {
            isUnityRewardedLoading.set(false)
            unityRewardedReady = true
            Timber.d("Unity Ads rewarded ad loaded (placement=%s)", placementId)
            eventListener?.onAdLoaded(AdType.REWARDED)
        }

        override fun onUnityAdsFailedToLoad(
            placementId: String,
            error: UnityAds.UnityAdsLoadError,
            message: String
        ) {
            isUnityRewardedLoading.set(false)
            unityRewardedReady = false
            Timber.w("Unity Ads rewarded failed to load: %s — will retry", message)
            eventListener?.onAdLoadFailed(AdType.REWARDED, message)
            scheduleLoadRetry { loadRewardedAd() }
        }
    }

    private val unityRewardedShowListener = object : IUnityAdsShowListener {
        override fun onUnityAdsShowStart(placementId: String) {
            eventListener?.onAdShown(AdType.REWARDED)
        }

        override fun onUnityAdsShowClick(placementId: String) {}

        override fun onUnityAdsShowComplete(
            placementId: String,
            state: UnityAds.UnityAdsShowCompletionState
        ) {
            eventListener?.onAdDismissed(AdType.REWARDED)
            // THE ONLY GOLD GRANT PATH — fires ONLY when the user actually
            // finished the ad (COMPLETED). SKIPPED/failed shows grant nothing.
            // Guarded so a duplicated callback can never double-credit.
            val token = rewardShowToken
            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED && token != null) {
                if (rewardGrantedForThisShow.compareAndSet(false, true)) {
                    Timber.i(
                        "Unity Ads rewarded COMPLETED (placement=%s) — granting +%d Gold (token=%s…)",
                        placementId, REWARDED_GOLD_AMOUNT, token.take(8)
                    )
                    try {
                        rewardGrantCallback?.invoke(token)
                    } catch (e: Exception) {
                        Timber.e(e, "Gold grant for rewarded ad failed")
                    }
                }
            } else if (token == null) {
                Timber.w("Unity Ads rewarded completion without show token — ignoring")
            } else {
                Timber.d("Unity Ads rewarded not completed (%s) — no reward", state)
            }
            // Pre-load the next reward opportunity.
            loadRewardedAd()
        }

        override fun onUnityAdsShowFailure(
            placementId: String,
            error: UnityAds.UnityAdsShowError,
            message: String
        ) {
            Timber.w("Unity Ads rewarded failed to display: %s", message)
            eventListener?.onAdShowFailed(AdType.REWARDED, message)
            loadRewardedAd()
        }
    }

    /**
     * Show the rewarded ad ("Rewarded_Android"). Gold is granted ONLY through
     * [rewardGrantCallback] when Unity Ads confirms COMPLETED completion.
     * Available to EVERYONE — Premium users included (voluntary reward).
     *
     * @return true when the ad was actually shown (or is about to show).
     */
    fun showRewardedAd(activity: Activity): Boolean {
        if (!unityRewardedReady) {
            Timber.d("Rewarded ad not ready — requesting load")
            loadRewardedAd()
            return false
        }
        rewardShowToken = UUID.randomUUID().toString().replace("-", "")
        rewardGrantedForThisShow.set(false)
        unityRewardedReady = false // this load is consumed by the show
        return try {
            UnityAds.show(
                activity,
                UNITY_REWARDED_PLACEMENT,
                UnityAdsShowOptions(),
                unityRewardedShowListener
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Exception showing rewarded ad — never crash on ad errors")
            loadRewardedAd()
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Rewarded ad — LevelPlay "Profile Pic" (5 watches → SUNRISE picture)
    //  Premium users: ALLOWED — rewarded format, voluntary mechanic.
    // ═══════════════════════════════════════════════════════════════════════

    /** Load the profile-picture rewarded ad. Safe to call repeatedly. */
    fun loadProfilePicRewardedAd() {
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
     * Available to EVERYONE — Premium users included.
     *
     * @return true when the ad was actually shown (or is about to show).
     */
    fun showProfilePicRewardedAd(activity: Activity): Boolean {
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

    // ═══════════════════════════════════════════════════════════════════════
    //  Interstitial ad — Unity Ads "Interstitial_Android", frequency capped
    //  Premium users: BLOCKED (per-format eligibility).
    // ═══════════════════════════════════════════════════════════════════════

    /** Load an interstitial ad. Safe to call repeatedly; skipped for Premium. */
    fun loadInterstitialAd() {
        if (isPremiumUser) return // Premium → interstitial BLOCKED
        if (!isUnitySdkReady.get()) return
        if (unityInterstitialReady) return
        if (!isUnityInterstitialLoading.compareAndSet(false, true)) return
        try {
            UnityAds.load(UNITY_INTERSTITIAL_PLACEMENT, unityInterstitialLoadListener)
        } catch (e: Exception) {
            isUnityInterstitialLoading.set(false)
            Timber.e(e, "Exception requesting interstitial ad load")
            scheduleLoadRetry { loadInterstitialAd() }
        }
    }

    private val unityInterstitialLoadListener = object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String) {
            isUnityInterstitialLoading.set(false)
            unityInterstitialReady = true
            Timber.d("Unity Ads interstitial ad loaded (placement=%s)", placementId)
            eventListener?.onAdLoaded(AdType.INTERSTITIAL)
        }

        override fun onUnityAdsFailedToLoad(
            placementId: String,
            error: UnityAds.UnityAdsLoadError,
            message: String
        ) {
            isUnityInterstitialLoading.set(false)
            unityInterstitialReady = false
            Timber.w("Unity Ads interstitial failed to load: %s — will retry", message)
            eventListener?.onAdLoadFailed(AdType.INTERSTITIAL, message)
            scheduleLoadRetry { loadInterstitialAd() }
        }
    }

    private val unityInterstitialShowListener = object : IUnityAdsShowListener {
        override fun onUnityAdsShowStart(placementId: String) {
            lastInterstitialShowTime = Date().time
            eventListener?.onAdShown(AdType.INTERSTITIAL)
        }

        override fun onUnityAdsShowClick(placementId: String) {}

        override fun onUnityAdsShowComplete(
            placementId: String,
            state: UnityAds.UnityAdsShowCompletionState
        ) {
            Timber.d("Unity Ads interstitial closed (%s)", state)
            eventListener?.onAdDismissed(AdType.INTERSTITIAL)
            // Resume the caller's navigation now that the ad flow finished.
            val continuation = interstitialDismissed
            interstitialDismissed = null
            continuation?.invoke()
            loadInterstitialAd() // Pre-load next ad
        }

        override fun onUnityAdsShowFailure(
            placementId: String,
            error: UnityAds.UnityAdsShowError,
            message: String
        ) {
            Timber.w("Unity Ads interstitial failed to show: %s", message)
            eventListener?.onAdShowFailed(AdType.INTERSTITIAL, message)
            // Never block the caller's navigation when the show fails.
            val continuation = interstitialDismissed
            interstitialDismissed = null
            continuation?.invoke()
            loadInterstitialAd()
        }
    }

    /**
     * Show an interstitial ad if available and the frequency cap is met.
     * Calls [onAdDismissed] whether the ad was shown or skipped, so callers
     * can always continue navigation.
     *
     * Frequency capping: max 1 interstitial per
     * [INTERSTITIAL_FREQUENCY_CAP_MS]; never for verified Premium users
     * (per-format eligibility: Premium → interstitial BLOCKED).
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

        if (!unityInterstitialReady) {
            Timber.d("Interstitial not available, proceeding without ad")
            loadInterstitialAd()
            onAdDismissed()
            return
        }

        unityInterstitialReady = false // this load is consumed by the show
        try {
            interstitialDismissed = onAdDismissed
            UnityAds.show(
                activity,
                UNITY_INTERSTITIAL_PLACEMENT,
                UnityAdsShowOptions(),
                unityInterstitialShowListener
            )
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
        if (!unityInterstitialReady) return false
        return Date().time - lastInterstitialShowTime >= INTERSTITIAL_FREQUENCY_CAP_MS
    }

    /** Seconds remaining until the interstitial cap allows the next show. */
    fun getInterstitialCooldownSeconds(): Long {
        val remaining = INTERSTITIAL_FREQUENCY_CAP_MS - (Date().time - lastInterstitialShowTime)
        return if (remaining > 0) remaining / 1000 else 0L
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Release ONLY the interstitial. Called when the user becomes a verified
     * Premium subscriber (per-format eligibility: Premium → interstitial
     * BLOCKED). Rewarded ads are deliberately KEPT — Premium users may still
     * watch them voluntarily for +200 Gold.
     */
    private fun releaseInterstitialAds() {
        unityInterstitialReady = false
        interstitialDismissed = null
        isUnityInterstitialLoading.set(false)
        Timber.d("AdManager: interstitial released (Premium active — rewarded untouched)")
    }

    /**
     * Release all ad references and reset all stacks (full teardown only —
     * NOT used for the Premium transition, which only blocks per-format).
     */
    fun release() {
        profilePicRewardedAd = null
        interstitialDismissed = null
        unityRewardedReady = false
        unityInterstitialReady = false
        isProfilePicRewardedLoading.set(false)
        isUnityRewardedLoading.set(false)
        isUnityInterstitialLoading.set(false)
        retryJobs.forEach { it.cancel() }
        retryJobs.clear()
        Timber.d("AdManager released — all ad references cleared")
    }
}

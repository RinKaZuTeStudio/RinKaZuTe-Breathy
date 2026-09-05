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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
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
 * Kept ONLY for the native ad (v1.0.11 rev 5: rewarded shows run on Unity
 * Ads — LevelPlay is no longer a rewarded show path):
 * - **App Key**:      `27e9c42cd`
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
 * ── No artificial cooldown ───────────────────────────────────────────────
 * There is deliberately NO app-side frequency cap or cooldown. An ad is
 * eligible whenever the ad provider/SDK reports one ready — the SDK is the
 * single pacing authority. The app never artificially delays an ad just
 * because a previous one was shown.
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

        /** Rewarded placement — +200 Gold ONLY on verified COMPLETED shows.
         *  GOLD flow ONLY — the SUNRISE picture flow uses the DEDICATED
         *  picture placement [UNITY_PICTURE_REWARDED_PLACEMENT] (v1.0.11 rev 6). */
        const val UNITY_REWARDED_PLACEMENT = "Rewarded_Android"

        /** DEDICATED picture-rewarded placement (v1.0.11 rev 6) — serves the
         *  SUNRISE picture watch flow ONLY. Kept strictly separate from the
         *  gold placement [UNITY_REWARDED_PLACEMENT] so each flow has its own
         *  production waterfall. Reward still granted ONLY on COMPLETED. */
        const val UNITY_PICTURE_REWARDED_PLACEMENT = "Ad2"

        /** Full-screen interstitial placement. */
        const val UNITY_INTERSTITIAL_PLACEMENT = "Interstitial_Android"

        // ── AdMob production ad-unit identifiers (exact, never test IDs) ─
        // These are the production AdMob units associated with the Breathy
        // mediation waterfall (LevelPlay console network config). The Google
        // Mobile Ads SDK is NOT embedded standalone (no APPLICATION_ID
        // meta-data exists — it was removed at v1.0.3); these unit IDs are
        // consumed server-side by the mediation waterfall and are recorded
        // here verbatim as the production source of truth.
        /** AdMob App Open ad unit (production). */
        const val ADMOB_APP_OPEN_AD_UNIT_ID = "ca-app-pub-9434446627275871/5853663684"

        /** AdMob Gold Rewarded ad unit (production) — pairs with
         *  [UNITY_REWARDED_PLACEMENT]. */
        const val ADMOB_GOLD_REWARDED_AD_UNIT_ID = "ca-app-pub-9434446627275871/8503877756"

        /** AdMob Interstitial ad unit (production) — pairs with
         *  [UNITY_INTERSTITIAL_PLACEMENT]. */
        const val ADMOB_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9434446627275871/1600874709"

        /** AdMob Picture Rewarded ad unit (production) — pairs with the
         *  dedicated SUNRISE picture placement [UNITY_PICTURE_REWARDED_PLACEMENT]. */
        const val ADMOB_PICTURE_REWARDED_AD_UNIT_ID = "ca-app-pub-9434446627275871/8657966595"

        // ── Unity LevelPlay production identifiers (native ads) ───────────
        /** LevelPlay App Key (Unity LevelPlay platform → production). */
        const val LEVELPLAY_APP_KEY = "27e9c42cd"

        /** v1.0.9 LevelPlay rewarded ad unit (production, retained for
         *  reference). v1.0.11 rev 5: the SUNRISE picture flow no longer shows
         *  through LevelPlay — it runs on the REAL Unity Ads rewarded
         *  placement [UNITY_REWARDED_PLACEMENT], which is the proven
         *  production rewarded stack. The unit id is kept untouched because it
         *  is a production identifier. */
        const val PROFILE_PIC_REWARDED_AD_UNIT_ID = "sdogk85zaxbkjym5"

        /** Native ad unit — rendered as a Breathy-styled sponsored card. */
        const val NATIVE_AD_UNIT_ID = "5o8vznxxsem6mv51"

        /** Gold granted for a completed "Rewarded_Android" placement. */
        const val REWARDED_GOLD_AMOUNT = 200

        // ── Timing ─────────────────────────────────────────────────────────
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

    // ── Loading state ──────────────────────────────────────────────────────

    private val isInitializing = AtomicBoolean(false)
    private val isUnityInitializing = AtomicBoolean(false)
    private val isUnityRewardedLoading = AtomicBoolean(false)
    private val isUnityPictureRewardedLoading = AtomicBoolean(false)
    private val isUnityInterstitialLoading = AtomicBoolean(false)

    /** True once [LevelPlay.init] succeeded — LevelPlay loads start here. */
    private val isSdkReady = AtomicBoolean(false)

    /** True once [UnityAds.initialize] succeeded — Unity loads start here. */
    private val isUnitySdkReady = AtomicBoolean(false)

    /** True while a loaded Unity rewarded/interstitial ad is ready to show. */
    @Volatile
    private var unityRewardedReady = false

    /** True while the DEDICATED picture-rewarded ("Ad2") ad is ready to show. */
    @Volatile
    private var unityPictureRewardedReady = false

    @Volatile
    private var unityInterstitialReady = false

    private var retryJobs: MutableList<Job> = mutableListOf()

    /** Unique token for the currently shown rewarded ad (gold dedup key). */
    private var rewardShowToken: String? = null

    /** Guards against duplicate grant callbacks for one completed ad. */
    private val rewardGrantedForThisShow = AtomicBoolean(false)

    /**
     * What the CURRENTLY SHOWING rewarded ad was started for. One production
     * rewarded placement ([UNITY_REWARDED_PLACEMENT]) serves both purposes;
     * the completion callback routes the grant accordingly:
     * - [RewardedPurpose.GOLD]        → +200 Gold via [rewardGrantCallback].
     * - [RewardedPurpose.PROFILE_PIC] → 1 watch toward the SUNRISE picture
     *   unlock via [profilePicGrantCallback].
     * Confined to the main thread (set in [showRewardedAd], read in the show
     * listener).
     */
    private var rewardedShowPurpose: RewardedPurpose = RewardedPurpose.GOLD

    /** Why a rewarded ad was started — routes the completion grant. */
    private enum class RewardedPurpose { GOLD, PROFILE_PIC }

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
     * v1.0.9 — called with a unique token when a rewarded ad started for the
     * SUNRISE picture flow COMPLETES (v1.0.11 rev 5: on the Unity Ads
     * "Rewarded_Android" placement). AppModule records one watch toward the
     * 5-ad unlock (ledger-deduped).
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
     * - LevelPlay (App Key [LEVELPLAY_APP_KEY]) → native ad only (v1.0.11
     *   rev 5: rewarded ads run exclusively on Unity Ads).
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
                    loadPictureRewardedAd()
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
                    // v1.0.11 rev 5: LevelPlay now serves ONLY the native ad.
                    // The SUNRISE rewarded flow runs on Unity Ads rewarded.
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

    /**
     * Load the DEDICATED picture-rewarded ad ("Ad2") for the SUNRISE picture
     * flow (v1.0.11 rev 6). Fully independent from the gold placement: own
     * ready flag, own loading flag, own load listener. Safe to call
     * repeatedly. NO premium gate — rewarded stays available to everyone.
     */
    fun loadPictureRewardedAd() {
        if (!isUnitySdkReady.get()) return
        if (unityPictureRewardedReady) return
        if (!isUnityPictureRewardedLoading.compareAndSet(false, true)) return
        try {
            UnityAds.load(UNITY_PICTURE_REWARDED_PLACEMENT, unityPictureRewardedLoadListener)
        } catch (e: Exception) {
            isUnityPictureRewardedLoading.set(false)
            Timber.e(e, "Exception requesting picture-rewarded ad load")
            scheduleLoadRetry { loadPictureRewardedAd() }
        }
    }

    private val unityPictureRewardedLoadListener = object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String) {
            isUnityPictureRewardedLoading.set(false)
            unityPictureRewardedReady = true
            Timber.d("Unity Ads picture-rewarded ad loaded (placement=%s)", placementId)
        }

        override fun onUnityAdsFailedToLoad(
            placementId: String,
            error: UnityAds.UnityAdsLoadError,
            message: String
        ) {
            isUnityPictureRewardedLoading.set(false)
            unityPictureRewardedReady = false
            Timber.w("Unity Ads picture-rewarded failed to load: %s — will retry", message)
            scheduleLoadRetry { loadPictureRewardedAd() }
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
            // THE ONLY GRANT PATH — fires ONLY when the user actually finished
            // the ad (COMPLETED). SKIPPED/failed shows grant nothing. Guarded
            // so a duplicated callback can never double-credit. The grant is
            // routed by [rewardedShowPurpose]: the GOLD flow credits +200 Gold
            // through [rewardGrantCallback]; the PROFILE_PIC (SUNRISE) flow
            // records one watch through [profilePicGrantCallback].
            val token = rewardShowToken
            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED && token != null) {
                if (rewardGrantedForThisShow.compareAndSet(false, true)) {
                    when (rewardedShowPurpose) {
                        RewardedPurpose.GOLD -> {
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
                        RewardedPurpose.PROFILE_PIC -> {
                            Timber.i(
                                "Unity Ads rewarded COMPLETED (placement=%s) — recording 1 SUNRISE watch (token=%s…)",
                                placementId, token.take(8)
                            )
                            try {
                                profilePicGrantCallback?.invoke(token)
                            } catch (e: Exception) {
                                Timber.e(e, "Profile-pic unlock grant for rewarded ad failed")
                            }
                        }
                    }
                }
            } else if (token == null) {
                Timber.w("Unity Ads rewarded completion without show token — ignoring")
            } else {
                Timber.d("Unity Ads rewarded not completed (%s) — no reward", state)
            }
            // Pre-load the next reward opportunity — for the SAME flow that
            // just finished (each purpose owns its dedicated placement).
            when (rewardedShowPurpose) {
                RewardedPurpose.GOLD -> loadRewardedAd()
                RewardedPurpose.PROFILE_PIC -> loadPictureRewardedAd()
            }
        }

        override fun onUnityAdsShowFailure(
            placementId: String,
            error: UnityAds.UnityAdsShowError,
            message: String
        ) {
            Timber.w("Unity Ads rewarded failed to display: %s", message)
            eventListener?.onAdShowFailed(AdType.REWARDED, message)
            when (rewardedShowPurpose) {
                RewardedPurpose.GOLD -> loadRewardedAd()
                RewardedPurpose.PROFILE_PIC -> loadPictureRewardedAd()
            }
        }
    }

    /**
     * Show the REAL rewarded ad ("Rewarded_Android") for +200 Gold. The grant
     * is decided ONLY inside [unityRewardedShowListener] when Unity Ads
     * reports COMPLETED (routed through [rewardGrantCallback]).
     * Available to EVERYONE — Premium users included (voluntary reward).
     *
     * @return true when the ad was actually shown (or is about to show).
     */
    fun showRewardedAd(activity: Activity): Boolean =
        startRewardedShow(activity, RewardedPurpose.GOLD)

    /** Internal show entry — keeps the private [RewardedPurpose] type hidden.
     *  v1.0.11 rev 6: each purpose shows its OWN dedicated placement —
     *  GOLD → [UNITY_REWARDED_PLACEMENT] (`Rewarded_Android`),
     *  PROFILE_PIC → [UNITY_PICTURE_REWARDED_PLACEMENT] (`Ad2`). */
    private fun startRewardedShow(activity: Activity, purpose: RewardedPurpose): Boolean {
        val placement = when (purpose) {
            RewardedPurpose.GOLD -> UNITY_REWARDED_PLACEMENT
            RewardedPurpose.PROFILE_PIC -> UNITY_PICTURE_REWARDED_PLACEMENT
        }
        val ready = when (purpose) {
            RewardedPurpose.GOLD -> unityRewardedReady
            RewardedPurpose.PROFILE_PIC -> unityPictureRewardedReady
        }
        if (!ready) {
            Timber.d("Rewarded ad not ready (placement=%s) — requesting load", placement)
            when (purpose) {
                RewardedPurpose.GOLD -> loadRewardedAd()
                RewardedPurpose.PROFILE_PIC -> loadPictureRewardedAd()
            }
            return false
        }
        rewardShowToken = UUID.randomUUID().toString().replace("-", "")
        rewardGrantedForThisShow.set(false)
        rewardedShowPurpose = purpose
        // This load is consumed by the show (flag of the placement we show).
        when (purpose) {
            RewardedPurpose.GOLD -> unityRewardedReady = false
            RewardedPurpose.PROFILE_PIC -> unityPictureRewardedReady = false
        }
        return try {
            UnityAds.show(
                activity,
                placement,
                UnityAdsShowOptions(),
                unityRewardedShowListener
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Exception showing rewarded ad — never crash on ad errors")
            when (purpose) {
                RewardedPurpose.GOLD -> loadRewardedAd()
                RewardedPurpose.PROFILE_PIC -> loadPictureRewardedAd()
            }
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Rewarded ad — SUNRISE picture flow (5 completed watches → unlock)
    //  v1.0.11 rev 6: runs on the DEDICATED picture placement "Ad2" — strictly
    //  separate from the gold placement ("Rewarded_Android"). The ad opens and
    //  plays for real; a watch is recorded ONLY when Unity Ads reports
    //  COMPLETED, and duplicate callbacks are guarded. LevelPlay does not
    //  show rewarded ads (it keeps serving the native ad).
    //  Premium users: ALLOWED — rewarded format, voluntary mechanic.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Show the REAL rewarded ad for the SUNRISE picture flow — the DEDICATED
     * "Ad2" picture placement (v1.0.11 rev 6; NOT the gold placement). One
     * watch is recorded ONLY through [profilePicGrantCallback] when Unity Ads
     * confirms COMPLETED completion — never on early close, failure or invalid
     * callbacks, and never without the ad actually playing.
     * Available to EVERYONE — Premium users included.
     *
     * @return true when the ad was actually shown (or is about to show).
     */
    fun showProfilePicRewardedAd(activity: Activity): Boolean =
        startRewardedShow(activity, RewardedPurpose.PROFILE_PIC)

    // ═══════════════════════════════════════════════════════════════════════
    //  Interstitial ad — Unity Ads "Interstitial_Android" — NO cooldown
    //  (the SDK is the only pacing authority) · Premium: BLOCKED per-format.
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
     * Show an interstitial ad whenever the SDK has one ready.
     * Calls [onAdDismissed] whether the ad was shown or skipped, so callers
     * can always continue navigation.
     *
     * NO app-side cooldown: eligibility is decided ENTIRELY by the ad SDK.
     * The app never artificially delays an ad because a previous one was
     * shown. Only verified Premium users are excluded (per-format
     * eligibility: Premium → interstitial BLOCKED).
     */
    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (isPremiumUser) {
            Timber.d("Skipping interstitial show: premium user")
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

    /** Whether an interstitial can be shown right now (ready + not Premium).
     *  There is NO cooldown — the SDK decides availability. */
    fun canShowInterstitial(): Boolean {
        if (isPremiumUser) return false
        return unityInterstitialReady
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
        interstitialDismissed = null
        unityRewardedReady = false
        unityPictureRewardedReady = false
        unityInterstitialReady = false
        isUnityRewardedLoading.set(false)
        isUnityPictureRewardedLoading.set(false)
        isUnityInterstitialLoading.set(false)
        retryJobs.forEach { it.cancel() }
        retryJobs.clear()
        Timber.d("AdManager released — all ad references cleared")
    }
}

package breathy.com.utils

import android.app.Activity
import android.content.Context
import breathy.com.data.repository.PremiumRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
 * Manages advertising for Breathy. v1.0.23 — AD ADMOB-ONLY.
 *
 * The Unity Ads + Unity LevelPlay mediation stack was RETIRED in v1.0.23
 * (owner decision). Every format now loads and shows through the Google
 * Mobile Ads SDK against the NEW AdMob app (v1.0.21 migration, publisher
 * 9434446627275871, app `ca-app-pub-9434446627275871~1020887836`):
 *
 * ── Production AdMob identifiers (source of truth — never test IDs) ──────
 * - **App Open**:    `ca-app-pub-9434446627275871/1257681230`
 * - **Interstitial**: `ca-app-pub-9434446627275871/6356974992`
 * - **Rewarded 1 (Gold)**:  `ca-app-pub-9434446627275871/5304737452`  → +200 Gold
 * - **Rewarded 2 (Picture)**: `ca-app-pub-9434446627275871/1296220001` → SUNRISE
 *
 * ── Premium eligibility is checked PER AD FORMAT (never a global gate) ────
 * | Format       | Free     | Premium  |
 * |--------------|----------|----------|
 * | App Open     | shown    | BLOCKED  |
 * | Interstitial | shown    | BLOCKED  |
 * | Rewarded     | allowed  | ALLOWED  |
 *
 * Rewarded Ads are an OPTIONAL REWARD MECHANIC, not a forced placement:
 * Premium subscribers may still voluntarily watch a rewarded ad and receive
 * the configured reward (+200 Gold). There is deliberately NO global
 * `if (isPremium) return` in this manager — only the per-format checks
 * above. Becoming Premium releases ONLY the interstitial (and blocks app
 * opens); rewarded ads stay loaded and usable.
 *
 * ── Gold reward integrity ────────────────────────────────────────────────
 * The +200 Gold grant fires ONLY from AdMob's
 * [com.google.android.gms.ads.rewarded.OnUserEarnedRewardListener] — the
 * SDK calls it exclusively when the user actually finished the ad. Closing
 * early or a failed show grants nothing. Duplicate grants are prevented
 * twice: a per-show [AtomicBoolean] guard here, and a unique show token used
 * as the Gold-ledger dedup key in [DI AppModule] (same integrity model as
 * the previous Unity stack).
 *
 * ── No artificial cooldown ───────────────────────────────────────────────
 * There is deliberately NO app-side frequency cap for interstitial or
 * rewarded formats. An ad is eligible whenever the SDK reports one ready —
 * the SDK is the single pacing authority. The App Open format is the single
 * deliberate exception: it shows at most once per
 * [APP_OPEN_MIN_INTERVAL_MS] so backgrounding the app never triggers a
 * back-to-back full-screen ad.
 *
 * Thread safety: Google Mobile Ads public APIs are main-thread APIs.
 * Mutable ad references are confined to the main thread; loading flags use
 * [AtomicBoolean].
 */
class AdManager(
    private val context: Context
) {

    companion object {
        // ── AdMob production ad-unit identifiers (exact, never test IDs) ─
        // NEW AdMob app (v1.0.21 migration — publisher 9434446627275871,
        // app ca-app-pub-9434446627275871~1020887836). Consumed DIRECTLY by
        // the Google Mobile Ads SDK since v1.0.23 (the LevelPlay mediation
        // waterfall was retired).
        /** AdMob App Open ad unit (production). */
        const val ADMOB_APP_OPEN_AD_UNIT_ID = "ca-app-pub-9434446627275871/1257681230"

        /** AdMob Gold Rewarded ad unit (production, Rewarded Ad 1) → +200 Gold. */
        const val ADMOB_GOLD_REWARDED_AD_UNIT_ID = "ca-app-pub-9434446627275871/5304737452"

        /** AdMob Interstitial ad unit (production). */
        const val ADMOB_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9434446627275871/6356974992"

        /** AdMob Picture Rewarded ad unit (production, Rewarded Ad 2) —
         *  serves the SUNRISE picture watch flow ONLY. */
        const val ADMOB_PICTURE_REWARDED_AD_UNIT_ID = "ca-app-pub-9434446627275871/1296220001"

        /** Gold granted for a completed Gold rewarded ad. */
        const val REWARDED_GOLD_AMOUNT = 200

        // ── Timing ─────────────────────────────────────────────────────────
        /** Delay before the first ad-load retry after a load failure (15s). */
        private const val AD_RETRY_INITIAL_MS = 15_000L

        /** Maximum delay between ad-load retries (2 minutes). */
        private const val AD_RETRY_MAX_MS = 120_000L

        /** Minimum interval between App Open ad shows (4 hours). */
        private const val APP_OPEN_MIN_INTERVAL_MS = 4 * 60 * 60 * 1000L
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
        APP_OPEN
    }

    // ── SDK / loading state ────────────────────────────────────────────────

    private val isMobileAdsInitializing = AtomicBoolean(false)
    private val isMobileAdsReady = AtomicBoolean(false)
    private val isGoldRewardedLoading = AtomicBoolean(false)
    private val isPictureRewardedLoading = AtomicBoolean(false)
    private val isInterstitialLoading = AtomicBoolean(false)
    private val isAppOpenLoading = AtomicBoolean(false)
    private val isShowingAppOpen = AtomicBoolean(false)

    /** Loaded ads (main-thread confined). Null = not ready. */
    private var goldRewardedAd: RewardedAd? = null
    private var pictureRewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null

    private var retryJobs: MutableList<Job> = mutableListOf()

    private var appOpenLastShownAt = 0L

    /** Unique token for the currently shown rewarded ad (gold dedup key). */
    private var rewardShowToken: String? = null

    /** Guards against duplicate grant callbacks for one completed ad. */
    private val rewardGrantedForThisShow = AtomicBoolean(false)

    /**
     * What the CURRENTLY SHOWING rewarded ad was started for. Each purpose
     * owns its DEDICATED AdMob unit; the earned-reward callback routes the
     * grant accordingly:
     * - [RewardedPurpose.GOLD]        → +200 Gold via [rewardGrantCallback].
     * - [RewardedPurpose.PROFILE_PIC] → 1 watch toward the SUNRISE picture
     *   unlock via [profilePicGrantCallback].
     * Confined to the main thread (set in [startRewardedShow], read in the
     * earned-reward callback).
     */
    private var rewardedShowPurpose: RewardedPurpose = RewardedPurpose.GOLD

    /** Why a rewarded ad was started — routes the completion grant. */
    private enum class RewardedPurpose { GOLD, PROFILE_PIC }

    // ── Premium flag ───────────────────────────────────────────────────────

    /**
     * Verified Premium entitlement. NOT a global ad gate: per format,
     * app-open/interstitial check it to BLOCK, rewarded ads stay ALLOWED.
     */
    @Volatile
    var isPremiumUser: Boolean = false
        private set

    private val premiumScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Called with a unique token when a rewarded ad is EARNED (user finished
     * it). The owner (AppModule) grants exactly [REWARDED_GOLD_AMOUNT] Gold
     * using the token as the Gold-ledger dedup key — idempotent under
     * retries/replays.
     */
    var rewardGrantCallback: ((token: String) -> Unit)? = null

    /**
     * Called with a unique token when a rewarded ad started for the SUNRISE
     * picture flow is EARNED (user finished it). AppModule records one watch
     * toward the 5-ad unlock (ledger-deduped).
     */
    var profilePicGrantCallback: ((token: String) -> Unit)? = null

    /** Optional listener for ad lifecycle events. */
    var eventListener: AdEventListener? = null

    // ── Premium entitlement observation ────────────────────────────────────

    /**
     * Keep ad behaviour in sync with the verified Premium entitlement —
     * PER FORMAT (never a global gate):
     * - premium  → release ONLY the interstitial + app open. Rewarded ads
     *              stay loaded and usable (voluntary reward mechanic).
     * - lost     → resume loading the free-user formats.
     */
    fun attachPremiumState(state: kotlinx.coroutines.flow.StateFlow<PremiumRepository.PremiumState>) {
        premiumScope.launch {
            state.collect { premium ->
                val wasPremium = isPremiumUser
                isPremiumUser = premium.isPremium
                if (premium.isPremium && !wasPremium) {
                    Timber.d("AdManager: verified Premium active — blocking app-open/interstitial, rewarded stays available")
                    releaseFreeUserFullScreenAds()
                } else if (!premium.isPremium && wasPremium) {
                    Timber.d("AdManager: Premium no longer active — resuming free-user ad strategy")
                    loadInterstitialAd()
                    loadAppOpenAd()
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
     * Initialize the Google Mobile Ads SDK once during app startup
     * (MainActivity.onCreate) and then load every format. Rewarded ads load
     * for EVERYONE (Premium included) — rewarded is the voluntary reward
     * mechanic and stays available to subscribers.
     */
    fun initialize() {
        if (!isMobileAdsInitializing.compareAndSet(false, true)) return
        try {
            MobileAds.initialize(context) { status ->
                if (!isMobileAdsReady.compareAndSet(false, true)) return@initialize
                Timber.i(
                    "Google Mobile Ads initialized (adapters=%d) — loading AdMob inventory",
                    status.adapterStatusMap.size
                )
                loadRewardedAd()
                loadPictureRewardedAd()
                loadInterstitialAd()
                loadAppOpenAd()
            }
        } catch (e: Exception) {
            isMobileAdsInitializing.set(false)
            Timber.e(e, "Failed to initialize Google Mobile Ads SDK")
            eventListener?.onAdLoadFailed(AdType.INTERSTITIAL, "SDK init failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Rewarded ad — AdMob Rewarded Ad 1 (+200 Gold on earned reward)
    //  Premium users: ALLOWED — rewarded is a voluntary reward mechanic.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Load the Gold rewarded ad. Safe to call repeatedly. NO premium gate —
     * Premium users keep full access to rewarded rewards.
     */
    fun loadRewardedAd() {
        if (!isMobileAdsReady.get()) return
        if (goldRewardedAd != null) return
        if (!isGoldRewardedLoading.compareAndSet(false, true)) return
        try {
            RewardedAd.load(
                context,
                ADMOB_GOLD_REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isGoldRewardedLoading.set(false)
                        goldRewardedAd = ad
                        Timber.d("AdMob Gold rewarded ad loaded (%s)", ADMOB_GOLD_REWARDED_AD_UNIT_ID)
                        eventListener?.onAdLoaded(AdType.REWARDED)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isGoldRewardedLoading.set(false)
                        goldRewardedAd = null
                        Timber.w("AdMob Gold rewarded failed to load: %s — will retry", error.message)
                        eventListener?.onAdLoadFailed(AdType.REWARDED, error.message)
                        scheduleLoadRetry { loadRewardedAd() }
                    }
                }
            )
        } catch (e: Exception) {
            isGoldRewardedLoading.set(false)
            Timber.e(e, "Exception requesting Gold rewarded ad load")
            scheduleLoadRetry { loadRewardedAd() }
        }
    }

    /**
     * Load the DEDICATED Picture rewarded ad (Rewarded Ad 2) for the SUNRISE
     * picture flow. Fully independent from the Gold unit: own ready state,
     * own loading flag, own callback. Safe to call repeatedly. NO premium
     * gate — rewarded stays available to everyone.
     */
    fun loadPictureRewardedAd() {
        if (!isMobileAdsReady.get()) return
        if (pictureRewardedAd != null) return
        if (!isPictureRewardedLoading.compareAndSet(false, true)) return
        try {
            RewardedAd.load(
                context,
                ADMOB_PICTURE_REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isPictureRewardedLoading.set(false)
                        pictureRewardedAd = ad
                        Timber.d("AdMob Picture rewarded ad loaded (%s)", ADMOB_PICTURE_REWARDED_AD_UNIT_ID)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isPictureRewardedLoading.set(false)
                        pictureRewardedAd = null
                        Timber.w("AdMob Picture rewarded failed to load: %s — will retry", error.message)
                        scheduleLoadRetry { loadPictureRewardedAd() }
                    }
                }
            )
        } catch (e: Exception) {
            isPictureRewardedLoading.set(false)
            Timber.e(e, "Exception requesting Picture rewarded ad load")
            scheduleLoadRetry { loadPictureRewardedAd() }
        }
    }

    /**
     * Show the REAL rewarded ad (Rewarded Ad 1) for +200 Gold. The grant is
     * decided ONLY inside AdMob's OnUserEarnedRewardListener (routed through
     * [rewardGrantCallback]). Available to EVERYONE — Premium users included.
     *
     * @return true when the ad was actually shown (or is about to show).
     */
    fun showRewardedAd(activity: Activity): Boolean =
        startRewardedShow(activity, RewardedPurpose.GOLD)

    /** Show the DEDICATED Picture rewarded ad (Rewarded Ad 2) — SUNRISE flow. */
    fun showProfilePicRewardedAd(activity: Activity): Boolean =
        startRewardedShow(activity, RewardedPurpose.PROFILE_PIC)

    /** Internal show entry — keeps the private [RewardedPurpose] type hidden. */
    private fun startRewardedShow(activity: Activity, purpose: RewardedPurpose): Boolean {
        val ad = when (purpose) {
            RewardedPurpose.GOLD -> goldRewardedAd
            RewardedPurpose.PROFILE_PIC -> pictureRewardedAd
        }
        if (ad == null) {
            Timber.d("Rewarded ad not ready (purpose=%s) — requesting load", purpose)
            when (purpose) {
                RewardedPurpose.GOLD -> loadRewardedAd()
                RewardedPurpose.PROFILE_PIC -> loadPictureRewardedAd()
            }
            return false
        }
        // Consume the loaded ad and arm the per-show grant guards.
        when (purpose) {
            RewardedPurpose.GOLD -> goldRewardedAd = null
            RewardedPurpose.PROFILE_PIC -> pictureRewardedAd = null
        }
        rewardShowToken = UUID.randomUUID().toString().replace("-", "")
        rewardGrantedForThisShow.set(false)
        rewardedShowPurpose = purpose
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                eventListener?.onAdShown(AdType.REWARDED)
            }

            override fun onAdDismissedFullScreenContent() {
                eventListener?.onAdDismissed(AdType.REWARDED)
                when (purpose) {
                    RewardedPurpose.GOLD -> loadRewardedAd()
                    RewardedPurpose.PROFILE_PIC -> loadPictureRewardedAd()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Timber.w("AdMob rewarded failed to display: %s", adError.message)
                eventListener?.onAdShowFailed(AdType.REWARDED, adError.message)
                when (purpose) {
                    RewardedPurpose.GOLD -> loadRewardedAd()
                    RewardedPurpose.PROFILE_PIC -> loadPictureRewardedAd()
                }
            }
        }
        return try {
            // THE ONLY GRANT PATH — OnUserEarnedRewardListener fires ONLY when
            // the user actually finished the ad. SKIPPED/failed shows grant
            // nothing. The per-show AtomicBoolean guard prevents any double
            // credit. Routing: GOLD → +200 Gold; PROFILE_PIC → 1 SUNRISE watch.
            ad.show(activity) { _ ->
                val token = rewardShowToken
                if (token != null && rewardGrantedForThisShow.compareAndSet(false, true)) {
                    when (rewardedShowPurpose) {
                        RewardedPurpose.GOLD -> {
                            Timber.i(
                                "AdMob rewarded EARNED (unit=%s) — granting +%d Gold (token=%s…)",
                                ADMOB_GOLD_REWARDED_AD_UNIT_ID, REWARDED_GOLD_AMOUNT, token.take(8)
                            )
                            try {
                                rewardGrantCallback?.invoke(token)
                            } catch (e: Exception) {
                                Timber.e(e, "Gold grant for rewarded ad failed")
                            }
                        }
                        RewardedPurpose.PROFILE_PIC -> {
                            Timber.i(
                                "AdMob rewarded EARNED (unit=%s) — recording 1 SUNRISE watch (token=%s…)",
                                ADMOB_PICTURE_REWARDED_AD_UNIT_ID, token.take(8)
                            )
                            try {
                                profilePicGrantCallback?.invoke(token)
                            } catch (e: Exception) {
                                Timber.e(e, "Profile-pic unlock grant for rewarded ad failed")
                            }
                        }
                    }
                } else if (token == null) {
                    Timber.w("AdMob rewarded earned reward without show token — ignoring")
                }
            }
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
    //  Interstitial ad — AdMob Interstitial — NO cooldown (the SDK is the
    //  only pacing authority) · Premium: BLOCKED per-format.
    // ═══════════════════════════════════════════════════════════════════════

    /** Load an interstitial ad. Safe to call repeatedly; skipped for Premium. */
    fun loadInterstitialAd() {
        if (isPremiumUser) return // Premium → interstitial BLOCKED
        if (!isMobileAdsReady.get()) return
        if (interstitialAd != null) return
        if (!isInterstitialLoading.compareAndSet(false, true)) return
        try {
            InterstitialAd.load(
                context,
                ADMOB_INTERSTITIAL_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isInterstitialLoading.set(false)
                        interstitialAd = ad
                        Timber.d("AdMob interstitial ad loaded (%s)", ADMOB_INTERSTITIAL_AD_UNIT_ID)
                        eventListener?.onAdLoaded(AdType.INTERSTITIAL)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isInterstitialLoading.set(false)
                        interstitialAd = null
                        Timber.w("AdMob interstitial failed to load: %s — will retry", error.message)
                        eventListener?.onAdLoadFailed(AdType.INTERSTITIAL, error.message)
                        scheduleLoadRetry { loadInterstitialAd() }
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading.set(false)
            Timber.e(e, "Exception requesting interstitial ad load")
            scheduleLoadRetry { loadInterstitialAd() }
        }
    }

    /**
     * Show an interstitial ad whenever the SDK has one ready.
     * Calls [onAdDismissed] whether the ad was shown or skipped, so callers
     * can always continue navigation.
     *
     * NO app-side cooldown: eligibility is decided ENTIRELY by the ad SDK.
     * Only verified Premium users are excluded (per-format eligibility:
     * Premium → interstitial BLOCKED).
     */
    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (isPremiumUser) {
            Timber.d("Skipping interstitial show: premium user")
            onAdDismissed()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Timber.d("Interstitial not available, proceeding without ad")
            loadInterstitialAd()
            onAdDismissed()
            return
        }

        interstitialAd = null // this load is consumed by the show
        try {
            interstitialDismissed = onAdDismissed
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    eventListener?.onAdShown(AdType.INTERSTITIAL)
                }

                override fun onAdDismissedFullScreenContent() {
                    eventListener?.onAdDismissed(AdType.INTERSTITIAL)
                    // Never block the caller's navigation when the ad closes.
                    val continuation = interstitialDismissed
                    interstitialDismissed = null
                    continuation?.invoke()
                    loadInterstitialAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Timber.w("AdMob interstitial failed to display: %s", adError.message)
                    eventListener?.onAdShowFailed(AdType.INTERSTITIAL, adError.message)
                    // Never block the caller's navigation when the show fails.
                    val continuation = interstitialDismissed
                    interstitialDismissed = null
                    continuation?.invoke()
                    loadInterstitialAd()
                }
            }
            ad.show(activity)
        } catch (e: Exception) {
            Timber.e(e, "Exception showing interstitial — never crash on ad errors")
            interstitialDismissed = null
            onAdDismissed()
            loadInterstitialAd()
        }
    }

    /** Pending navigation continuation for the showing interstitial (main thread only). */
    private var interstitialDismissed: (() -> Unit)? = null

    /** Whether an interstitial can be shown right now (ready + not Premium).
     *  There is NO cooldown — the SDK decides availability. */
    fun canShowInterstitial(): Boolean {
        if (isPremiumUser) return false
        return interstitialAd != null
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  App Open ad — AdMob App Open · Premium: BLOCKED per-format.
    //  Deliberate pacing: at most ONE show per [APP_OPEN_MIN_INTERVAL_MS]
    //  (4h) so backgrounding the app never fires back-to-back full screens.
    // ═══════════════════════════════════════════════════════════════════════

    /** Load an App Open ad. Safe to call repeatedly; skipped for Premium. */
    fun loadAppOpenAd() {
        if (isPremiumUser) return // Premium → app open BLOCKED
        if (!isMobileAdsReady.get()) return
        if (appOpenAd != null) return
        if (!isAppOpenLoading.compareAndSet(false, true)) return
        try {
            AppOpenAd.load(
                context,
                ADMOB_APP_OPEN_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isAppOpenLoading.set(false)
                        appOpenAd = ad
                        Timber.d("AdMob app-open ad loaded (%s)", ADMOB_APP_OPEN_AD_UNIT_ID)
                        eventListener?.onAdLoaded(AdType.APP_OPEN)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isAppOpenLoading.set(false)
                        appOpenAd = null
                        Timber.w("AdMob app-open failed to load: %s — will retry", error.message)
                        scheduleLoadRetry { loadAppOpenAd() }
                    }
                }
            )
        } catch (e: Exception) {
            isAppOpenLoading.set(false)
            Timber.e(e, "Exception requesting app-open ad load")
            scheduleLoadRetry { loadAppOpenAd() }
        }
    }

    /**
     * Show the App Open ad if one is ready, the user is not Premium, and the
     * [APP_OPEN_MIN_INTERVAL_MS] pacing window has elapsed. Call from the
     * host Activity's onResume. Never blocks or throws — ads must never
     * crash the app.
     */
    fun maybeShowAppOpenAd(activity: Activity) {
        if (isPremiumUser) return
        if (!isShowingAppOpen.compareAndSet(false, true)) return
        try {
            val ad = appOpenAd
            val now = System.currentTimeMillis()
            if (ad == null) {
                loadAppOpenAd()
                return
            }
            if (appOpenLastShownAt != 0L && now - appOpenLastShownAt < APP_OPEN_MIN_INTERVAL_MS) {
                return
            }
            appOpenAd = null // consumed by the show
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    appOpenLastShownAt = System.currentTimeMillis()
                    eventListener?.onAdShown(AdType.APP_OPEN)
                }

                override fun onAdDismissedFullScreenContent() {
                    eventListener?.onAdDismissed(AdType.APP_OPEN)
                    loadAppOpenAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Timber.w("AdMob app-open failed to display: %s", adError.message)
                    eventListener?.onAdShowFailed(AdType.APP_OPEN, adError.message)
                    loadAppOpenAd()
                }
            }
            ad.show(activity)
        } catch (e: Exception) {
            Timber.e(e, "Exception showing app-open ad — never crash on ad errors")
            loadAppOpenAd()
        } finally {
            isShowingAppOpen.set(false)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Release ONLY the free-user full-screen formats. Called when the user
     * becomes a verified Premium subscriber (per-format eligibility:
     * Premium → app-open/interstitial BLOCKED). Rewarded ads are
     * deliberately KEPT — Premium users may still watch them voluntarily
     * for +200 Gold.
     */
    private fun releaseFreeUserFullScreenAds() {
        interstitialAd = null
        interstitialDismissed = null
        isInterstitialLoading.set(false)
        appOpenAd = null
        isAppOpenLoading.set(false)
        Timber.d("AdManager: app-open + interstitial released (Premium active — rewarded untouched)")
    }

    /**
     * Release all ad references and reset all stacks (full teardown only —
     * NOT used for the Premium transition, which only blocks per-format).
     */
    fun release() {
        goldRewardedAd = null
        pictureRewardedAd = null
        interstitialAd = null
        appOpenAd = null
        interstitialDismissed = null
        isGoldRewardedLoading.set(false)
        isPictureRewardedLoading.set(false)
        isInterstitialLoading.set(false)
        isAppOpenLoading.set(false)
        retryJobs.forEach { it.cancel() }
        retryJobs.clear()
        Timber.d("AdManager released — all ad references cleared")
    }
}

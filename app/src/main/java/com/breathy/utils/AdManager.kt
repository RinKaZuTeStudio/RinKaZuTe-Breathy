package com.breathy.utils

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import timber.log.Timber
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages AdMob app-open and interstitial ads for Breathy.
 *
 * Ad Configuration:
 * - **App ID**: `ca-app-pub-9434446627275871~3054699475`
 * - **Open App Ad Unit**: `ca-app-pub-9434446627275871/9005175949`
 * - **Interstitial Ad Unit**: `ca-app-pub-9434446627275871/7446506098`
 *
 * App-open ads are shown on cold start; interstitial ads are shown
 * between screens with a frequency cap of max 1 per 3 minutes.
 * Premium subscribers are automatically exempt from all ads.
 *
 * Thread safety: All mutable state is accessed from the main thread
 * (Google Mobile Ads SDK requirement). [AtomicBoolean] is used for
 * loading flags that may be read from background threads.
 */
class AdManager(
    private val context: Context
) {

    companion object {
        // ── AdMob IDs ──────────────────────────────────────────────────────
        /** AdMob App ID (configured in AndroidManifest). */
        private const val ADMOB_APP_ID = "ca-app-pub-9434446627275871~3054699475"

        /** Ad unit ID for app-open ads. */
        private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-9434446627275871/9005175949"

        /** Ad unit ID for interstitial ads. */
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9434446627275871/7446506098"

        // ── Timing ─────────────────────────────────────────────────────────
        /** Maximum age of a cached app-open ad before it's considered stale (4 hours). */
        private const val AD_CACHE_DURATION_MS = 4L * 60L * 60L * 1000L

        /** Minimum interval between interstitial ad shows (3 minutes). */
        private const val INTERSTITIAL_FREQUENCY_CAP_MS = 3L * 60L * 1000L

        // ── Loading States ─────────────────────────────────────────────────
        /** Maximum time to wait for an ad to load before giving up (5 seconds). */
        private const val AD_LOAD_TIMEOUT_MS = 5_000L
    }

    /**
     * Callback interface for ad lifecycle events.
     */
    interface AdEventListener {
        /** Called when an ad is successfully loaded and ready to show. */
        fun onAdLoaded(adType: AdType) {}

        /** Called when an ad fails to load. The app should NOT crash. */
        fun onAdLoadFailed(adType: AdType, error: String) {}

        /** Called when an ad is displayed to the user. */
        fun onAdShown(adType: AdType) {}

        /** Called when an ad is dismissed by the user. */
        fun onAdDismissed(adType: AdType) {}

        /** Called when an ad fails to show after being loaded. */
        fun onAdShowFailed(adType: AdType, error: String) {}

        /** Called when the user clicks on an ad. */
        fun onAdClicked(adType: AdType) {}

        /** Called when an ad impression is recorded. */
        fun onAdImpression(adType: AdType) {}
    }

    /**
     * Types of ads supported by Breathy.
     */
    enum class AdType {
        APP_OPEN,
        INTERSTITIAL
    }

    /**
     * Current loading state for each ad type.
     */
    enum class AdLoadingState {
        /** No ad is loaded or loading. */
        IDLE,
        /** An ad is currently being fetched from AdMob. */
        LOADING,
        /** An ad is loaded and ready to be shown. */
        LOADED,
        /** The ad is currently being displayed. */
        SHOWING
    }

    // ── Ad References ──────────────────────────────────────────────────────────

    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null

    // ── Loading State ──────────────────────────────────────────────────────────

    private val isAppOpenAdLoading = AtomicBoolean(false)
    private val isInterstitialLoading = AtomicBoolean(false)

    private var appOpenAdLoadTime: Long = 0L
    private var appOpenLoadingState: AdLoadingState = AdLoadingState.IDLE
    private var interstitialLoadingState: AdLoadingState = AdLoadingState.IDLE

    // ── Frequency Capping ──────────────────────────────────────────────────────

    private var lastInterstitialShowTime: Long = 0L

    // ── Premium Flag ───────────────────────────────────────────────────────────

    /** When `true`, no ads are loaded or shown. */
    var isPremiumUser: Boolean = false

    // ── Event Listener ─────────────────────────────────────────────────────────

    /** Optional listener for ad lifecycle events. */
    var eventListener: AdEventListener? = null

    // ═══════════════════════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initialize the Mobile Ads SDK. Should be called once during app startup,
     * ideally in [android.app.Application.onCreate].
     *
     * Initialization is performed on a background thread so it doesn't block
     * the main thread. Ad loading should only begin after initialization
     * completes.
     */
    fun initialize() {
        try {
            MobileAds.initialize(context) { initializationStatus ->
                val statusMap = initializationStatus.adapterStatusMap
                val readyCount = statusMap.count { it.value.initializationState.name == "READY" }
                Timber.d(
                    "Mobile Ads SDK initialized: %d/%d adapters ready",
                    readyCount, statusMap.size
                )
                // Pre-load both ad types after SDK initialization
                loadAppOpenAd()
                loadInterstitialAd()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Mobile Ads SDK")
            eventListener?.onAdLoadFailed(AdType.APP_OPEN, "SDK init failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  App-Open Ad
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the current loading state for the app-open ad.
     */
    fun getAppOpenLoadingState(): AdLoadingState = appOpenLoadingState

    /**
     * Pre-load an app-open ad. Should be called during app startup
     * so the ad is ready when the user reaches the home screen.
     *
     * If the ad is already loading or a valid cached ad exists, this
     * is a no-op.
     */
    fun loadAppOpenAd() {
        if (isPremiumUser) {
            Timber.d("Skipping app-open ad load: premium user")
            return
        }
        if (!isAppOpenAdLoading.compareAndSet(false, true)) {
            Timber.d("App-open ad already loading, skipping")
            return
        }
        if (isAppOpenAdAvailable()) {
            isAppOpenAdLoading.set(false)
            Timber.d("App-open ad already cached and valid, skipping load")
            return
        }

        appOpenLoadingState = AdLoadingState.LOADING
        Timber.d("Loading app-open ad: unit=%s", APP_OPEN_AD_UNIT_ID)

        try {
            AppOpenAd.load(
                context,
                APP_OPEN_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        appOpenAdLoadTime = Date().time
                        isAppOpenAdLoading.set(false)
                        appOpenLoadingState = AdLoadingState.LOADED
                        Timber.d("App-open ad loaded successfully")
                        eventListener?.onAdLoaded(AdType.APP_OPEN)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        appOpenAd = null
                        isAppOpenAdLoading.set(false)
                        appOpenLoadingState = AdLoadingState.IDLE
                        Timber.w("App-open ad failed to load: code=%d, message=%s", error.code, error.message)
                        eventListener?.onAdLoadFailed(AdType.APP_OPEN, "Code ${error.code}: ${error.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isAppOpenAdLoading.set(false)
            appOpenLoadingState = AdLoadingState.IDLE
            Timber.e(e, "Exception loading app-open ad — ad load failures don't crash")
            eventListener?.onAdLoadFailed(AdType.APP_OPEN, "Exception: ${e.message}")
        }
    }

    /**
     * Show the app-open ad if one is available. Calls [onAdDismissed]
     * whether the ad was shown or skipped (error, not loaded, premium, etc.),
     * so the caller can always proceed to the next screen.
     *
     * @param activity     The current activity (required by the AdMob SDK).
     * @param onAdDismissed Callback invoked when the ad flow is complete
     *                       (ad dismissed, failed to show, or skipped).
     */
    fun showAppOpenAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (isPremiumUser) {
            Timber.d("Skipping app-open ad show: premium user")
            onAdDismissed()
            return
        }

        if (!isAppOpenAdAvailable()) {
            Timber.d("App-open ad not available, proceeding without ad")
            onAdDismissed()
            return
        }

        appOpenLoadingState = AdLoadingState.SHOWING

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                appOpenLoadingState = AdLoadingState.IDLE
                loadAppOpenAd() // Pre-load next ad
                Timber.d("App-open ad dismissed")
                eventListener?.onAdDismissed(AdType.APP_OPEN)
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                appOpenLoadingState = AdLoadingState.IDLE
                loadAppOpenAd()
                Timber.w("App-open ad failed to show: code=%d, message=%s", error.code, error.message)
                eventListener?.onAdShowFailed(AdType.APP_OPEN, "Code ${error.code}: ${error.message}")
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Timber.d("App-open ad showed successfully")
                eventListener?.onAdShown(AdType.APP_OPEN)
            }

            override fun onAdClicked() {
                eventListener?.onAdClicked(AdType.APP_OPEN)
            }

            override fun onAdImpression() {
                eventListener?.onAdImpression(AdType.APP_OPEN)
            }
        }

        try {
            appOpenAd?.show(activity) ?: run {
                appOpenLoadingState = AdLoadingState.IDLE
                onAdDismissed()
            }
        } catch (e: Exception) {
            appOpenAd = null
            appOpenLoadingState = AdLoadingState.IDLE
            Timber.e(e, "Exception showing app-open ad — ad show failures don't crash")
            eventListener?.onAdShowFailed(AdType.APP_OPEN, "Exception: ${e.message}")
            onAdDismissed()
        }
    }

    /**
     * Check if the cached app-open ad is still valid.
     * Ads expire after [AD_CACHE_DURATION_MS] to avoid showing stale creative.
     */
    private fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null && (Date().time - appOpenAdLoadTime) < AD_CACHE_DURATION_MS
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Interstitial Ad
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the current loading state for the interstitial ad.
     */
    fun getInterstitialLoadingState(): AdLoadingState = interstitialLoadingState

    /**
     * Load an interstitial ad. Should be called well before the moment
     * you intend to show it (e.g., when a user starts a story post).
     *
     * If the ad is already loading or a valid cached ad exists, this
     * is a no-op.
     */
    fun loadInterstitialAd() {
        if (isPremiumUser) {
            Timber.d("Skipping interstitial ad load: premium user")
            return
        }
        if (!isInterstitialLoading.compareAndSet(false, true)) {
            Timber.d("Interstitial ad already loading, skipping")
            return
        }
        if (interstitialAd != null) {
            isInterstitialLoading.set(false)
            Timber.d("Interstitial ad already loaded, skipping")
            return
        }

        interstitialLoadingState = AdLoadingState.LOADING
        Timber.d("Loading interstitial ad: unit=%s", INTERSTITIAL_AD_UNIT_ID)

        try {
            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialLoading.set(false)
                        interstitialLoadingState = AdLoadingState.LOADED
                        Timber.d("Interstitial ad loaded successfully")
                        eventListener?.onAdLoaded(AdType.INTERSTITIAL)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isInterstitialLoading.set(false)
                        interstitialLoadingState = AdLoadingState.IDLE
                        Timber.w(
                            "Interstitial ad failed to load: code=%d, message=%s",
                            error.code, error.message
                        )
                        eventListener?.onAdLoadFailed(
                            AdType.INTERSTITIAL,
                            "Code ${error.code}: ${error.message}"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading.set(false)
            interstitialLoadingState = AdLoadingState.IDLE
            Timber.e(e, "Exception loading interstitial ad — ad load failures don't crash")
            eventListener?.onAdLoadFailed(AdType.INTERSTITIAL, "Exception: ${e.message}")
        }
    }

    /**
     * Show an interstitial ad if available and the frequency cap is met.
     * Calls [onAdDismissed] whether the ad was shown or skipped.
     *
     * Frequency capping: Maximum 1 interstitial ad per 3 minutes.
     * This prevents ad fatigue and maintains a good user experience.
     *
     * @param activity     The current activity (required by the AdMob SDK).
     * @param onAdDismissed Callback invoked when the ad flow is complete.
     */
    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (isPremiumUser) {
            Timber.d("Skipping interstitial ad show: premium user")
            onAdDismissed()
            return
        }

        // ── Frequency Cap Check ────────────────────────────────────────────
        val now = Date().time
        val elapsedSinceLastShow = now - lastInterstitialShowTime
        if (elapsedSinceLastShow < INTERSTITIAL_FREQUENCY_CAP_MS) {
            val remainingSeconds = (INTERSTITIAL_FREQUENCY_CAP_MS - elapsedSinceLastShow) / 1000
            Timber.d(
                "Interstitial ad frequency cap: %ds remaining until next show",
                remainingSeconds
            )
            onAdDismissed()
            return
        }

        if (interstitialAd == null) {
            Timber.d("Interstitial ad not available, proceeding without ad")
            loadInterstitialAd() // Trigger a load for next time
            onAdDismissed()
            return
        }

        interstitialLoadingState = AdLoadingState.SHOWING

        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                interstitialLoadingState = AdLoadingState.IDLE
                loadInterstitialAd() // Pre-load next ad
                Timber.d("Interstitial ad dismissed")
                eventListener?.onAdDismissed(AdType.INTERSTITIAL)
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                interstitialLoadingState = AdLoadingState.IDLE
                loadInterstitialAd()
                Timber.w(
                    "Interstitial ad failed to show: code=%d, message=%s",
                    error.code, error.message
                )
                eventListener?.onAdShowFailed(
                    AdType.INTERSTITIAL,
                    "Code ${error.code}: ${error.message}"
                )
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                lastInterstitialShowTime = Date().time
                Timber.d("Interstitial ad showed successfully")
                eventListener?.onAdShown(AdType.INTERSTITIAL)
            }

            override fun onAdClicked() {
                eventListener?.onAdClicked(AdType.INTERSTITIAL)
            }

            override fun onAdImpression() {
                eventListener?.onAdImpression(AdType.INTERSTITIAL)
            }
        }

        try {
            interstitialAd?.show(activity) ?: run {
                interstitialLoadingState = AdLoadingState.IDLE
                onAdDismissed()
            }
        } catch (e: Exception) {
            interstitialAd = null
            interstitialLoadingState = AdLoadingState.IDLE
            Timber.e(e, "Exception showing interstitial ad — ad show failures don't crash")
            eventListener?.onAdShowFailed(AdType.INTERSTITIAL, "Exception: ${e.message}")
            onAdDismissed()
        }
    }

    /**
     * Check if the interstitial ad can be shown right now (loaded + frequency cap met).
     *
     * @return `true` if the interstitial ad is loaded and the 3-minute
     *         frequency cap has elapsed since the last show.
     */
    fun canShowInterstitial(): Boolean {
        if (isPremiumUser) return false
        if (interstitialAd == null) return false
        val elapsed = Date().time - lastInterstitialShowTime
        return elapsed >= INTERSTITIAL_FREQUENCY_CAP_MS
    }

    /**
     * Get the remaining time in seconds until the interstitial frequency
     * cap allows the next ad to be shown.
     *
     * @return Seconds remaining, or 0 if an ad can be shown now.
     */
    fun getInterstitialCooldownSeconds(): Long {
        val elapsed = Date().time - lastInterstitialShowTime
        val remaining = INTERSTITIAL_FREQUENCY_CAP_MS - elapsed
        return if (remaining > 0) remaining / 1000 else 0L
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Release all ad references. Call this when the user becomes a premium
     * subscriber or when the ad manager is no longer needed.
     */
    fun release() {
        appOpenAd = null
        interstitialAd = null
        appOpenLoadingState = AdLoadingState.IDLE
        interstitialLoadingState = AdLoadingState.IDLE
        isAppOpenAdLoading.set(false)
        isInterstitialLoading.set(false)
        Timber.d("AdManager released — all ad references cleared")
    }
}

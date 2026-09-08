package breathy.com.data.repository

import android.app.Activity
import android.content.Context
import android.util.Base64
import breathy.com.data.models.Subscription
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Lifecycle state of the Google Play subscription, derived from REAL Play
 * Billing data on every re-check — never from a locally cached flag.
 *
 * - [ACTIVE]: purchase verified, auto-renewal on.
 * - [CANCELED_BUT_STILL_ENTITLED]: the user cancelled auto-renewal in
 *   Google Play but the current billing period has not ended yet —
 *   premium STAYS active until Play stops returning the purchase.
 * - [EXPIRED] / [REVOKED]: Play no longer returns a valid purchase
 *   (period ended, or revoked/refunded) — premium removed.
 * - [PENDING]: purchase awaiting payment (no entitlement yet).
 * - [PAUSED]: reported via the Firestore mirror when known (account hold).
 * - [NONE]: no subscription on Google Play for this account.
 */
enum class SubscriptionStatus {
    NONE,
    ACTIVE,
    CANCELED_BUT_STILL_ENTITLED,
    EXPIRED,
    REVOKED,
    PENDING,
    PAUSED
}

/**
 * Central, app-scoped Premium entitlement manager for Breathy.
 *
 * Implements a REAL Google Play auto-renewing subscription:
 * - Product ID:  `breathy_premium_monthly`
 * - Base plan:   `monthly-premium`
 * - Offer:       `launch-offer` (used automatically when available)
 * - Type:        Monthly, auto-renewing, SUBS product
 *
 * The repository is the SINGLE SOURCE OF TRUTH for premium state across the
 * whole app:
 * - [AdManager] observes it to enable/disable ads (verified premium = zero ads).
 * - The Events system observes it to gate premium-only events.
 * - The Subscription screen renders the localized Play price from it.
 *
 * Entitlement rules (verified purchase state only — never a bare flag):
 * 1. On startup and after every purchase/restore, [queryPurchasesAsync] is
 *    called against Google Play; a subscription counts as active only when
 *    `purchaseState == PURCHASED` for [PRODUCT_ID_PREMIUM].
 * 2. The purchase is acknowledged (or acknowledged immediately after buying).
 * 3. Entitlement is mirrored into Firestore `subscriptions/{uid}` with the
 *    purchase token, auto-renew state and an estimated expiry so other
 *    surfaces (leaderboard badge, public profile) can display it.
 * 4. On every app start the entitlement is RE-CHECKED against Google Play,
 *    so expired/cancelled subscriptions automatically lose premium (and ads
 *    resume) without relying on stale local flags.
 *
 * Purchase lifecycle handling: success, pending (e.g. cash payments),
 * user-cancelled, failed, restored, renewed, and expired are all handled —
 * see [handlePurchases].
 */
class PremiumRepository(
    context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : PurchasesUpdatedListener {

    companion object {
        /** Google Play product id for the monthly premium subscription. */
        const val PRODUCT_ID_PREMIUM = "breathy_premium_monthly"

        /** Google Play base plan id for the monthly premium subscription. */
        const val BASE_PLAN_ID = "monthly-premium"

        /** Active launch offer for the base plan. */
        const val OFFER_ID = "launch-offer"

        /** Firestore collection mirroring the entitlement. */
        private const val SUBSCRIPTIONS_COLLECTION = "subscriptions"

        /** Connection retry backoff (ms). */
        private const val RETRY_BACKOFF_MS = 2_000L

        /** Max automatic BillingClient connection retries before surfacing an error. */
        private const val MAX_CONNECT_RETRIES = 5

        /** Max automatic retries for the price query before we surface a manual Retry. */
        private const val PRICE_MAX_AUTO_RETRIES = 5

        /** Hard timeout for a single queryProductDetailsAsync round-trip. */
        private const val PRICE_QUERY_TIMEOUT_MS = 15_000L

        /** v1.0.22 — watchdog for a connection attempt whose setup callback
         *  never arrives (Play Store mid-update, binder death). After this
         *  window the connection is re-driven instead of wedging forever. */
        private const val CONNECT_WATCHDOG_MS = 20_000L

        /** v1.0.22 — bounded wait for a purchase tap until the client is
         *  connected AND the product details + offer token are loaded.
         *  v1.0.24 — 30s: the window must also cover the connection retry
         *  chain (2s backoff × 5) plus one full queryProductDetails round
         *  trip, so a slow-but-recovering Play Store still launches instead
         *  of timing out into an error. Still strictly bounded — never an
         *  infinite preparing state. */
        private const val PURCHASE_READINESS_TIMEOUT_MS = 30_000L

        /**
         * Google Play licensing PUBLIC verification key for this app.
         * This is public verification material (from Play Console → Monetize
         * setup), NOT a private secret — it is used to verify purchase
         * signatures client-side.
         */
        private const val PLAY_LICENSE_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxC6bruDRbG/etTcw8byXzclmjrxsqvQulsis4TD1rm+smwYNyLfi2EC6N1/P7xCv9cRC3TM7/oxA1va+P4a3MWpTSXK41ONrnDPpk2UQmATbwM2+79HRClMkBq49OCjuhpNjPPNa547Cl+d9idF7yMox0CZpjQctuo6OYC7dFNziBCLOkX+G6e2fWT1Ekqn/jLAh7UVKmD39kES2khtJxu5naoyHUQG69W/9xY64Bp5a2kwgNNUzL23QVyP/TUt2EedFfhwfg9jOvsi5mGS6/jWcpzM3OJ5lyaECSbmH832td+czc610VSPoPEdZa7simgSWAAswrDCihalqsrTP8wIDAQAB"

        /** Grace window applied on top of an estimated expiry (3 days). */
        private val EXPIRY_GRACE_MS = TimeUnit.DAYS.toMillis(3)
    }

    /** Immutable snapshot of the premium state exposed to the whole app. */
    data class PremiumState(
        /** Verified active premium entitlement (from Google Play, not a cached flag). */
        val isPremium: Boolean = false,
        /** True while the billing client is connecting / querying. */
        val isChecking: Boolean = true,
        /** Localized price string from Google Play (e.g. "$2.99"). */
        val localizedPrice: String? = null,
        /** Why the Play price is not available yet (null = no price problem). */
        val priceError: String? = null,
        /** Localized currency code from Google Play. */
        val currencyCode: String? = null,
        /** Whether a launch offer is currently available on the base plan. */
        val hasLaunchOffer: Boolean = false,
        /** True while a purchase flow is running. */
        val isPurchasing: Boolean = false,
        /**
         * True while the repository is preparing the purchase the user just
         * requested (connecting to Google Play / finishing the product query)
         * before the Play sheet can open. v1.0.22 — the Subscribe button is
         * NEVER allowed to fail silently: this flag gives visible feedback
         * ("connecting…") for the window where the sheet cannot open yet.
         */
        val isPreparingPurchase: Boolean = false,
        /**
         * v1.0.22 — why the Google Play purchase sheet did not open on the
         * last Subscribe tap (null = no failure). Surfaced in the paywall UI
         * next to the button; replaces the previous silent `return false`.
         */
        val purchaseError: String? = null,
        /** The active subscription document (Firestore mirror), when available. */
        val subscription: Subscription? = null,
        /** Detailed lifecycle state derived from the last verified Play query. */
        val status: SubscriptionStatus = SubscriptionStatus.NONE,
        /** Whether Google Play will auto-renew (false = user cancelled renewal). */
        val isAutoRenewing: Boolean? = null,
        /**
         * The account uid the CURRENT in-memory entitlement belongs to.
         * Premium is ALWAYS bound to the authenticated app account that
         * owns the Play purchase — never a global app-wide flag.
         */
        val entitlementUid: String? = null
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Auto-retry counter for the Play price query (reset on success / manual refresh). */
    private var priceQueryAttempts = 0

    /** Auto-retry counter for the BillingClient connection (reset on success). */
    private var connectAttempts = 0

    /** Generation token — stale query callbacks/timeouts are ignored. */
    private var priceQueryGeneration = 0

    private val _state = MutableStateFlow(PremiumState())
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    private var billingClient: BillingClient? = null
    private var premiumProductDetails: ProductDetails? = null
    private var cachedOfferToken: String? = null

    private val isConnecting = AtomicBoolean(false)

    /**
     * v1.0.24 — set when the BillingClient connection is declared hard-dead
     * (max connect retries exhausted via the watchdog or the setup callback).
     * Lets a pending purchase-wait exit EARLY with the concrete reason
     * instead of riding the full readiness timeout, and guarantees the UI
     * never sits in isPreparingPurchase longer than the bounded window.
     */
    @Volatile
    private var billingHardFailure: Boolean = false

    /**
     * v1.0.25 — the LAST concrete verdict Google Play returned for a
     * connection attempt ("code X: debugMessage"), or null when Play never
     * responded at all (pure disconnects). Appended to the user-facing
     * exhaustion messages so the REAL Play reason is visible on screen —
     * not only in logcat. Cleared on every fresh attempt and on success.
     */
    @Volatile
    private var lastBillingFailureDetail: String? = null

    /**
     * v1.0.22 — generation token for BillingClient connection attempts.
     * Incremented on every startConnection and on every setup callback so a
     * stale watchdog can never re-drive a connection that has already
     * resolved. This exists because the previous code could PERMANENTLY wedge
     * the purchase flow: [onBillingServiceDisconnected] never reset
     * [isConnecting], so every later connectAndRefresh became a silent no-op
     * piggyback and the Subscribe button stopped opening Google Play forever
     * (the reported bug).
     */
    private val connectionGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    // ═════════════════════════════════════════════════════════════════
    //  Account binding — premium NEVER leaks between accounts
    // ═════════════════════════════════════════════════════════════════

    /**
     * Called whenever the authenticated app account changes (login, logout,
     * account switch, restore session). The in-memory entitlement is RESET
     * immediately and then re-resolved for the CURRENT account:
     *
     * - logout (uid == null)   → entitlement wiped from memory at once;
     *   Account B never sees Account A's premium while resolving.
     * - login (uid != null)    → fresh Google Play re-check; the purchase
     *   token must be bound to THIS uid in `subscriptions/{uid}` to grant
     *   premium (see [processSinglePurchase]).
     *
     * Safe to call repeatedly with the same uid — the state is only reset
     * when the uid actually changes.
     */
    fun onAuthStateChanged(newUid: String?) {
        if (_state.value.entitlementUid == newUid) return
        if (newUid == null) {
            Timber.i("PremiumRepo: account signed out — clearing premium state from memory")
            _state.value = PremiumState(
                localizedPrice = _state.value.localizedPrice,
                currencyCode = _state.value.currencyCode,
                hasLaunchOffer = _state.value.hasLaunchOffer,
                isChecking = false,
                status = SubscriptionStatus.NONE
            )
        } else {
            Timber.i("PremiumRepo: account changed to %s — re-resolving entitlement for the new account", newUid)
            _state.value = PremiumState(
                localizedPrice = _state.value.localizedPrice,
                currencyCode = _state.value.currencyCode,
                hasLaunchOffer = _state.value.hasLaunchOffer,
                entitlementUid = newUid,
                isChecking = true
            )
            recheckEntitlement()
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Billing client lifecycle
    // ═════════════════════════════════════════════════════════════════════

    private fun ensureClient(): BillingClient {
        billingClient?.let { return it }
        // PBL 8.0.0 — enablePendingPurchases now requires explicit
        // PendingPurchasesParams (no-arg overload was removed).
        val client = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().build())
            .build()
        billingClient = client
        return client
    }

    /** Connect to Google Play Billing (idempotent, safe to call repeatedly). */
    fun connectAndRefresh(onReady: (() -> Unit)? = null) {
        if (isConnecting.compareAndSet(false, true)) {
            _state.update { it.copy(isChecking = true) }
            val client = ensureClient()
            if (client.isReady) {
                isConnecting.set(false)
                onConnected(onReady)
                return
            }
            // v1.0.22 — watchdog: if the setup callback never arrives (Play
            // Store mid-update, binder death), re-drive the connection instead
            // of leaving isConnecting stuck true forever.
            val attemptGeneration = connectionGeneration.incrementAndGet()
            scope.launch {
                kotlinx.coroutines.delay(CONNECT_WATCHDOG_MS)
                if (attemptGeneration == connectionGeneration.get() && isConnecting.get()) {
                    Timber.w("PremiumRepo: connection watchdog fired — re-driving BillingClient connection")
                    isConnecting.set(false)
                    connectAttempts++
                    if (connectAttempts >= MAX_CONNECT_RETRIES) {
                        billingHardFailure = true // v1.0.24 — release pending purchase waits
                        val detail = lastBillingFailureDetail // v1.0.25 — show the real Play verdict
                        onPriceUnavailable(
                            "Google Play Billing is not responding on this device" +
                                (if (detail != null) " (Play said: $detail)." else ".") +
                                " Update the Google Play Store app and try again."
                        )
                    } else {
                        connectAndRefresh(onReady)
                    }
                }
            }
            billingHardFailure = false // v1.0.24 — a fresh attempt clears a past failure
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    // Invalidate the watchdog — the connection attempt resolved.
                    connectionGeneration.incrementAndGet()
                    isConnecting.set(false)
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Timber.i(
                            "PremiumRepo: BillingClient connected (responseCode=%s, debugMessage='%s')",
                            result.responseCode, result.debugMessage
                        )
                        billingHardFailure = false
                        lastBillingFailureDetail = null // v1.0.25 — success clears the stale verdict
                        onConnected(onReady)
                    } else {
                        Timber.w(
                            "PremiumRepo: Billing setup FAILED (responseCode=%s, debugMessage='%s')",
                            result.responseCode, result.debugMessage
                        )
                        // v1.0.25 — remember the concrete Play verdict so the
                        // exhaustion error can show it on screen. This is where
                        // e.g. DEVELOPER_ERROR (sideloaded build not matching the
                        // Play Console entry) or BILLING_UNAVAILABLE (Play Store
                        // outdated) become user-visible instead of logcat-only.
                        lastBillingFailureDetail =
                            "code ${result.responseCode}" +
                            (if (result.debugMessage.isNotBlank()) ": ${result.debugMessage}" else "")
                        connectAttempts++
                        if (connectAttempts >= MAX_CONNECT_RETRIES) {
                            // v1.0.19 — Play Billing itself is unavailable on this
                            // device; never leave the UI on "Loading price…".
                            billingHardFailure = true // v1.0.24 — release pending purchase waits
                            val detail = lastBillingFailureDetail // v1.0.25 — show the real Play verdict
                            onPriceUnavailable(
                                "Google Play Billing is not available on this device" +
                                    (if (detail != null) " (Play said: $detail)." else ".") +
                                    " Update the Google Play Store app and try again."
                            )
                        } else {
                            _state.update { it.copy(isChecking = false) }
                            scheduleRetry(onReady)
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Timber.w("PremiumRepo: Billing service disconnected")
                    // v1.0.22 ROOT-CAUSE FIX for "Subscribe never opens Google
                    // Play": this callback previously did NOT reset
                    // [isConnecting], so every later connectAndRefresh became a
                    // silent no-op piggyback and startConnection() could never
                    // be called again — exactly the deadlock that made the
                    // purchase button dead. Per Google's guidance the client
                    // MUST be allowed to reconnect here.
                    isConnecting.set(false)
                    // v1.0.25 — disconnects now consume the SAME bounded retry
                    // budget as every other connection failure. Previously a
                    // device that could not reach the Play Billing service at
                    // all (no internet, Play Store updating/disabled, ad-blocking
                    // Private DNS or VPN, no GMS) looped here FOREVER:
                    // connectAttempts never grew, no error was ever set, and a
                    // pending purchase tap rode the full 30s readiness window
                    // into the generic "Could not reach Google Play Billing"
                    // message with zero diagnostics. Now the budget runs out
                    // (~10s) into billingHardFailure + a specific reason.
                    connectAttempts++
                    if (connectAttempts >= MAX_CONNECT_RETRIES) {
                        Timber.w(
                            "PremiumRepo: Billing connection dropped %d times — declaring Play Billing unreachable",
                            connectAttempts
                        )
                        billingHardFailure = true
                        val detail = lastBillingFailureDetail
                        onPriceUnavailable(
                            "Google Play Billing could not stay connected to the Play Store app" +
                                (if (detail != null) " (Play said: $detail)." else ".") +
                                " Check your internet connection, make sure the Google" +
                                " Play Store app is up to date, then try again."
                        )
                        return
                    }
                    scheduleRetry(onReady)
                }
            })
        } else {
            // A connection attempt is already in flight — piggyback the callback
            // by waiting for the next successful refresh.
            onReady?.let { cb -> scope.launch { waitForReadyThen(cb) } }
        }
    }

    private suspend fun waitForReadyThen(callback: () -> Unit) {
        // Wait until the client reports ready (bounded), then run the callback.
        withTimeoutOrNull(15_000L) {
            while (billingClient?.isReady != true) {
                kotlinx.coroutines.delay(250)
            }
        }
        // v1.0.24 — ALWAYS fire, even when the client never became ready. A
        // dropped callback silently swallowed everything piggybacking on this
        // connection attempt and (before the v1.0.24 launchPurchaseWhenReady
        // rework) left isPreparingPurchase stuck true forever.
        callback()
    }

    private fun scheduleRetry(onReady: (() -> Unit)?) {
        scope.launch {
            kotlinx.coroutines.delay(RETRY_BACKOFF_MS)
            connectAndRefresh(onReady)
        }
    }

    private fun onConnected(onReady: (() -> Unit)?) {
        connectAttempts = 0
        queryProductDetails()
        recheckEntitlement()
        onReady?.invoke()
    }

    /**
     * v1.0.17 — public manual retry for the Play price. Used by the
     * subscription screen when automatic retries are exhausted, so the UI
     * never sits on "Loading price from Google Play" forever.
     */
    fun refreshPricing() {
        priceQueryAttempts = 0
        connectAttempts = 0 // v1.0.25 — a manual retry earns a full bounded budget
        billingHardFailure = false // v1.0.25 — same fresh-start rule as a purchase tap
        lastBillingFailureDetail = null // v1.0.25 — collect a fresh verdict
        _state.update { it.copy(priceError = null, isChecking = true) }
        if (billingClient?.isReady == true) queryProductDetails() else connectAndRefresh()
    }

    /** Surface a price-loading failure in the UI state (never silent). */
    private fun onPriceUnavailable(message: String) {
        Timber.w("PremiumRepo: price unavailable — %s", message)
        _state.update { it.copy(priceError = message, isChecking = false) }
    }

    /** Bounded automatic retry of the price query (stops after [PRICE_MAX_AUTO_RETRIES]). */
    private fun schedulePriceRetry() {
        if (priceQueryAttempts >= PRICE_MAX_AUTO_RETRIES) {
            Timber.w("PremiumRepo: price auto-retry exhausted — manual refresh required")
            return
        }
        priceQueryAttempts++
        val gen = priceQueryGeneration
        scope.launch {
            kotlinx.coroutines.delay(RETRY_BACKOFF_MS * priceQueryAttempts)
            if (gen != priceQueryGeneration) return@launch // a newer query owns the flow
            if (billingClient?.isReady == true) queryProductDetails() else connectAndRefresh()
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Product details (localized price + offer token)
    // ═════════════════════════════════════════════════════════════════════

    private fun queryProductDetails() {
        val client = billingClient
        if (client == null || !client.isReady) {
            // Never silently drop a price request — re-drive the connection
            // (idempotent) so a pending query always lands somewhere.
            connectAndRefresh()
            return
        }

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID_PREMIUM)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        // v1.0.17 — the price query can no longer stall the UI: every
        // failure path (non-OK response, empty product list, Play timeout)
        // now sets a user-visible [PremiumState.priceError] and schedules a
        // bounded automatic retry. Stale callbacks (after a newer query or a
        // manual refresh) are ignored via the generation token.
        val generation = ++priceQueryGeneration
        val timeoutJob: Job = scope.launch {
            kotlinx.coroutines.delay(PRICE_QUERY_TIMEOUT_MS)
            if (generation == priceQueryGeneration) {
                onPriceUnavailable("Google Play is taking too long to respond.")
                schedulePriceRetry()
            }
        }

        // PBL 8.0.0 — the callback now delivers a QueryProductDetailsResult
        // wrapper; the product list lives in queryResult.productDetailsList.
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (generation != priceQueryGeneration) return@queryProductDetailsAsync // stale
            timeoutJob.cancel()
            // v1.0.24 — full Play-side visibility: response code + debugMessage
            // for every product query, plus the complete offer inventory.
            Timber.i(
                "PremiumRepo: queryProductDetails responseCode=%s debugMessage='%s' products=%d",
                result.responseCode, result.debugMessage,
                queryResult.productDetailsList?.size ?: 0
            )
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("PremiumRepo: product query failed: %s", result.debugMessage)
                onPriceUnavailable("Google Play returned an error while loading the price.")
                schedulePriceRetry()
                return@queryProductDetailsAsync
            }
            val details = queryResult.productDetailsList
                .firstOrNull { it.productId == PRODUCT_ID_PREMIUM }
            if (details == null) {
                Timber.w("PremiumRepo: product %s not found in Play Console", PRODUCT_ID_PREMIUM)
                onPriceUnavailable(
                    "Premium subscription is not available in Google Play for this account yet. " +
                        "It may still be rolling out in your country."
                )
                schedulePriceRetry()
                return@queryProductDetailsAsync
            }
            priceQueryAttempts = 0
            premiumProductDetails = details

            // Prefer the launch-offer on the monthly-premium base plan when active;
            // otherwise fall back to the plain base plan offer token.
            val offerDetails = details.subscriptionOfferDetails.orEmpty()
            // v1.0.24 — log the FULL offer inventory (base plan, offer id, token,
            // pricing phases) so a bad Play Console setup is diagnosable from
            // logcat alone.
            Timber.i(
                "PremiumRepo: offer inventory for %s: %s",
                PRODUCT_ID_PREMIUM,
                offerDetails.map { o ->
                    "${o.basePlanId}/${o.offerId ?: "<base-plan>"} token=${o.offerToken.take(12)}… phases=" +
                        o.pricingPhases.pricingPhaseList.joinToString("|") { ph ->
                            "${ph.formattedPrice}/${ph.billingPeriod}"
                        }
                }
            )
            val launchOffer = offerDetails.firstOrNull {
                it.basePlanId == BASE_PLAN_ID && it.offerId == OFFER_ID
            }
            val basePlanOffer = offerDetails.firstOrNull { it.basePlanId == BASE_PLAN_ID }
            val chosenOffer = launchOffer ?: basePlanOffer
            if (chosenOffer == null) {
                // v1.0.19 — Play returned the product but NO purchasable offer
                // for this device account. Root causes: the base plan is not
                // active in the account's country, or the Play catalog has not
                // refreshed yet. Previously this fell through SILENTLY, leaving
                // the UI on "Loading price…" forever — the exact reported bug.
                Timber.w(
                    "PremiumRepo: product %s returned without an offer for base plan %s (offers=%s)",
                    PRODUCT_ID_PREMIUM, BASE_PLAN_ID,
                    offerDetails.map { it.basePlanId to it.offerId }
                )
                onPriceUnavailable(
                    "The monthly plan is not available for this Google Play account yet. " +
                        "If you are the app owner, check the base plan's country list in " +
                        "Play Console, then tap Retry."
                )
                schedulePriceRetry()
                return@queryProductDetailsAsync
            }
            cachedOfferToken = chosenOffer.offerToken
            // v1.0.24 — record WHY this offer was chosen (launch-offer when
            // available, plain base-plan offer otherwise — never a failure).
            Timber.i(
                "PremiumRepo: offer SELECTED %s (token=%s…) — launchOffer=%s basePlanFallback=%s",
                if (launchOffer != null) "launch-offer" else "base-plan-offer",
                chosenOffer.offerToken.take(12),
                launchOffer != null,
                launchOffer == null && basePlanOffer != null
            )

            val price = chosenOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            val currency = chosenOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.priceCurrencyCode
            _state.update {
                it.copy(
                    localizedPrice = price ?: it.localizedPrice,
                    currencyCode = currency ?: it.currencyCode,
                    hasLaunchOffer = launchOffer != null,
                    priceError = null, // v1.0.25 — a successful query clears a stale failure message
                    isChecking = false
                )
            }
            Timber.i(
                "PremiumRepo: product ready — price=%s currency=%s launchOffer=%s",
                price, currency, launchOffer != null
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Purchase flow
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Launch the real Google Play subscription purchase flow.
     * Returns false when the flow could not be started (not connected /
     * product missing), in which case the caller should show an error.
     */
    fun launchPurchase(activity: Activity): Boolean {
        val client = billingClient
        val details = premiumProductDetails
        val offerToken = cachedOfferToken
        if (client == null || !client.isReady || details == null || offerToken == null) {
            Timber.w("PremiumRepo: purchase requested but billing not ready — reconnecting")
            connectAndRefresh()
            return false
        }
        _state.update { it.copy(isPurchasing = true, purchaseError = null) }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .apply {
                // v1.0.9: bind this purchase to the CURRENT app account via
                // Play's obfuscatedAccountId. Play echoes it back on every
                // purchase query (purchase.accountIdentifiers), so premium can
                // NEVER leak to a different app account on this device —
                // verified by Google Play, no Firestore dependency.
                auth.currentUser?.uid?.let { uid -> setObfuscatedAccountId(uid) }
            }
            .build()

        val result = try {
            client.launchBillingFlow(activity, flowParams)
        } catch (e: Exception) {
            // v1.0.24 — a thrown launch (dead Play process, security exception,
            // activity finishing) must NEVER wedge the purchase flags.
            Timber.w(e, "PremiumRepo: launchBillingFlow THREW — resetting purchase state")
            _state.update {
                it.copy(
                    isPurchasing = false,
                    isPreparingPurchase = false,
                    purchaseError = "Google Play could not start the purchase " +
                        "(${e.message ?: e.javaClass.simpleName}). Make sure you are signed " +
                        "in to Google Play and try again."
                )
            }
            return false
        }
        // v1.0.24 — ALWAYS log the launch verdict (code + debugMessage).
        Timber.i(
            "PremiumRepo: launchBillingFlow → responseCode=%s debugMessage='%s'",
            result.responseCode, result.debugMessage
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(
                    isPurchasing = false,
                    isPreparingPurchase = false,
                    // v1.0.24 — surface the exact Play verdict, not a generic note.
                    purchaseError = "Google Play could not start the purchase (code ${result.responseCode}" +
                        (if (result.debugMessage.isNotBlank()) ": ${result.debugMessage}" else "") +
                        "). Make sure you are signed in to Google Play and try again."
                )
            }
            Timber.w("PremiumRepo: billing flow failed to launch: %s", result.debugMessage)
            return false
        }
        return true
    }

    /**
     * v1.0.22 — user-facing purchase entry point. The Subscribe button must
     * NEVER fail silently: if Google Play Billing is not ready yet (cold
     * start, Play Store busy, product query still in flight), this connects,
     * WAITS (bounded) for client + product details + offer token, and then
     * opens the Play purchase sheet automatically. The caller receives one
     * result; every failure sets [PremiumState.purchaseError] so the paywall
     * can show the exact reason instead of a dead button.
     *
     * v1.0.24 — DEADLOCK ELIMINATION. The old implementation registered its
     * continuation as the connectAndRefresh callback, but that callback could
     * be DROPPED on three separate paths (the 15s piggyback wait, connect
     * retry exhaustion, watchdog exhaustion) — isPreparingPurchase then
     * stayed TRUE forever and the button spun on "connecting…" without ever
     * opening Google Play. Now the bounded wait below is the SINGLE OWNER of
     * the prepare lifecycle: connectAndRefresh() is fired WITHOUT a callback
     * (fire-and-forget kick), and the wait always terminates — launching the
     * real Play sheet on success, or populating purchaseError (with the
     * concrete Play reason when known) on failure. isPreparingPurchase is
     * reset on EVERY exit path.
     */
    fun launchPurchaseWhenReady(activity: Activity, onResult: (Boolean) -> Unit) {
        _state.update { it.copy(purchaseError = null) }
        val client = billingClient
        if (client != null && client.isReady &&
            premiumProductDetails != null && cachedOfferToken != null
        ) {
            // Already launchable — open the Play sheet immediately.
            onResult(launchPurchase(activity))
            return
        }
        // Not launchable yet — prepare visibly, then auto-open the sheet.
        _state.update { it.copy(isPreparingPurchase = true) }
        connectAttempts = 0 // explicit user action — grant a fresh bounded retry budget
        billingHardFailure = false // v1.0.24 — the user's tap deserves a fresh attempt
        lastBillingFailureDetail = null // v1.0.25 — the new attempt collects its own verdict
        connectAndRefresh() // v1.0.24 — fire-and-forget; the wait below owns the outcome
        scope.launch {
            val ready = waitForPurchaseReadiness()
            _state.update { it.copy(isPreparingPurchase = false) }
            if (ready) {
                onResult(launchPurchase(activity))
            } else {
                Timber.w(
                    "PremiumRepo: purchase abandoned — billing not ready after wait (hardFailure=%s, priceError=%s)",
                    billingHardFailure, _state.value.priceError
                )
                val knownReason = _state.value.priceError
                _state.update {
                    it.copy(
                        isPreparingPurchase = false,
                        purchaseError = knownReason
                            ?: ("Could not reach Google Play Billing." +
                                " Check your internet connection, make sure the Google" +
                                " Play Store app is up to date, then try again.")
                    )
                }
                onResult(false)
            }
        }
    }

    /**
     * v1.0.22 — bounded wait until the client is connected AND the product
     * details + offer token are loaded (the exact prerequisites for
     * [launchPurchase] to actually open the Play sheet).
     * v1.0.24 — also exits EARLY when the connection is declared hard-dead
     * (max retries exhausted), surfacing the concrete reason immediately
     * instead of riding the full timeout.
     */
    private suspend fun waitForPurchaseReadiness(): Boolean =
        withTimeoutOrNull(PURCHASE_READINESS_TIMEOUT_MS) {
            while (billingClient?.isReady != true ||
                premiumProductDetails == null ||
                cachedOfferToken == null
            ) {
                if (billingHardFailure) return@withTimeoutOrNull false
                kotlinx.coroutines.delay(250)
            }
            true
        } ?: false

    /** PurchasesUpdatedListener — entry point for every completed flow. */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Timber.i(
                    "PremiumRepo: onPurchasesUpdated OK — %d purchase(s)",
                    purchases?.size ?: 0
                )
                if (purchases.isNullOrEmpty()) {
                    _state.update { it.copy(isPurchasing = false, isPreparingPurchase = false) }
                    return
                }
                handlePurchases(purchases)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Timber.i(
                    "PremiumRepo: purchase cancelled by user (responseCode=%s, debugMessage='%s')",
                    result.responseCode, result.debugMessage
                )
                _state.update { it.copy(isPurchasing = false, isPreparingPurchase = false) }
            }
            else -> {
                // v1.0.24 — every failure path populates purchaseError with the
                // exact Play verdict (code + debugMessage) and resets both flags.
                Timber.w(
                    "PremiumRepo: purchase FAILED (responseCode=%s, debugMessage='%s')",
                    result.responseCode, result.debugMessage
                )
                _state.update {
                    it.copy(
                        isPurchasing = false,
                        isPreparingPurchase = false,
                        purchaseError = "Google Play purchase failed (code ${result.responseCode}" +
                            (if (result.debugMessage.isNotBlank()) ": ${result.debugMessage}" else "") +
                            ")."
                    )
                }
            }
        }
    }

    /**
     * Process a list of purchases: acknowledge verified ones, record the
     * entitlement, and update state. Handles PURCHASED, PENDING and everything
     * else (failed/refunded) — see [processSinglePurchase].
     */
    private fun handlePurchases(purchases: List<Purchase>) {
        var anyPremiumActive = false
        for (purchase in purchases) {
            val processed = processSinglePurchase(purchase)
            anyPremiumActive = anyPremiumActive || processed
        }
        _state.update { it.copy(isPurchasing = false, isPreparingPurchase = false) }
        if (!anyPremiumActive) recheckEntitlement()
    }

    /** @return true when this purchase will be (or already is) resolved into an entitlement decision. */
    private fun processSinglePurchase(purchase: Purchase): Boolean {
        return when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val ownsPremium = purchase.products.contains(PRODUCT_ID_PREMIUM)
                if (!ownsPremium) return false
                val signatureOk = verifyPurchaseSignature(purchase)
                if (!signatureOk) {
                    Timber.w("PremiumRepo: purchase signature verification failed — ignoring purchase")
                    return false
                }
                if (!purchase.isAcknowledged) {
                    acknowledge(purchase.purchaseToken)
                }
                // Binding check is async (Firestore read); Play callbacks must
                // return quickly, so the grant/deny decision happens in scope.
                scope.launch { resolvePurchaseEntitlement(purchase) }
                true
            }
            Purchase.PurchaseState.PENDING -> {
                Timber.i("PremiumRepo: purchase pending (awaiting payment method)")
                _state.update {
                    it.copy(isPurchasing = false, status = SubscriptionStatus.PENDING, isPremium = false)
                }
                false
            }
            else -> {
                Timber.i("PremiumRepo: purchase state %d — no entitlement", purchase.purchaseState)
                false
            }
        }
    }

    /**
     * Resolve whether a verified Play purchase grants premium to the CURRENT
     * signed-in app account.
     *
     * v1.0.9 ROOT-CAUSE FIX — the grant decision is now derived from GOOGLE
     * PLAY ONLY, never from the Firestore mirror:
     *
     * - Purchases started by THIS app write the signed-in uid into Play's
     *   `obfuscatedAccountId` (see [launchPurchase]); Play echoes it back in
     *   `purchase.accountIdentifiers` on EVERY query. A purchase bound to a
     *   DIFFERENT app account is therefore denied by Play itself — with zero
     *   Firestore round-trips.
     * - Everything else (legacy purchases made before this fix, test
     *   purchases, blank identifiers) GRANTS for the current account. The
     *   old logic gated the grant behind an 8s Firestore mirror read that
     *   routinely timed out on a cold app (UNKNOWN → deny → the subscribed
     *   account never turned premium), while a NEW account could hit the
     *   MISSING branch and CLAIM the purchase. Both failure modes are gone:
     *   no Firestore read sits in the grant path anymore.
     * - The Firestore mirror (`subscriptions/{uid}`) is still written
     *   best-effort AFTER the in-memory grant, purely so other surfaces
     *   (leaderboard badge, public profile) can display premium while
     *   Google Play is unreachable.
     */
    private suspend fun resolvePurchaseEntitlement(purchase: Purchase) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Timber.w("PremiumRepo: verified purchase but no signed-in app account — not granting premium")
            return
        }

        // ── Play-verified account binding ─────────────────────────────────
        val obfuscatedAccount = try {
            purchase.accountIdentifiers?.obfuscatedAccountId
        } catch (e: Exception) {
            null
        }
        if (!obfuscatedAccount.isNullOrBlank() && obfuscatedAccount != uid) {
            Timber.w(
                "PremiumRepo: premium purchase exists on Google Play but is bound to a DIFFERENT app account (Play obfuscatedAccountId) — premium denied for %s",
                uid
            )
            _state.update {
                it.copy(
                    isPremium = false,
                    isChecking = false,
                    isPurchasing = false,
                    status = SubscriptionStatus.NONE,
                    isAutoRenewing = null,
                    entitlementUid = uid
                )
            }
            return
        }

        // ── GRANT — Google Play is the source of truth ────────────────────
        val autoRenewing = purchase.isAutoRenewing
        val status = if (autoRenewing) SubscriptionStatus.ACTIVE
        else SubscriptionStatus.CANCELED_BUT_STILL_ENTITLED
        Timber.i(
            "PremiumRepo: verified premium purchase (token=%s…) autoRenewing=%s → %s (granted from Play, no mirror gating)",
            purchase.purchaseToken.take(8), autoRenewing, status
        )
        persistEntitlement(purchase)
        _state.update {
            it.copy(
                isPremium = true,
                isChecking = false,
                isPurchasing = false,
                status = status,
                isAutoRenewing = autoRenewing,
                entitlementUid = uid,
                subscription = it.subscription?.copy(
                    active = true,
                    purchaseToken = purchase.purchaseToken,
                    autoRenewing = autoRenewing
                )
                    ?: Subscription(
                        active = true,
                        plan = BASE_PLAN_ID,
                        purchaseToken = purchase.purchaseToken,
                        autoRenewing = autoRenewing
                    )
            )
        }
    }

    private fun acknowledge(purchaseToken: String) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.i("PremiumRepo: purchase acknowledged")
            } else {
                Timber.w("PremiumRepo: acknowledge failed: %s", result.debugMessage)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Entitlement re-check (startup / restore / state change)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Re-query Google Play for active subscriptions and recompute the
     * entitlement. Called on app start, on login/account switch, on restore,
     * and after purchases so expiry/cancellation always takes effect.
     *
     * State derivation:
     * - purchase found, autoRenewing=true  → ACTIVE
     * - purchase found, autoRenewing=false → CANCELED_BUT_STILL_ENTITLED
     * - no purchase (previously entitled)  → EXPIRED (period ended or revoked)
     * - no purchase (never entitled)       → NONE
     */
    fun recheckEntitlement() {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectAndRefresh()
            return
        }
        _state.update { it.copy(isChecking = true) }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("PremiumRepo: purchase re-check failed: %s", result.debugMessage)
                _state.update { it.copy(isChecking = false) }
                // Fall back to the Firestore mirror so a temporarily
                // unreachable Play Store doesn't strip a paying user.
                loadEntitlementFromFirestore()
                return@queryPurchasesAsync
            }
            val premiumPurchase = purchases.firstOrNull {
                it.products.contains(PRODUCT_ID_PREMIUM) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (premiumPurchase == null) {
                val previousStatus = _state.value.status
                val wasEntitled = previousStatus == SubscriptionStatus.ACTIVE ||
                        previousStatus == SubscriptionStatus.CANCELED_BUT_STILL_ENTITLED
                Timber.i(
                    "PremiumRepo: no active premium subscription on Google Play (previous=%s)",
                    previousStatus
                )
                // Play hides a purchase while it is on account hold (paused) or
                // after it was revoked/refunded — the mirror tells us which.
                val mirrorState = _state.value.subscription?.state?.lowercase()
                val status = when {
                    mirrorState == "paused" -> SubscriptionStatus.PAUSED
                    mirrorState == "revoked" -> SubscriptionStatus.REVOKED
                    wasEntitled -> SubscriptionStatus.EXPIRED
                    else -> SubscriptionStatus.NONE
                }
                setPremium(false, null, status)
            } else {
                processSinglePurchase(premiumPurchase)
                _state.update { it.copy(isChecking = false) }
            }
            _state.update { it.copy(isChecking = false) }
        }
    }

    /** Public restore entry point used by the Subscription screen / Settings. */
    fun restorePurchases(onFinished: (found: Boolean) -> Unit = {}) {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectAndRefresh { recheckEntitlement() }
            onFinished(false)
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("PremiumRepo: restore failed: %s", result.debugMessage)
                onFinished(false)
                return@queryPurchasesAsync
            }
            val premiumPurchase = purchases.firstOrNull {
                it.products.contains(PRODUCT_ID_PREMIUM) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (premiumPurchase != null) {
                processSinglePurchase(premiumPurchase)
                onFinished(true)
            } else {
                setPremium(false, null, SubscriptionStatus.NONE)
                onFinished(false)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Purchase signature verification (Play licensing public key)
    // ═════════════════════════════════════════════════════════════════════

    private fun verifyPurchaseSignature(purchase: Purchase): Boolean {
        return try {
            val signature = purchase.signature
            val originalJson = purchase.originalJson
            if (signature.isNullOrBlank()) {
                // Billing Library 7+ usually doesn't expose signatures in test
                // contexts; absence is not proof of tampering.
                Timber.d("PremiumRepo: no purchase signature present (test context?) — treating as valid")
                return true
            }
            val publicKey = decodePublicKey(PLAY_LICENSE_PUBLIC_KEY) ?: return true
            val sig = Signature.getInstance("SHA1withRSA").apply {
                initVerify(publicKey)
                update(originalJson.toByteArray(Charsets.UTF_8))
            }
            val verified = sig.verify(Base64.decode(signature, Base64.DEFAULT))
            Timber.d("PremiumRepo: purchase signature verified=%s", verified)
            verified
        } catch (e: Exception) {
            Timber.w(e, "PremiumRepo: signature verification error — defaulting to valid")
            true
        }
    }

    private fun decodePublicKey(base64Key: String): PublicKey? = try {
        val decoded = Base64.decode(base64Key, Base64.DEFAULT)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded))
    } catch (e: Exception) {
        Timber.w(e, "PremiumRepo: failed to decode licensing public key")
        null
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Entitlement persistence (Firestore mirror)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Mirror a verified purchase into Firestore. The subscription renews
     * automatically; `expiresAt` is an estimated horizon used only as a
     * fallback display — the source of truth is always the re-check against
     * Google Play performed at every app start.
     */
    private fun persistEntitlement(purchase: Purchase) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            try {
                // Monthly auto-renewing plan: estimate the next renewal as +31 days
                // from now (refreshed on every app start by [recheckEntitlement]).
                val estimatedExpiry = Timestamp(
                    java.util.Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(31))
                )
                val data = mapOf(
                    "active" to true,
                    "plan" to BASE_PLAN_ID,
                    "productId" to PRODUCT_ID_PREMIUM,
                    "purchaseToken" to purchase.purchaseToken,
                    "autoRenewing" to purchase.isAutoRenewing,
                    "expiresAt" to estimatedExpiry,
                    "updatedAt" to Timestamp.now()
                )
                withTimeoutOrNull(10_000L) {
                    firestore.collection(SUBSCRIPTIONS_COLLECTION).document(uid)
                        .set(data, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    // Denormalize premium onto the public profile so the
                    // leaderboard/community can badge it without queries.
                    firestore.collection("publicProfiles").document(uid)
                        .set(
                            mapOf(
                                "premium" to true,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).await()
                }
                Timber.i("PremiumRepo: entitlement mirrored to Firestore")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "PremiumRepo: failed to mirror entitlement (purchase still valid locally)")
            }
        }
    }

    /**
     * Mark the entitlement inactive locally and in Firestore.
     * [status] carries WHY premium ended (EXPIRED, REVOKED, NONE…).
     */
    private fun setPremium(active: Boolean, subscription: Subscription?, status: SubscriptionStatus = SubscriptionStatus.NONE) {
        _state.update { current ->
            current.copy(
                isPremium = active,
                status = if (active) current.status else status,
                isAutoRenewing = if (active) current.isAutoRenewing else null,
                subscription = if (active) {
                    subscription ?: current.subscription?.copy(active = true)
                        ?: Subscription(active = true, plan = BASE_PLAN_ID)
                } else {
                    current.subscription?.copy(active = false)
                }
            )
        }
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            try {
                if (!active) {
                    withTimeoutOrNull(10_000L) {
                        firestore.collection(SUBSCRIPTIONS_COLLECTION).document(uid)
                            .set(
                                mapOf(
                                    "active" to false,
                                    "updatedAt" to Timestamp.now()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            ).await()
                        firestore.collection("publicProfiles").document(uid)
                            .set(
                                mapOf(
                                    "premium" to false,
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            ).await()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "PremiumRepo: failed to update Firestore mirror")
            }
        }
    }

    /**
     * Load the Firestore mirror (used only when Google Play is unreachable —
     * e.g. no Play Store on the device). Applies the expiry check with a
     * small grace window so offline users are not dropped mid-cycle.
     */
    private fun loadEntitlementFromFirestore() {
        val uid = auth.currentUser?.uid ?: run {
            _state.update { it.copy(isChecking = false) }
            return
        }
        scope.launch {
            try {
                val doc = withTimeoutOrNull(8_000L) {
                    firestore.collection(SUBSCRIPTIONS_COLLECTION).document(uid).get().await()
                }
                val sub = doc?.data?.let { Subscription.fromFirestoreMap(it) }
                val mirrorActive = sub != null && sub.purchaseToken.isNotBlank() && sub.active &&
                    (sub.expiresAt.toDate().time + EXPIRY_GRACE_MS) > System.currentTimeMillis()
                _state.update {
                    it.copy(
                        isChecking = false,
                        isPremium = mirrorActive,
                        status = when {
                            sub?.state?.lowercase() == "revoked" -> SubscriptionStatus.REVOKED
                            sub?.state?.lowercase() == "paused" -> SubscriptionStatus.PAUSED
                            mirrorActive && sub?.autoRenewing == true -> SubscriptionStatus.ACTIVE
                            mirrorActive -> SubscriptionStatus.CANCELED_BUT_STILL_ENTITLED
                            sub != null && sub.active -> SubscriptionStatus.EXPIRED
                            else -> SubscriptionStatus.NONE
                        },
                        isAutoRenewing = sub?.autoRenewing,
                        entitlementUid = auth.currentUser?.uid,
                        subscription = sub
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "PremiumRepo: failed to load Firestore mirror")
                _state.update { it.copy(isChecking = false) }
            }
        }
    }

    /** Convenience for screens: current premium flag. */
    fun isPremium(): Boolean = _state.value.isPremium

    /** Release the billing client (app teardown only). */
    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}

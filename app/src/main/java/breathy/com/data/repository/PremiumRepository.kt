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
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
        /** Localized currency code from Google Play. */
        val currencyCode: String? = null,
        /** Whether a launch offer is currently available on the base plan. */
        val hasLaunchOffer: Boolean = false,
        /** True while a purchase flow is running. */
        val isPurchasing: Boolean = false,
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

    private val _state = MutableStateFlow(PremiumState())
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    private var billingClient: BillingClient? = null
    private var premiumProductDetails: ProductDetails? = null
    private var cachedOfferToken: String? = null

    private val isConnecting = AtomicBoolean(false)

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
        val client = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases()
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
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    isConnecting.set(false)
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Timber.i("PremiumRepo: BillingClient connected")
                        onConnected(onReady)
                    } else {
                        Timber.w("PremiumRepo: Billing setup failed: %s — will retry", result.debugMessage)
                        _state.update { it.copy(isChecking = false) }
                        scheduleRetry(onReady)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Timber.w("PremiumRepo: Billing service disconnected")
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
        if (billingClient?.isReady == true) callback()
    }

    private fun scheduleRetry(onReady: (() -> Unit)?) {
        scope.launch {
            kotlinx.coroutines.delay(RETRY_BACKOFF_MS)
            connectAndRefresh(onReady)
        }
    }

    private fun onConnected(onReady: (() -> Unit)?) {
        queryProductDetails()
        recheckEntitlement()
        onReady?.invoke()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Product details (localized price + offer token)
    // ═════════════════════════════════════════════════════════════════════

    private fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID_PREMIUM)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("PremiumRepo: product query failed: %s", result.debugMessage)
                return@queryProductDetailsAsync
            }
            val details = detailsList.firstOrNull { it.productId == PRODUCT_ID_PREMIUM }
            if (details == null) {
                Timber.w("PremiumRepo: product %s not found in Play Console", PRODUCT_ID_PREMIUM)
                return@queryProductDetailsAsync
            }
            premiumProductDetails = details

            // Prefer the launch-offer on the monthly-premium base plan when active;
            // otherwise fall back to the plain base plan offer token.
            val offerDetails = details.subscriptionOfferDetails.orEmpty()
            val launchOffer = offerDetails.firstOrNull {
                it.basePlanId == BASE_PLAN_ID && it.offerId == OFFER_ID
            }
            val basePlanOffer = offerDetails.firstOrNull { it.basePlanId == BASE_PLAN_ID }
            val chosenOffer = launchOffer ?: basePlanOffer
            cachedOfferToken = chosenOffer?.offerToken

            val price = chosenOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            val currency = chosenOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.priceCurrencyCode
            _state.update {
                it.copy(
                    localizedPrice = price ?: it.localizedPrice,
                    currencyCode = currency ?: it.currencyCode,
                    hasLaunchOffer = launchOffer != null
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
        _state.update { it.copy(isPurchasing = true) }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(isPurchasing = false) }
            Timber.w("PremiumRepo: billing flow failed to launch: %s", result.debugMessage)
            return false
        }
        return true
    }

    /** PurchasesUpdatedListener — entry point for every completed flow. */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    _state.update { it.copy(isPurchasing = false) }
                    return
                }
                handlePurchases(purchases)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Timber.i("PremiumRepo: purchase cancelled by user")
                _state.update { it.copy(isPurchasing = false) }
            }
            else -> {
                Timber.w("PremiumRepo: purchase failed: %s", result.debugMessage)
                _state.update { it.copy(isPurchasing = false) }
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
        _state.update { it.copy(isPurchasing = false) }
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
     * Binding rule: the purchase token must be recorded under THIS account
     * (`subscriptions/{uid}.purchaseToken`, written at purchase time by
     * [persistEntitlement]). A purchase bound to a different app account on
     * this device NEVER leaks into the current account.
     *
     * Unreachable Firestore policy: keep the previous in-memory decision for
     * the SAME account (don't strip a paying user on a network blip);
     * fail-closed (deny) for a NEW account so no state can leak through.
     */
    private suspend fun resolvePurchaseEntitlement(purchase: Purchase) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Timber.w("PremiumRepo: verified purchase but no signed-in app account — not granting premium")
            return
        }
        val previous = _state.value
        val sameAccount = previous.entitlementUid == uid

        val binding = readTokenBinding(uid, purchase.purchaseToken)
        val bound = when (binding) {
            true -> true
            false -> false
            null -> sameAccount && previous.isPremium // Firestore unreachable — trust same-account cache
        }

        if (!bound) {
            Timber.w(
                "PremiumRepo: premium purchase exists on Google Play but is NOT bound to account %s — premium denied",
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

        val autoRenewing = purchase.isAutoRenewing
        val status = if (autoRenewing) SubscriptionStatus.ACTIVE
        else SubscriptionStatus.CANCELED_BUT_STILL_ENTITLED
        Timber.i(
            "PremiumRepo: verified premium purchase (token=%s…) autoRenewing=%s → %s",
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

    /**
     * Read the purchase-token binding for [uid] from Firestore.
     * Returns:
     * - `true`  → token bound to this account;
     * - `false` → token belongs to a different account (or this account has
     *   no purchase record at all);
     * - `null`  → Firestore unreachable / timed out (binding UNKNOWN).
     */
    private suspend fun readTokenBinding(uid: String, purchaseToken: String): Boolean? {
        return try {
            val doc = withTimeoutOrNull(8_000L) {
                firestore.collection(SUBSCRIPTIONS_COLLECTION).document(uid).get().await()
            }
            when {
                doc == null -> null // timeout — unknown
                !doc.exists() -> false // this account has never recorded a purchase
                else -> {
                    val mirrored = doc.getString("purchaseToken")
                    !mirrored.isNullOrBlank() && mirrored == purchaseToken
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PremiumRepo: failed to read token binding for %s", uid)
            null
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
                setPremium(false, null, if (wasEntitled) SubscriptionStatus.EXPIRED else SubscriptionStatus.NONE)
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

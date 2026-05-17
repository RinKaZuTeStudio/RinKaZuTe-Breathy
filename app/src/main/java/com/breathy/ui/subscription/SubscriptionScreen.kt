@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breathy.ui.subscription

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.breathy.BreathyApplication
import com.breathy.data.models.Subscription
import com.breathy.data.repository.AuthRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.SemanticError
import com.breathy.ui.theme.SemanticSuccess
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.foundation.background

// ═══════════════════════════════════════════════════════════════════════════════
//  SubscriptionScreen — "Support me" one-time purchase + Play Billing
// ═══════════════════════════════════════════════════════════════════════════════

private const val PRODUCT_ID_SUPPORTER = "breathy_supporter"

@Composable
fun SubscriptionScreen(
    onBack: () -> Unit = {},
    viewModel: SubscriptionViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        viewModel(factory = SubscriptionViewModelFactory(
            auth = app.appModule.firebaseAuth,
            firestore = app.appModule.firestore,
            authRepository = app.appModule.authRepository
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Staggered entrance
    var headerVisible by remember { mutableStateOf(false) }
    var benefitsVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        headerVisible = true
        delay(200)
        benefitsVisible = true
        delay(200)
        buttonVisible = true
    }

    DisposableEffect(Unit) {
        Timber.d("SubscriptionScreen: composed")
        onDispose { Timber.d("SubscriptionScreen: disposed") }
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Support Breathy",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgSurface,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Header with Heart Icon ────────────────────────────────────
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(tween(400)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(400)
                )
            ) {
                if (uiState.isSubscribed) {
                    ThankYouSection()
                } else {
                    SupportHeaderSection()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Benefits List ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = benefitsVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(400)
                ) + fadeIn(tween(400))
            ) {
                BenefitsList(benefits = uiState.benefits)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Purchase Button / Status ──────────────────────────────────
            AnimatedVisibility(
                visible = buttonVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(400)
                ) + fadeIn(tween(400))
            ) {
                if (uiState.isSubscribed) {
                    AlreadySubscribedSection(
                        subscription = uiState.subscription
                    )
                } else {
                    PurchaseSection(
                        priceText = uiState.priceText,
                        isPurchasing = uiState.isPurchasing,
                        onPurchase = {
                            val activity = context as? Activity
                            if (activity != null) {
                                viewModel.launchBillingFlow(activity)
                            }
                        },
                        errorMessage = uiState.errorMessage
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Restore Purchases ─────────────────────────────────────────
            RestorePurchasesSection(
                onRestore = { viewModel.restorePurchases() },
                isRestoring = uiState.isRestoring
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Loading State ────────────────────────────────────────────────────
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Support Header — Hero section for non-subscribers
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SupportHeaderSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "heart_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_glow_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            // Glow effect
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentPrimary.copy(alpha = glowAlpha),
                            AccentPrimary.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = size.minDimension / 2
                    ),
                    radius = size.minDimension / 2,
                    center = center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolunteerActivism,
                    contentDescription = "Support",
                    tint = AccentPrimary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Support Breathy",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Help us keep helping others quit smoking",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Thank You Section — Shown to existing supporters
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThankYouSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "star_glow")
    val starScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Supporter",
                tint = AccentPrimary,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = starScale; scaleY = starScale }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Thank You! ✨",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You're a Breathy Supporter!\nYour support helps thousands of people quit smoking.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Benefits List — What the supporter gets
// ═══════════════════════════════════════════════════════════════════════════════

data class SupporterBenefit(
    val icon: String,
    val title: String,
    val description: String
)

@Composable
private fun BenefitsList(benefits: List<SupporterBenefit>) {
    Column {
        Text(
            text = "What you get",
            style = MaterialTheme.typography.titleMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        benefits.forEachIndexed { index, benefit ->
            BenefitRow(
                benefit = benefit,
                modifier = Modifier.fillMaxWidth()
            )
            if (index < benefits.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BenefitRow(
    benefit: SupporterBenefit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .then(
                        Modifier.background(
                            color = AccentPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = benefit.icon, fontSize = 20.sp)
            }

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = benefit.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = benefit.description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            // Checkmark
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Included",
                tint = AccentPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Purchase Section — Buy button with Play Billing
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PurchaseSection(
    priceText: String,
    isPurchasing: Boolean,
    onPurchase: () -> Unit,
    errorMessage: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Price display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "One-time purchase",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No subscriptions, no recurring charges",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextDisabled,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buy button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AccentPrimary),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp),
            onClick = { if (!isPurchasing) onPurchase() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BgPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Processing...",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = BgPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VolunteerActivism,
                        contentDescription = null,
                        tint = BgPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Support for $priceText",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = BgPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = SemanticError,
                    fontSize = 12.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Already Subscribed Section
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AlreadySubscribedSection(subscription: Subscription?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Active",
                tint = SemanticSuccess,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "Active Supporter",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = if (subscription?.plan?.isNotBlank() == true) "Plan: ${subscription.plan}"
                    else "One-time purchase",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Restore Purchases Section
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RestorePurchasesSection(
    onRestore: () -> Unit,
    isRestoring: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onRestore,
            enabled = !isRestoring,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isRestoring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AccentPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AccentPrimary
                )
                Spacer(modifier = Modifier.width(8.dp)
                )
            }
            Text(
                text = "Restore Purchases",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AccentPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class SubscriptionUiState(
    val isSubscribed: Boolean = false,
    val subscription: Subscription? = null,
    val priceText: String = "$1.00",
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val benefits: List<SupporterBenefit> = listOf(
        SupporterBenefit("🚫", "Ad-Free Experience", "No interruptions on your quit journey"),
        SupporterBenefit("⭐", "Supporter Badge", "Show your support on your profile"),
        SupporterBenefit("🤖", "Priority AI Coach", "Faster responses from your AI companion"),
        SupporterBenefit("💚", "Support the Mission", "Help others quit smoking worldwide")
    )
)

class SubscriptionViewModel(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    // Billing client
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    private val uid: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    init {
        loadSubscriptionStatus()
        initBillingClient()
    }

    private fun loadSubscriptionStatus() {
        viewModelScope.launch {
            try {
                val doc = withTimeoutOrNull(10_000L) {
                    firestore.collection("subscriptions").document(uid).get().await()
                }
                if (doc != null && doc.exists()) {
                    val sub = Subscription.fromFirestoreMap(doc.data ?: emptyMap())
                    _uiState.update {
                        it.copy(
                            isSubscribed = sub.isActive(),
                            subscription = sub,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load subscription status")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun initBillingClient() {
        val context = authRepository.getCurrentUser()?.let {
            // We need application context — use a workaround
            null
        }

        // BillingClient requires an Activity context, so we'll initialize lazily
        // when the Activity is available via launchBillingFlow
        Timber.i("Billing client will be initialized on first purchase attempt")
    }

    /**
     * Initialize BillingClient with the given Activity context.
     * Called from the composable when launching the billing flow.
     */
    private fun ensureBillingClient(activity: Activity) {
        if (billingClient != null && billingClient!!.isReady) return

        val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
            handlePurchaseResult(billingResult, purchases)
        }

        billingClient = BillingClient.newBuilder(activity.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.i("BillingClient ready")
                    queryProductDetails()
                } else {
                    Timber.e("BillingClient setup failed: ${billingResult.debugMessage}")
                    _uiState.update {
                        it.copy(errorMessage = "Billing setup failed: ${billingResult.debugMessage}")
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.w("BillingClient disconnected")
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_SUPPORTER)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = productDetailsList.firstOrNull()
                productDetails?.let { details ->
                    val offerToken = "" // Not needed for one-time purchases in Billing 6.x
                    val priceAmount = try { details.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0 } catch (_: Exception) { 0 }
                    val priceCurrency = try { details.oneTimePurchaseOfferDetails?.priceCurrencyCode ?: "USD" } catch (_: Exception) { "USD" }
                    val formattedPrice = try { details.oneTimePurchaseOfferDetails?.formattedPrice ?: "$1.00" } catch (_: Exception) { "$1.00" }

                    _uiState.update { it.copy(priceText = formattedPrice) }
                    Timber.i("Product details loaded: $formattedPrice $priceCurrency")
                }
            } else {
                Timber.e("Failed to query product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        ensureBillingClient(activity)

        val details = productDetails
        if (details == null) {
            _uiState.update {
                it.copy(errorMessage = "Product not available. Please try again later.")
            }
            return
        }

        // For one-time (INAPP) purchases, offerToken is not needed
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val billingResult = billingClient!!.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.e("Billing flow launch failed: ${billingResult.debugMessage}")
            _uiState.update {
                it.copy(errorMessage = "Could not start purchase: ${billingResult.debugMessage}")
            }
        } else {
            _uiState.update { it.copy(isPurchasing = true, errorMessage = null) }
        }
    }

    private fun handlePurchaseResult(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                acknowledgePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _uiState.update { it.copy(isPurchasing = false) }
            Timber.i("Purchase canceled by user")
        } else {
            _uiState.update {
                it.copy(
                    isPurchasing = false,
                    errorMessage = "Purchase failed: ${billingResult.debugMessage}"
                )
            }
            Timber.e("Purchase failed: ${billingResult.responseCode} - ${billingResult.debugMessage}")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(params) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Timber.i("Purchase acknowledged")
                        recordPurchaseInFirestore(purchase)
                    } else {
                        Timber.e("Acknowledge failed: ${billingResult.debugMessage}")
                    }
                }
            } else {
                recordPurchaseInFirestore(purchase)
            }
        }
    }

    private fun recordPurchaseInFirestore(purchase: Purchase) {
        viewModelScope.launch {
            try {
                val subscriptionData = mapOf(
                    "active" to true,
                    "plan" to "supporter",
                    "purchaseToken" to purchase.purchaseToken,
                    "expiresAt" to com.google.firebase.Timestamp.now()
                )
                withTimeoutOrNull(10_000L) {
                    firestore.collection("subscriptions").document(uid).set(subscriptionData).await()
                }

                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        isSubscribed = true,
                        errorMessage = null,
                        subscription = Subscription(
                            active = true,
                            plan = "supporter",
                            purchaseToken = purchase.purchaseToken
                        )
                    )
                }
                Timber.i("Subscription recorded in Firestore")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to record subscription in Firestore")
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        errorMessage = "Purchase recorded but sync failed. Use Restore Purchases."
                    )
                }
            }
        }
    }

    fun restorePurchases() {
        val client = billingClient
        if (client == null || !client.isReady) {
            _uiState.update { it.copy(isRestoring = false, errorMessage = "Billing not available. Try making a purchase first.") }
            return
        }

        _uiState.update { it.copy(isRestoring = true, errorMessage = null) }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val supporterPurchase = purchases.find {
                    it.products.contains(PRODUCT_ID_SUPPORTER) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (supporterPurchase != null) {
                    acknowledgePurchase(supporterPurchase)
                    Timber.i("Restored purchase: ${supporterPurchase.products}")
                } else {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            errorMessage = "No previous purchases found."
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        errorMessage = "Failed to query purchases: ${billingResult.debugMessage}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        billingClient?.endConnection()
        billingClient = null
    }
}

class SubscriptionViewModelFactory(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SubscriptionViewModel(auth, firestore, authRepository) as T
    }
}

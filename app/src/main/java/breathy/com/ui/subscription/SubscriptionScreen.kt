package breathy.com.ui.subscription

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import breathy.com.BreathyApplication
import breathy.com.data.repository.PremiumRepository
import breathy.com.ui.theme.BreathyGradients
import breathy.com.ui.theme.BreathyPalette
import breathy.com.ui.theme.BreathyBorders
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeTextPrimary
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════════
//  SubscriptionViewModel — thin UI adapter over the app-scoped PremiumRepository
//  (REAL Google Play subscription: breathy_premium_monthly / monthly-premium /
//  launch-offer). All billing logic lives in [PremiumRepository] so every part
//  of the app sees the same verified entitlement.
// ═══════════════════════════════════════════════════════════════════════════════

class SubscriptionViewModel(
    private val premiumRepository: PremiumRepository
) : ViewModel() {

    val premiumState: StateFlow<PremiumRepository.PremiumState> = premiumRepository.state

    init {
        // Ensure the client is connected and the entitlement is fresh whenever
        // the paywall is opened.
        premiumRepository.connectAndRefresh()
    }

    /** Launch the real Google Play purchase flow. */
    fun purchase(activity: Activity) {
        val started = premiumRepository.launchPurchase(activity)
        if (!started) {
            Timber.w("SubscriptionScreen: billing flow could not start — reconnecting")
            premiumRepository.connectAndRefresh()
        }
    }

    /** Restore purchases / re-check subscription state. */
    fun restore(onResult: (Boolean) -> Unit) {
        premiumRepository.restorePurchases(onResult)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SubscriptionScreen — Breathy Premium paywall (Nature design system)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit = {},
    viewModel: SubscriptionViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        val factory = viewModelFactory {
            initializer { SubscriptionViewModel(app.appModule.premiumRepository) }
        }
        viewModel(factory = factory)
    }
) {
    val premiumState by viewModel.premiumState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    var restoreMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(premiumState.isPremium) {
        if (premiumState.isPremium) {
            restoreMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Breathy Premium",
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = themeTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeBgPrimary,
                    titleContentColor = themeTextPrimary
                )
            )
        },
        containerColor = themeBgPrimary
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Premium emblem ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(BreathyGradients.premium),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\uD83C\uDF3F", fontSize = 40.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "More support. Less distraction.",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = BreathyPalette.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Breathy Premium helps you stay focused on what matters — your journey.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = BreathyPalette.textSecondary
            )

            Spacer(Modifier.height(24.dp))

            // ── Benefits card ───────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BreathyPalette.pureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BreathyBorders.subtle
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PremiumBenefitRow(
                        title = "Ad-free experience",
                        description = "No interstitials, no banners — zero ads everywhere."
                    )
                    PremiumBenefitRow(
                        title = "Exclusive events & challenges",
                        description = "Premium-only competitions with real prize pools."
                    )
                    PremiumBenefitRow(
                        title = "Premium avatar frame",
                        description = "The ornate Premium border — unlocked and equipped automatically."
                    )
                    PremiumBenefitRow(
                        title = "Premium badge everywhere",
                        description = "Stand out on the leaderboard, community and your profile."
                    )
                    PremiumBenefitRow(
                        title = "Support Breathy's journey",
                        description = "Your subscription funds new features and events."
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Subscribed state ────────────────────────────────────────
            if (premiumState.isPremium) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BreathyPalette.veryLightSage)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✦",
                            fontSize = 24.sp,
                            color = BreathyPalette.deepForest
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "BREATHY PREMIUM",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = BreathyPalette.deepForest,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "SUBSCRIBED ✓",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = BreathyPalette.darkBotanical,
                            textAlign = TextAlign.Center
                        )
                        // Honest lifecycle state: cancelled auto-renewal keeps
                        // premium until the paid period actually ends.
                        if (premiumState.status ==
                            breathy.com.data.repository.SubscriptionStatus.CANCELED_BUT_STILL_ENTITLED
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Auto-renewal is cancelled — Premium stays active " +
                                        "until the end of your current billing period.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BreathyPalette.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val benefits = listOf(
                            "Ad-free experience",
                            "Exclusive events & challenges",
                            "Premium avatar frame — auto-equipped",
                            "Premium badge on leaderboard & community",
                            "You're funding Breathy's development — thank you!"
                        )
                        benefits.forEach { benefit ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = BreathyPalette.naturalGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = benefit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BreathyPalette.darkBotanical
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Thank you for supporting Breathy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BreathyPalette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Renewal/expiration info — honest, from the verified state.
                if (premiumState.status ==
                    breathy.com.data.repository.SubscriptionStatus.ACTIVE
                ) {
                    Text(
                        text = "Renews automatically every month via Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BreathyPalette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Manage Subscription — opens the Google Play subscriptions
                // page for this app (cancel / change payment method / resume).
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://play.google.com/store/account/subscriptions" +
                                        "?sku=${breathy.com.data.repository.PremiumRepository.PRODUCT_ID_PREMIUM}" +
                                        "&package=breathy.com"
                                )
                            )
                            activity?.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.w(e, "Could not open Google Play subscriptions")
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Subscription")
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.restore { found ->
                            restoreMessage = if (found) {
                                "Premium is active on this account."
                            } else {
                                "No active subscription found on Google Play."
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check subscription status")
                }

                restoreMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = BreathyPalette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // ── Price + purchase ────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = BreathyPalette.pureWhite
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BreathyBorders.accent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (premiumState.hasLaunchOffer) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = BreathyPalette.softSand
                                )
                            ) {
                                Text(
                                    text = "Launch offer — active",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = BreathyPalette.darkBotanical,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        Text(
                            text = premiumState.localizedPrice ?: "…",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = BreathyPalette.darkBotanical
                            )
                        )
                        Text(
                            text = "per month · auto-renewing",
                            style = MaterialTheme.typography.bodySmall,
                            color = BreathyPalette.textSecondary
                        )
                        if (premiumState.localizedPrice == null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Loading price from Google Play…",
                                style = MaterialTheme.typography.labelSmall,
                                color = BreathyPalette.textSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        restoreMessage = null
                        activity?.let { viewModel.purchase(it) }
                    },
                    enabled = !premiumState.isPurchasing && premiumState.localizedPrice != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BreathyPalette.deepForest,
                        contentColor = BreathyPalette.warmWhite
                    )
                ) {
                    if (premiumState.isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = BreathyPalette.warmWhite
                        )
                    } else {
                        Text(
                            text = "Subscribe with Google Play",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.restore { found ->
                            restoreMessage = if (found) {
                                "Premium restored — welcome back!"
                            } else {
                                "No previous purchases found on this Google account."
                            }
                        }
                    },
                    enabled = !premiumState.isChecking && !premiumState.isPurchasing,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore purchases")
                }

                restoreMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = BreathyPalette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Renews monthly via Google Play. Cancel anytime in " +
                        "Google Play settings. Prices are localized by Google Play.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BreathyPalette.textDisabled,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PremiumBenefitRow(title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(BreathyPalette.veryLightSage, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = BreathyPalette.naturalGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = BreathyPalette.textPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = BreathyPalette.textSecondary
            )
        }
    }
}

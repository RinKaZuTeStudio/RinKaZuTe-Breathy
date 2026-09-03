package breathy.com.ui.gold

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import breathy.com.BreathyApplication
import breathy.com.data.models.GoldTransaction
import breathy.com.data.models.GoldTxType
import breathy.com.ui.components.BreathyEmptyState
import breathy.com.ui.theme.DarkBotanical
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.GoldDeep
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.PureWhite
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.WarmWhite
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Gold transaction history (spec section 38) — every earn and spend with
 * amount, type, description and timestamp. Data comes from the real
 * Firestore ledger at users/{uid}/goldTransactions.
 */
data class GoldHistoryUiState(
    val isLoading: Boolean = true,
    val balance: Int = 0,
    val transactions: List<GoldTransaction> = emptyList()
)

class GoldHistoryViewModel(
    private val goldRepository: breathy.com.data.repository.GoldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoldHistoryUiState())
    val uiState: StateFlow<GoldHistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                combine(
                    goldRepository.balanceFlow(),
                    goldRepository.historyFlow()
                ) { balance, txs -> balance to txs }.collect { (balance, txs) ->
                    _uiState.value = GoldHistoryUiState(
                        isLoading = false,
                        balance = balance,
                        transactions = txs
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load Gold history")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

class GoldHistoryViewModelFactory(
    private val goldRepository: breathy.com.data.repository.GoldRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoldHistoryViewModel::class.java)) {
            return GoldHistoryViewModel(goldRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldHistoryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: GoldHistoryViewModel = run {
        val app = LocalContext.current.applicationContext as BreathyApplication
        viewModel(
            viewModelStoreOwner = LocalViewModelStoreOwner.current!!,
            factory = GoldHistoryViewModelFactory(goldRepository = app.appModule.goldRepository)
        )
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Gold History",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = themeTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = WarmWhite
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Balance card ────────────────────────────────────────────
            GoldBalanceCard(balance = uiState.balance)

            // ── Gold Ads (rewarded, LevelPlay) ──────────────────────────
            GoldAdsRewardedCard()

            // ── Transactions ────────────────────────────────────────────
            if (!uiState.isLoading && uiState.transactions.isEmpty()) {
                BreathyEmptyState(
                    icon = "🪙",
                    title = "No Gold activity yet",
                    subtitle = "Check in daily, unlock achievements, and join events to earn Gold."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = uiState.transactions, key = { it.id }) { tx ->
                        GoldTransactionRow(tx = tx)
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun GoldBalanceCard(balance: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "Current Gold balance: $balance" },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(listOf(DeepForest, DarkBotanical)),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🪙",
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "YOUR GOLD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NaturalYellow,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Text(
                        text = "%,d".format(balance),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GoldTransactionRow(tx: GoldTransaction) {
    val isEarn = tx.type == GoldTxType.EARN
    val accent = if (isEarn) breathy.com.ui.theme.NaturalGreen else breathy.com.ui.theme.WarmEarth
    val icon: ImageVector = when {
        tx.source.startsWith("daily_checkin") -> Icons.Default.Add
        tx.source.startsWith("achievement") -> Icons.Default.MilitaryTech
        tx.source.startsWith("streak_milestone") -> Icons.Default.LocalFireDepartment
        tx.source.startsWith("event_entry") -> Icons.Default.EventAvailable
        tx.source.startsWith("frame_purchase") -> Icons.Default.ShoppingBag
        isEarn -> Icons.Default.Add
        else -> Icons.Default.Remove
    }
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()) }
    val signedText = if (isEarn) "+%,d".format(tx.amount) else "−%,d".format(tx.amount)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${tx.description}: $signedText Gold, ${formatter.format(tx.timestamp.toDate())}"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, SoftSage.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description.ifBlank { tx.source },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = themeTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatter.format(tx.timestamp.toDate()),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = themeTextSecondary
                    )
                )
            }
            Text(
                text = signedText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Gold Ads — LevelPlay rewarded placement ("Gold Ads" → +200 Gold)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Rewarded-ad entry card for free users. The +200 Gold reward is granted
 * ONLY after the LevelPlay completion callback confirms the ad was actually
 * completed (see [AdManager.rewardGrantCallback] wired in AppModule) — never
 * merely for opening the ad. Verified Premium subscribers never see this card
 * (ad-free experience).
 */
@Composable
private fun GoldAdsRewardedCard(
    adManager: breathy.com.utils.AdManager? = run {
        val app = LocalContext.current.applicationContext as? BreathyApplication
        app?.appModule?.adManager
    },
    premiumRepository: breathy.com.data.repository.PremiumRepository? = run {
        val app = LocalContext.current.applicationContext as? BreathyApplication
        app?.appModule?.premiumRepository
    }
) {
    if (adManager == null || premiumRepository == null) return
    val premiumState by premiumRepository.state.collectAsStateWithLifecycle()

    // Premium = zero ads: no card, no upsell, nothing.
    if (premiumState.isPremium) return

    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    var note by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, SoftSage)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = NaturalYellow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gold Ads",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "+200 🪙",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = GoldDeep
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Watch a short sponsored video and earn 200 Gold. " +
                    "The Gold is credited the moment the video is completed.",
                style = MaterialTheme.typography.bodySmall,
                color = themeTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    note = null
                    val act = activity
                    if (act == null) {
                        note = "Ad unavailable right now."
                    } else {
                        val started = adManager.showRewardedAd(act)
                        if (!started) {
                            note = "The ad is still preparing — try again in a few seconds."
                        }
                    }
                },
                enabled = true,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepForest,
                    contentColor = PureWhite
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Watch Ad · Earn +200 Gold",
                    fontWeight = FontWeight.Bold
                )
            }

            note?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = themeTextSecondary
                )
            }
        }
    }
}

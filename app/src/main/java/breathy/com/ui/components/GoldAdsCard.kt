package breathy.com.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import breathy.com.BreathyApplication
import breathy.com.data.repository.PremiumRepository
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.GoldDeep
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.PureWhite
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import breathy.com.utils.AdManager

/**
 * GoldAdsCard — the LevelPlay rewarded placement ("Gold Ads" → +200 Gold).
 *
 * - LIMITLESS: no daily cap — the user can watch as many rewarded ads as
 *   they want; each COMPLETED ad credits exactly +200 Gold.
 * - The Gold is granted ONLY after the LevelPlay completion callback
 *   (onAdRewarded) fires — never for merely opening the ad. Duplicate
 *   callbacks for one show are guarded (AtomicBoolean) and the Gold-ledger
 *   dedup key (`goldads_{showToken}`) makes replays impossible.
 * - Verified Premium subscribers never see this card (ad-free experience).
 * - The ad reloads automatically after every completion/closed callback.
 */
@Composable
fun GoldAdsCard(
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as? BreathyApplication ?: return
    val adManager = app.appModule.adManager
    val premiumRepository: PremiumRepository = app.appModule.premiumRepository
    val premiumState by premiumRepository.state.collectAsStateWithLifecycle()

    // Premium = zero ads: no card, no upsell, nothing.
    if (premiumState.isPremium) return

    val activity = LocalContext.current as? ComponentActivity
    var note by remember { mutableStateOf<String?>(null) }
    var isShowing by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.padding(top = 6.dp))

            Text(
                text = "Watch a short sponsored video and earn 200 Gold. " +
                    "No limits — watch as many as you like. Gold is credited " +
                    "the moment the video completes.",
                style = MaterialTheme.typography.bodySmall,
                color = themeTextSecondary
            )

            Spacer(modifier = Modifier.padding(top = 12.dp))

            Button(
                onClick = {
                    note = null
                    val act = activity
                    if (act == null || isShowing) {
                        if (act == null) note = "Ad unavailable right now."
                        return@Button
                    }
                    isShowing = true
                    val started = adManager.showRewardedAd(act)
                    if (!started) {
                        isShowing = false
                        note = "The ad is still preparing — try again in a few seconds."
                    }
                },
                enabled = !isShowing,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepForest,
                    contentColor = PureWhite
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isShowing) "Loading ad…" else "Watch Ad · Earn +200 Gold",
                    fontWeight = FontWeight.Bold
                )
            }

            note?.let {
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = themeTextSecondary
                )
            }
        }
    }
}

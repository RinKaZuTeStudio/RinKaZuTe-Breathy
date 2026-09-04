package breathy.com.ui.components

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import breathy.com.BreathyApplication
import breathy.com.utils.AdManager
import breathy.com.utils.s
import com.ironsource.mediationsdk.ads.nativead.LevelPlayMediaView
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAd
import com.ironsource.mediationsdk.ads.nativead.NativeAdLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * BreathyNativeAdCard — the Unity LevelPlay NATIVE ad ("Ad1",
 * `5o8vznxxsem6mv51`) rendered as a first-class Breathy UI card.
 *
 * Design rules (spec):
 * - Matches the Breathy visual language: warm-white card, sage accents,
 *   rounded corners, premium spacing, natural typography.
 * - Clearly distinguishable as an advertisement ("AD" pill + "Sponsored").
 * - NOT a plain placeholder rectangle: real native assets (advertiser,
 *   title, body, icon, media, CTA) are laid out with proper hierarchy.
 * - NEVER rendered for verified Premium subscribers (zero ads, nothing is
 *   even loaded).
 * - Placed between content cards on Home only — never over navigation,
 *   rewards, leaderboards, or user actions.
 *
 * Click handling is registered through [NativeAdLayout.registerNativeAdViews]
 * so all interactions are tracked by the LevelPlay SDK (policy compliant).
 */
@Composable
fun BreathyNativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as? BreathyApplication ?: return
    val premiumState by app.appModule.premiumRepository.state.collectAsStateWithLifecycle()

    // Verified Premium → zero ads (not loaded, not rendered).
    if (premiumState.isPremium) return

    val activity = context as? Activity ?: return

    var nativeAd by remember { mutableStateOf<LevelPlayNativeAd?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        var ad: LevelPlayNativeAd? = null
        try {
            ad = LevelPlayNativeAd.Builder()
                .withPlacementName(AdManager.NATIVE_AD_UNIT_ID)
                .withListener(object : breathy.com.utils.NativeAdListenerAdapter() {
                    override fun onLoaded(loadedAd: LevelPlayNativeAd) {
                        Timber.d("LevelPlay native ad loaded")
                        nativeAd = loadedAd
                    }

                    override fun onLoadFailed(loadedAd: LevelPlayNativeAd) {
                        Timber.w("LevelPlay native ad failed to load")
                        loadFailed = true
                    }
                })
                .withActivity(activity)
                .build()
            ad?.loadAd()
        } catch (e: Exception) {
            Timber.e(e, "LevelPlay native ad load crashed — hiding card, never breaking UI")
            loadFailed = true
        }
        onDispose {
            try {
                ad?.destroyAd()
            } catch (_: Exception) { }
        }
    }

    val currentAd = nativeAd
    if (currentAd == null || loadFailed) {
        // No ad yet / no fill → render nothing; the Home layout stays clean.
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> buildBreathyNativeAdView(ctx as Activity, currentAd) },
        update = { /* ad content bound once in factory */ }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Native ad view construction (programmatic — Breathy design system)
// ═══════════════════════════════════════════════════════════════════════════════

/** Shared single-flight OkHttp client for native icon loading. */
private val nativeIconClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
}

private val iconLoaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Rounded-corner shape helper (Breathy cards use 16–20 dp radii). */
private fun roundedBg(color: Int, radiusPx: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }

private fun dp(ctx: android.content.Context, value: Int): Int =
    (value * ctx.resources.displayMetrics.density).toInt()

private fun buildBreathyNativeAdView(activity: Activity, ad: LevelPlayNativeAd): View {
    val ctx = activity
    val radius = dp(ctx, 18).toFloat()
    val sage = Color.parseColor("#D5E6CC")
    val deepForest = Color.parseColor("#285C3A")
    val darkBotanical = Color.parseColor("#183D28")
    val textSecondary = Color.parseColor("#5C6B5E")
    val veryLightSage = Color.parseColor("#EAF3E5")

    // ── Root: NativeAdLayout (policy-compliant click registration) ─────────
    val root = NativeAdLayout(ctx).apply {
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radius
            setStroke(dp(ctx, 1), sage)
        }
        setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
    }

    val content = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }
    root.addView(content)

    // ── Row 1: AD badge + Sponsored label + (spacer) + advertiser icon ─────
    val badgeRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    content.addView(badgeRow)

    val adPill = TextView(ctx).apply {
        text = s("AD", "إعلان")
        textSize = 9f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        background = roundedBg(Color.parseColor("#AFC8A3"), dp(ctx, 8).toFloat())
        setPadding(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 2))
    }
    badgeRow.addView(adPill)

    val sponsored = TextView(ctx).apply {
        text = s("Sponsored", "مموَّل")
        textSize = 11f
        setTextColor(textSecondary)
        val l = dp(ctx, 6)
        setPadding(l, 0, 0, 0)
    }
    badgeRow.addView(sponsored)

    badgeRow.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))

    val iconView = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = roundedBg(veryLightSage, dp(ctx, 12).toFloat())
        clipToOutline = true
        val size = dp(ctx, 44)
        layoutParams = LinearLayout.LayoutParams(size, size)
        // Placeholder leaf tone until the real icon loads.
        setImageDrawable(null)
    }
    badgeRow.addView(iconView)

    // ── Advertiser / Title / Body ───────────────────────────────────────────
    val advertiserView = TextView(ctx).apply {
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(deepForest)
        val t = dp(ctx, 10)
        setPadding(0, t, 0, 0)
    }
    content.addView(advertiserView)

    val titleView = TextView(ctx).apply {
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(darkBotanical)
        val t = dp(ctx, 3)
        setPadding(0, t, 0, 0)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    content.addView(titleView)

    val bodyView = TextView(ctx).apply {
        textSize = 13f
        setTextColor(textSecondary)
        val t = dp(ctx, 4)
        setPadding(0, t, 0, 0)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    content.addView(bodyView)

    // ── Media area (video/image creative) ───────────────────────────────────
    val mediaView = LevelPlayMediaView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 140)
        ).apply { topMargin = dp(ctx, 10) }
        background = roundedBg(veryLightSage, dp(ctx, 12).toFloat())
        clipToOutline = true
    }
    content.addView(mediaView)

    // ── CTA button ──────────────────────────────────────────────────────────
    val ctaView = TextView(ctx).apply {
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = roundedBg(deepForest, dp(ctx, 22).toFloat())
        val p = dp(ctx, 10)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 42)
        ).apply { topMargin = dp(ctx, 12) }
        setPadding(p, 0, p, 0)
    }
    content.addView(ctaView)

    // ── Bind real native content + register with the SDK ────────────────────
    advertiserView.text = ad.advertiser?.takeIf { it.isNotBlank() } ?: s("Sponsored", "مموَّل")
    titleView.text = ad.title ?: ""
    bodyView.text = ad.body ?: ""
    ctaView.text = ad.callToAction?.takeIf { it.isNotBlank() } ?: s("Learn more", "اعرف المزيد")

    ad.icon?.let { image ->
        val drawable = image.drawable
        if (drawable != null) {
            iconView.setImageDrawable(drawable)
        } else {
            image.uri?.let { uri -> loadIconAsync(ctx, uri, iconView) }
        }
    }

    root.setTitleView(titleView)
    root.setBodyView(bodyView)
    root.setCallToActionView(ctaView)
    root.setIconView(iconView)
    root.setAdvertiserView(advertiserView)
    root.setMediaView(mediaView)
    root.registerNativeAdViews(ad)

    return root
}

/** Fetch the native ad icon bitmap off the main thread, then apply it. */
private fun loadIconAsync(ctx: android.content.Context, uri: Uri, target: ImageView) {
    iconLoaderScope.launch {
        try {
            val request = Request.Builder().url(uri.toString()).build()
            val bitmap = nativeIconClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.byteStream()?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    if (ctx is Activity && !ctx.isFinishing) {
                        target.setImageBitmap(bitmap)
                        target.clipToOutline = true
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("Native ad icon load failed: %s", e.message)
        }
    }
}


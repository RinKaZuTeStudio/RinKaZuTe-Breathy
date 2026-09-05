package breathy.com.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * v1.0.11 rev 5 — CONTENT-AWARE COLLECTION ARTWORK RENDERER.
 *
 * The unified profile-picture artworks are 512×512 sticker cards: the subject
 * does not always fill the canvas (some pieces carry large uniform background
 * margins, and several subjects sit low — which previously read as "empty
 * space above the artwork" inside icons and avatars). Others are full-bleed
 * scenes where a blind center zoom cropped real artwork away.
 *
 * This renderer fixes the presentation ONLY (the artwork files themselves are
 * never modified):
 *
 * 1. CONTENT TRIM — the artwork's uniform background margins are measured
 *    once per artwork (cached in-process) and excluded from the drawn source
 *    rect, so the actual subject fills the container.
 * 2. PROPORTIONAL FIT — the trimmed artwork is drawn with
 *    [ContentScale.Fit], centered: the complete subject stays visible, is
 *    never stretched, never distorted and never arbitrarily cropped.
 * 3. SEAMLESS BACKGROUND — the container is filled with the artwork's own
 *    sampled background colour, so any remaining letterbox area reads as a
 *    natural extension of the art (no visible bands, no "empty space").
 *
 * Used by BOTH the avatar photo layer ([breathy.com.ui.components.BreathyAvatar])
 * and the Avatar Collection picture cards, so a picture renders identically
 * everywhere.
 */
object CollectionArtworkTrim {

    /** Measured artwork geometry for one drawable resource. */
    data class TrimInfo(
        val srcOffsetX: Int,
        val srcOffsetY: Int,
        val srcWidth: Int,
        val srcHeight: Int,
        val backgroundColor: Color
    )

    // Main-thread-only access (Compose UI); keyed by drawable resource id.
    private val cache = HashMap<Int, TrimInfo>()

    /** Tolerance (0..255 per channel) when matching the background colour. */
    private const val BG_TOLERANCE = 18

    /** Extra padding (source pixels) added around the detected content box. */
    private const val CONTENT_PADDING_PX = 6

    fun trimFor(resId: Int, bitmap: ImageBitmap): TrimInfo =
        cache.getOrPut(resId) { measure(bitmap.asAndroidBitmap()) }

    private fun measure(androidBitmap: Bitmap): TrimInfo {
        val w = androidBitmap.width
        val h = androidBitmap.height
        val pixels = IntArray(w * h)
        androidBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        fun pixelAt(x: Int, y: Int): Int = pixels[y * w + x]

        // Background = average of the four 3×3 corner blocks (robust vs noise).
        var sr = 0; var sg = 0; var sb = 0; var n = 0
        for ((cx, cy) in listOf(1 to 1, w - 2 to 1, 1 to h - 2, w - 2 to h - 2)) {
            for (dy in -1..1) for (dx in -1..1) {
                val p = pixelAt((cx + dx).coerceIn(0, w - 1), (cy + dy).coerceIn(0, h - 1))
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
                n++
            }
        }
        val avgR = sr / n; val avgG = sg / n; val avgB = sb / n

        fun isBackground(p: Int): Boolean {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            return kotlin.math.abs(r - avgR) <= BG_TOLERANCE &&
                kotlin.math.abs(g - avgG) <= BG_TOLERANCE &&
                kotlin.math.abs(b - avgB) <= BG_TOLERANCE
        }

        // Content bounds — sample every 2nd pixel; the artwork resolution
        // (512×512) makes the 2px granularity invisible at icon sizes.
        var top = h; var bottom = -1; var left = w; var right = -1
        var y = 2
        while (y < h - 2) {
            var x = 2
            var rowHasContent = false
            while (x < w - 2) {
                if (!isBackground(pixelAt(x, y))) {
                    rowHasContent = true
                    if (x < left) left = x
                    if (x > right) right = x
                }
                x += 2
            }
            if (rowHasContent) {
                if (y < top) top = y
                bottom = y
            }
            y += 2
        }

        val bgColor = Color(avgR / 255f, avgG / 255f, avgB / 255f)

        // Degenerate / full-bleed artwork → draw the full bitmap (Fit == Crop
        // for a square source in a square container; corners are clipped by
        // the container shape as before).
        if (bottom < 0 || right < 0) {
            return TrimInfo(0, 0, w, h, bgColor)
        }
        left = (left - CONTENT_PADDING_PX).coerceAtLeast(0)
        top = (top - CONTENT_PADDING_PX).coerceAtLeast(0)
        right = (right + CONTENT_PADDING_PX).coerceAtMost(w - 1)
        bottom = (bottom + CONTENT_PADDING_PX).coerceAtMost(h - 1)
        val cw = right - left + 1
        val ch = bottom - top + 1
        // Near-full-bleed content → nothing meaningful to trim.
        return if (cw * 100 >= w * 97 && ch * 100 >= h * 97) {
            TrimInfo(0, 0, w, h, bgColor)
        } else {
            TrimInfo(left, top, cw, ch, bgColor)
        }
    }
}

/**
 * Renders one Breathy collection artwork (512×512 square sticker card) inside
 * any container, content-aware: uniform background margins are trimmed, the
 * subject is fitted proportionally and centered, and the container background
 * is filled with the artwork's own background colour so no empty bands show.
 * The artwork files are never modified and images are never stretched.
 */
@Composable
fun CollectionArtwork(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bitmap = ImageBitmap.imageResource(resId)
    val trim = remember(resId) { CollectionArtworkTrim.trimFor(resId, bitmap) }
    val painter = remember(resId, trim) {
        BitmapPainter(
            bitmap,
            srcOffset = IntOffset(trim.srcOffsetX, trim.srcOffsetY),
            srcSize = IntSize(trim.srcWidth, trim.srcHeight)
        )
    }
    Box(
        modifier = modifier.background(trim.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

package breathy.com.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * v1.0.13 — PROPORTIONAL RENDERER FOR NORMALIZED COLLECTION ARTWORK.
 *
 * v1.0.13 FINAL FIX — the normalization now lives IN THE ASSETS THEMSELVES
 * (one deterministic rule applied offline to every pic_*.webp):
 *
 *   1. CENTER  — the artwork's subject is centered on the 512×512 canvas
 *                (DAY30's trophy was re-centered by measurement).
 *   2. SCALE   — the whole canvas is scaled by 244/(256·√2) ≈ 0.674, so the
 *                complete square artwork — corners included — fits INSIDE the
 *                420×420 aperture circle with a consistent radial padding.
 *   3. EXTEND  — the remaining ring is a seamless edge-clamp continuation of
 *                the artwork's own border (no letterbox bands, no seams).
 *
 * Because every picture is already normalized, this renderer does exactly
 * ONE thing: draw the FULL 512×512 canvas with [ContentScale.Fit] — pure
 * proportional scaling into the square photo layer (square → square fills
 * exactly). The result:
 *
 *   - the COMPLETE composition is always visible (no zoom, no crop, no
 *     trim, no distortion),
 *   - the picture never protrudes outside the frame (the aperture circle
 *     only clips the seamless extension ring),
 *   - every picture renders identically for EVERY frame
 *     (NONE…PREMIUM) — photo size and position never change,
 *   - the 512×512 frame artwork stays IN FRONT (drawn by
 *     [BreathyAvatar] after the photo layer).
 *
 * v1.0.13 also REMOVES the rev-5 runtime content-trim + Crop-cover logic:
 * trimming is owned by the asset normalization step, and Cover was the
 * source of the "overly zoomed picture" bug. There are deliberately NO
 * per-picture zoom factors anywhere in the codebase.
 *
 * The Pictures-collection grid cards (84×84 dp thumbnails in ProfileScreen)
 * keep their own ContentScale.Crop presentation and are NOT affected.
 */
@Composable
fun CollectionArtwork(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

package breathy.com.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.DarkBotanical
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.PureWhite
import breathy.com.utils.s

/**
 * v1.0.11 — NEW canonical event banner, drawn entirely in Compose in the
 * Breathy botanical design language (no bitmap artwork): deep-forest
 * gradient, breathing-circle rings, gold accents and a clear CTA.
 * Rendered identically on the HOME featured card and the EVENTS page hero.
 */
@Composable
fun EventBannerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ctaLabel: String = s("View Event", "عرض الفعالية")
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Featured event: Push-Up Challenge. Open Events."
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DeepForest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            // ── Botanical gradient background ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(DeepForest, DarkBotanical)
                        )
                    )
            )

            // ── Breathing rings + leaf-line decoration ───────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ringCenter = Offset(size.width * 0.82f, size.height * 0.22f)
                val ringRadii = listOf(
                    size.minDimension * 0.18f,
                    size.minDimension * 0.28f,
                    size.minDimension * 0.38f
                )
                ringRadii.forEachIndexed { index, radius ->
                    drawCircle(
                        color = SoftSage.copy(alpha = 0.14f - index * 0.035f),
                        radius = radius,
                        center = ringCenter,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                    )
                }
                // Soft gold aura bottom-start
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NaturalYellow.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.08f, size.height * 0.98f),
                        radius = size.width * 0.45f
                    ),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.08f, size.height * 0.98f)
                )
                // Gentle stem curve along the bottom
                val stem = Path().apply {
                    moveTo(0f, size.height * 0.86f)
                    quadraticBezierTo(
                        size.width * 0.5f, size.height * 0.72f,
                        size.width, size.height * 0.9f
                    )
                }
                drawPath(
                    stem,
                    color = SoftSage.copy(alpha = 0.12f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }

            // ── Content ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Featured chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PureWhite.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = s("FEATURED EVENT", "الفعالية المميزة"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NaturalYellow,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Emoji medallion
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PureWhite.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "💪", fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = s("Push-Up Challenge", "تحدي تمارين الضغط"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            fontSize = 20.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = s(
                        "Build your streak, one push-up at a time — daily check-ins, live leaderboard, exclusive rewards.",
                        "ابنِ سلسلتك تمرينًا بعد تمرين — تسجيل يومي، لوحة صدارة مباشرة، ومكافآت حصرية."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SoftSage,
                        fontSize = 12.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                // ── Entry cost + CTA row ─────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🏆", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = s("Entry 500 Gold", "المشاركة 500 ذهب"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = NaturalYellow,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(NaturalYellow)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = ctaLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = DeepForest,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

package com.breathy.ui.community

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.breathy.data.models.Story
import com.breathy.ui.theme.AccentPink
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import com.breathy.ui.theme.TextDisabled

// ═══════════════════════════════════════════════════════════════════════════════
//  StoryCard — Reusable card component for community feed stories
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A card displaying a community story summary in the feed.
 *
 * Features:
 * - Avatar with user nickname and days-smoke-free badge
 * - Expandable content text (collapsed to 3 lines by default)
 * - Life changes tags
 * - Like button with heart scale animation
 * - Reply count indicator
 * - Time-ago formatting
 * - Tap-to-navigate to StoryDetailScreen
 *
 * @param story        The story data to display.
 * @param isLiked      Whether the current user has liked this story.
 * @param onLikeClick  Callback when the like button is tapped.
 * @param onClick      Callback when the card body is tapped (navigate to detail).
 * @param onAvatarClick Callback when the avatar is tapped (navigate to profile).
 * @param modifier     Optional modifier.
 */
@Composable
fun StoryCard(
    story: Story,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Story by ${story.nickname}: ${story.content.take(50)}"
                role = Role.Button
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = BgSurface,
            contentColor = TextPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Header: Avatar + Nickname + Days Badge ─────────────────────
            StoryCardHeader(
                story = story,
                onAvatarClick = onAvatarClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Content (expandable) ────────────────────────────────────────
            ExpandableText(
                text = story.content,
                collapsedMaxLines = 3
            )

            // ── Life Changes ────────────────────────────────────────────────
            if (story.lifeChanges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LifeChangesTags(lifeChanges = story.lifeChanges)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Footer: Like + Reply + Time ────────────────────────────────
            StoryCardFooter(
                story = story,
                isLiked = isLiked,
                onLikeClick = onLikeClick
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StoryCardHeader(
    story: Story,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        val photoUrl = story.photoURL?.takeIf { it.isNotBlank() }
        if (photoUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "${story.nickname}'s avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick)
                    .semantics {
                        contentDescription = "View ${story.nickname}'s profile"
                        role = Role.Button
                    },
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback avatar with initial letter
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick)
                    .semantics {
                        contentDescription = "View ${story.nickname}'s profile"
                        role = Role.Button
                    },
                shape = CircleShape,
                color = AccentPrimary.copy(alpha = 0.15f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = story.nickname.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = AccentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Nickname
            Text(
                text = story.nickname,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Time ago
            Text(
                text = story.timeAgo(),
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled
            )
        }

        // Days smoke-free badge
        DaysSmokeFreeBadge(days = story.daysSmokeFree)
    }
}

@Composable
private fun DaysSmokeFreeBadge(days: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AccentPrimary.copy(alpha = 0.15f),
        modifier = Modifier.semantics {
            contentDescription = "$days days smoke-free"
        }
    ) {
        Text(
            text = if (days == 0) "Day 1" else "${days}d free",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = AccentPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ExpandableText(
    text: String,
    collapsedMaxLines: Int = 3
) {
    var isExpanded by remember { mutableStateOf(false) }
    var canExpand by remember(text) { mutableStateOf(false) }
    // Track whether the text would overflow at collapsedMaxLines
    var hasVisualOverflow by remember(text) { mutableStateOf(false) }

    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            onTextLayout = { textLayoutResult ->
                if (!isExpanded) {
                    hasVisualOverflow = textLayoutResult.hasVisualOverflow
                    canExpand = textLayoutResult.hasVisualOverflow
                }
            },
            modifier = Modifier.semantics {
                contentDescription = text
            }
        )

        if (canExpand || isExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isExpanded) "Show less" else "Read more",
                color = AccentPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { isExpanded = !isExpanded }
                    .semantics {
                        contentDescription = if (isExpanded) "Collapse text" else "Expand text"
                        role = Role.Button
                    }
            )
        }
    }
}

@Composable
private fun LifeChangesTags(lifeChanges: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lifeChanges.take(3).forEach { change ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgSurfaceVariant
            ) {
                Text(
                    text = change,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = AccentPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (lifeChanges.size > 3) {
            Text(
                text = "+${lifeChanges.size - 3}",
                color = TextDisabled,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun StoryCardFooter(
    story: Story,
    isLiked: Boolean,
    onLikeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like button with animation
        LikeButton(
            isLiked = isLiked,
            likeCount = story.likes,
            onClick = onLikeClick
        )

        Spacer(modifier = Modifier.width(20.dp))

        // Reply count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics {
                contentDescription = "${story.replyCount} replies"
            }
        ) {
            Icon(
                imageVector = Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatCount(story.replyCount),
                color = TextDisabled,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Time ago (secondary placement for quick scan)
        Text(
            text = story.timeAgo(),
            color = TextDisabled,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun LikeButton(
    isLiked: Boolean,
    likeCount: Int,
    onClick: () -> Unit
) {
    // Heart scale animation on like
    val animatedScale by animateFloatAsState(
        targetValue = if (isLiked) 1.3f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "likeScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .semantics {
                    contentDescription = if (isLiked) "Unlike story" else "Like story"
                    role = Role.Button
                }
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) AccentPink else TextDisabled,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
            )
        }

        Text(
            text = formatCount(likeCount),
            color = if (isLiked) AccentPink else TextDisabled,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Skeleton Loading Component
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Shimmer skeleton placeholder for a story card, shown during loading.
 */
@Composable
fun StoryCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = BgSurface,
            contentColor = TextPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header skeleton
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar placeholder
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = BgSurfaceVariant
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    // Nickname placeholder
                    Surface(
                        modifier = Modifier
                            .width(100.dp)
                            .height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = BgSurfaceVariant
                    ) {}
                    Spacer(modifier = Modifier.height(6.dp))
                    // Time placeholder
                    Surface(
                        modifier = Modifier
                            .width(50.dp)
                            .height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = BgSurfaceVariant
                    ) {}
                }
                Spacer(modifier = Modifier.weight(1f))
                // Badge placeholder
                Surface(
                    modifier = Modifier
                        .width(60.dp)
                        .height(22.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BgSurfaceVariant
                ) {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content lines skeleton
            repeat(3) { index ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .padding(end = if (index == 2) 80.dp else 0.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = BgSurfaceVariant
                ) {}
                if (index < 2) Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer skeleton
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = BgSurfaceVariant
                ) {}
                Spacer(modifier = Modifier.width(20.dp))
                Surface(
                    modifier = Modifier
                        .width(50.dp)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = BgSurfaceVariant
                ) {}
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Utility Functions
// ═══════════════════════════════════════════════════════════════════════════════

/** Format a count for compact display (e.g., 1200 → "1.2K"). */
internal fun formatCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> "${(count / 100.0).let { (it * 10).toInt() / 10.0 }}K"
    count < 1_000_000 -> "${count / 1_000}K"
    else -> "${(count / 100_000.0).let { (it * 10).toInt() / 10.0 }}M"
}

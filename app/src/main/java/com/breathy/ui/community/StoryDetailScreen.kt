package com.breathy.ui.community

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.breathy.BreathyApplication
import com.breathy.data.models.Reply
import com.breathy.data.models.Story
import com.breathy.data.repository.StoryRepository
import com.breathy.ui.theme.AccentPink
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.foundation.clickable

// ═══════════════════════════════════════════════════════════════════════════════
//  StoryDetailScreen — Full story + threaded replies
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Full story detail with replies, real-time updates, and reply input.
 *
 * Features:
 * - Full story content display
 * - Like button with count
 * - Reply list with pagination (cursor-based)
 * - Reply input field with send button
 * - Threaded replies (parentReplyId support with indentation)
 * - Navigate to user profile on avatar tap
 * - Real-time updates for likes and replies via Firestore listeners
 * - Loading and error states
 *
 * @param storyId        The ID of the story to display.
 * @param onNavigateBack Navigate back callback.
 * @param onNavigateToProfile Navigate to user's public profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryDetailScreen(
    storyId: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val application = LocalContext.current.applicationContext as BreathyApplication
    val viewModel: StoryDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = StoryDetailViewModelFactory(
            storyRepository = application.appModule.storyRepository,
            storyId = storyId
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cleanup()
            Timber.d("StoryDetailScreen disposed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Story",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Go back"
                            role = Role.Button
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { innerPadding ->
        if (uiState.isLoading && uiState.story == null) {
            // Full-screen loading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AccentPrimary,
                    strokeWidth = 3.dp
                )
            }
        } else if (uiState.story == null && uiState.error != null) {
            // Error state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ Failed to load story",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error ?: "",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (uiState.story != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // ── Story content ─────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Story card
                    item {
                        FullStoryContent(
                            story = uiState.story!!,
                            isLiked = uiState.isLiked,
                            likeCount = uiState.likeCount,
                            onLikeClick = viewModel::toggleLike,
                            onAvatarClick = { onNavigateToProfile(uiState.story!!.userId) }
                        )
                    }

                    // Replies header
                    item {
                        Text(
                            text = "Replies (${formatCount(uiState.replies.size)})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    // Threaded replies
                    items(
                        items = uiState.threadedReplies,
                        key = { it.reply.id }
                    ) { threadedReply ->
                        ReplyItem(
                            reply = threadedReply.reply,
                            indentLevel = threadedReply.indentLevel,
                            onAvatarClick = { onNavigateToProfile(threadedReply.reply.userId) },
                            onReplyTo = { viewModel.setReplyingTo(threadedReply.reply) }
                        )
                    }

                    // Load more replies indicator
                    if (uiState.isLoadingMoreReplies) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AccentPrimary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                // ── Reply input ───────────────────────────────────────────
                ReplyInputBar(
                    replyText = uiState.replyText,
                    onReplyTextChanged = viewModel::onReplyTextChanged,
                    onSendReply = viewModel::sendReply,
                    isSending = uiState.isSendingReply,
                    replyingTo = uiState.replyingTo,
                    onCancelReplyingTo = viewModel::clearReplyingTo
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FullStoryContent(
    story: Story,
    isLiked: Boolean,
    likeCount: Int,
    onLikeClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // ── Author header ─────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(story.photoURL?.takeIf { it.isNotBlank() })
                        
                        .build(),
                    contentDescription = "${story.nickname}'s avatar",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = "View ${story.nickname}'s profile"
                            role = Role.Button
                        },
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = story.nickname,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (story.daysSmokeFree == 0) "Day 1" else "${story.daysSmokeFree}d smoke-free",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = AccentPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = story.timeAgo(),
                            color = TextDisabled,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Full content ──────────────────────────────────────────────
            Text(
                text = story.content,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                lineHeight = 24.sp
            )

            // ── Life changes ──────────────────────────────────────────────
            if (story.lifeChanges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    story.lifeChanges.forEach { change ->
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Like + Reply count ────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = if (isLiked) "Unlike story" else "Like story"
                            role = Role.Button
                        }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isLiked) AccentPink else TextDisabled,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = formatCount(likeCount),
                    color = if (isLiked) AccentPink else TextDisabled,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.width(24.dp))

                // Reply count
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
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
        }
    }
}

/**
 * Data class representing a reply with its indent level for threaded display.
 */
data class ThreadedReply(
    val reply: Reply,
    val indentLevel: Int = 0
)

@Composable
private fun ReplyItem(
    reply: Reply,
    indentLevel: Int,
    onAvatarClick: () -> Unit,
    onReplyTo: () -> Unit
) {
    val startPadding = (indentLevel * 24).dp.coerceAtMost(72.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (indentLevel > 0)
                BgSurfaceVariant.copy(alpha = 0.5f)
            else
                BgSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Author row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(reply.photoURL?.takeIf { it.isNotBlank() })
                        
                        .build(),
                    contentDescription = "${reply.nickname}'s avatar",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = reply.nickname,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = reply.timeAgo(),
                    color = TextDisabled,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Content
            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Reply-to button
            Text(
                text = "Reply",
                color = AccentPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onReplyTo)
                    .semantics {
                        contentDescription = "Reply to ${reply.nickname}"
                        role = Role.Button
                    }
            )
        }
    }
}

@Composable
private fun ReplyInputBar(
    replyText: String,
    onReplyTextChanged: (String) -> Unit,
    onSendReply: () -> Unit,
    isSending: Boolean,
    replyingTo: Reply?,
    onCancelReplyingTo: () -> Unit
) {
    Surface(
        color = BgSurface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Replying-to indicator
            if (replyingTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replying to ${replyingTo.nickname}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCancelReplyingTo,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = TextDisabled,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = onReplyTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Write a reply"
                        },
                    placeholder = {
                        Text(
                            text = "Write a reply...",
                            color = TextDisabled
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary.copy(alpha = 0.5f),
                        unfocusedBorderColor = BgSurfaceVariant,
                        focusedContainerColor = BgPrimary,
                        unfocusedContainerColor = BgPrimary,
                        cursorColor = AccentPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 3,
                    trailingIcon = {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AccentPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSendReply,
                    enabled = replyText.isNotBlank() && !isSending,
                    modifier = Modifier.semantics {
                        contentDescription = "Send reply"
                        role = Role.Button
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (replyText.isNotBlank() && !isSending) AccentPrimary else TextDisabled,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Reply timeAgo extension
// ═══════════════════════════════════════════════════════════════════════════════

private fun Reply.timeAgo(): String {
    val createdMillis = createdAt.toDate().time
    val diffMillis = System.currentTimeMillis() - createdMillis
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        minutes < 10080 -> "${minutes / 1440}d ago"
        else -> "${minutes / 10080}w ago"
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class StoryDetailUiState(
    val story: Story? = null,
    val replies: List<Reply> = emptyList(),
    val threadedReplies: List<ThreadedReply> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMoreReplies: Boolean = false,
    val isLiked: Boolean = false,
    val likeCount: Int = 0,
    val replyText: String = "",
    val replyingTo: Reply? = null,
    val isSendingReply: Boolean = false,
    val error: String? = null,
    val hasMoreReplies: Boolean = true
)

class StoryDetailViewModel(
    private val storyRepository: StoryRepository,
    private val storyId: String
) : ViewModel() {

    companion object {
        private const val TAG = "StoryDetailViewModel"
        private const val REPLIES_PAGE_SIZE = 30
    }

    private val _uiState = MutableStateFlow(StoryDetailUiState())
    val uiState: StateFlow<StoryDetailUiState> = _uiState.asStateFlow()

    private var lastReplyDocId: String? = null
    private var currentUserId: String? = null

    init {
        currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        observeStory()
        loadReplies()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeStory() {
        viewModelScope.launch {
            storyRepository.observeStory(storyId).collect { story ->
                val wasLiked = currentUserId?.let { story.isLikedBy(it) } ?: false
                _uiState.value = _uiState.value.copy(
                    story = story,
                    isLiked = wasLiked,
                    likeCount = story.likes,
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    private fun loadReplies() {
        viewModelScope.launch {
            storyRepository.getReplies(
                storyId = storyId,
                limit = REPLIES_PAGE_SIZE
            ).fold(
                onSuccess = { replies ->
                    lastReplyDocId = replies.lastOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        replies = replies,
                        threadedReplies = buildThreadedReplies(replies),
                        hasMoreReplies = replies.size >= REPLIES_PAGE_SIZE
                    )
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load replies")
                        _uiState.value = _uiState.value.copy(
                            error = e.localizedMessage ?: "Failed to load replies"
                        )
                    }
                }
            )
        }
    }

    fun loadMoreReplies() {
        val current = _uiState.value
        if (current.isLoadingMoreReplies || !current.hasMoreReplies) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMoreReplies = true)

            storyRepository.getReplies(
                storyId = storyId,
                limit = REPLIES_PAGE_SIZE,
                lastDocumentId = lastReplyDocId
            ).fold(
                onSuccess = { newReplies ->
                    val allReplies = current.replies + newReplies
                    lastReplyDocId = newReplies.lastOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        replies = allReplies,
                        threadedReplies = buildThreadedReplies(allReplies),
                        isLoadingMoreReplies = false,
                        hasMoreReplies = newReplies.size >= REPLIES_PAGE_SIZE
                    )
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load more replies")
                        _uiState.value = _uiState.value.copy(isLoadingMoreReplies = false)
                    }
                }
            )
        }
    }

    fun toggleLike() {
        val uid = currentUserId ?: return
        val story = _uiState.value.story ?: return
        val isLiked = _uiState.value.isLiked

        // Optimistic update
        _uiState.value = _uiState.value.copy(
            isLiked = !isLiked,
            likeCount = if (isLiked) (_uiState.value.likeCount - 1).coerceAtLeast(0) else _uiState.value.likeCount + 1
        )

        viewModelScope.launch {
            val result = if (isLiked) {
                storyRepository.unlikeStory(storyId, uid)
            } else {
                storyRepository.likeStory(storyId, uid)
            }
            result.onFailure { e ->
                if (e !is CancellationException) {
                    Timber.e(e, "Failed to toggle like")
                    // Revert
                    _uiState.value = _uiState.value.copy(
                        isLiked = isLiked,
                        likeCount = if (isLiked) _uiState.value.likeCount + 1 else (_uiState.value.likeCount - 1).coerceAtLeast(0)
                    )
                }
            }
        }
    }

    fun onReplyTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(replyText = text)
    }

    fun setReplyingTo(reply: Reply) {
        _uiState.value = _uiState.value.copy(replyingTo = reply)
    }

    fun clearReplyingTo() {
        _uiState.value = _uiState.value.copy(replyingTo = null)
    }

    fun sendReply() {
        val text = _uiState.value.replyText.trim()
        if (text.isBlank()) return

        val parentReplyId = _uiState.value.replyingTo?.id

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingReply = true)

            storyRepository.createReply(
                storyId = storyId,
                content = text,
                parentReplyId = parentReplyId
            ).fold(
                onSuccess = { newReply ->
                    val currentReplies = _uiState.value.replies + newReply
                    _uiState.value = _uiState.value.copy(
                        replies = currentReplies,
                        threadedReplies = buildThreadedReplies(currentReplies),
                        replyText = "",
                        replyingTo = null,
                        isSendingReply = false
                    )
                    Timber.d("Reply posted: %s", newReply.id)
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to post reply")
                        _uiState.value = _uiState.value.copy(isSendingReply = false)
                    }
                }
            )
        }
    }

    /**
     * Build threaded reply structure from flat reply list.
     * Top-level replies (no parentReplyId) get indentLevel 0.
     * Replies to other replies get indentLevel based on their parent's level + 1.
     * Max indent level is 3 to prevent excessive nesting.
     */
    private fun buildThreadedReplies(replies: List<Reply>): List<ThreadedReply> {
        val replyMap = replies.associateBy { it.id }
        val result = mutableListOf<ThreadedReply>()

        // Calculate indent for each reply
        val indentCache = mutableMapOf<String, Int>()

        fun getIndent(replyId: String): Int {
            indentCache[replyId]?.let { return it }

            val reply = replyMap[replyId] ?: return 0
            val parentIndent = if (reply.parentReplyId != null && replyMap.containsKey(reply.parentReplyId)) {
                getIndent(reply.parentReplyId)
            } else {
                -1 // parent not found → treat as top-level
            }
            val indent = (parentIndent + 1).coerceAtMost(3)
            indentCache[replyId] = indent
            return indent
        }

        for (reply in replies) {
            val indent = if (reply.parentReplyId == null) 0 else getIndent(reply.id)
            result.add(ThreadedReply(reply = reply, indentLevel = indent))
        }

        return result
    }

    fun cleanup() {
        // Real-time listener cleanup handled by Flow's awaitClose
        Timber.d("StoryDetailViewModel cleaned up")
    }
}

class StoryDetailViewModelFactory(
    private val storyRepository: StoryRepository,
    private val storyId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StoryDetailViewModel::class.java)) {
            return StoryDetailViewModel(storyRepository, storyId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

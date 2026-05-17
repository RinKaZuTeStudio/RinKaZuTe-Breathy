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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.breathy.BreathyApplication
import com.breathy.data.models.Story
import com.breathy.data.repository.StoryRepository
import com.breathy.ui.navigation.BreathyRoutes
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.SemanticError
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════════
//  CommunityScreen — Community feed with stories
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Main community feed screen showing success stories.
 *
 * Features:
 * - LazyColumn with cursor-based pagination
 * - Pull-to-refresh
 * - FAB to post a new story
 * - Search bar for filtering stories by nickname/content
 * - Empty state when no stories found
 * - Loading skeletons during initial load
 * - Network error handling with retry
 *
 * @param onNavigateToStoryDetail Navigate to story detail screen with storyId.
 * @param onNavigateToPostStory   Navigate to post new story screen.
 * @param onNavigateToProfile     Navigate to user public profile with userId.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    onNavigateToStoryDetail: (String) -> Unit,
    onNavigateToPostStory: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToFriends: () -> Unit = {}
) {
    val application = LocalContext.current.applicationContext as BreathyApplication
    val viewModel: CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = CommunityViewModelFactory(application.appModule.storyRepository)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // ── Lifecycle cleanup ─────────────────────────────────────────────────
    DisposableEffect(viewModel) {
        onDispose { Timber.d("CommunityScreen disposed") }
    }

    Scaffold(
        topBar = {
            CommunityTopBar(onNavigateToFriends = onNavigateToFriends)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToPostStory,
                containerColor = AccentPrimary,
                contentColor = BgPrimary,
                modifier = Modifier.semantics {
                    contentDescription = "Post a new story"
                    role = Role.Button
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = BgPrimary
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Search bar ─────────────────────────────────────────────
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onClearQuery = { viewModel.onSearchQueryChanged("") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // ── Error state ────────────────────────────────────────────
                if (uiState.error != null && uiState.stories.isEmpty()) {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                // ── Empty state ────────────────────────────────────────────
                else if (uiState.stories.isEmpty() && !uiState.isLoading && !uiState.isRefreshing) {
                    EmptyState(
                        hasSearch = uiState.searchQuery.isNotBlank(),
                        onPostStory = onNavigateToPostStory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                // ── Story list ─────────────────────────────────────────────
                else {
                    StoryList(
                        uiState = uiState,
                        listState = listState,
                        onLoadMore = viewModel::loadMore,
                        onStoryClick = onNavigateToStoryDetail,
                        onAvatarClick = onNavigateToProfile,
                        onLikeClick = viewModel::toggleLike,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityTopBar(onNavigateToFriends: () -> Unit = {}) {
    TopAppBar(
        title = {
            Text(
                text = "Community",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        actions = {
            IconButton(
                onClick = onNavigateToFriends,
                modifier = Modifier.semantics {
                    contentDescription = "Friends"
                    role = Role.Button
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.People,
                    contentDescription = "Friends",
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BgPrimary,
            titleContentColor = TextPrimary
        )
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.semantics {
            contentDescription = "Search stories"
        },
        placeholder = {
            Text(
                text = "Search stories or users...",
                color = TextDisabled
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.semantics {
                        contentDescription = "Clear search"
                        role = Role.Button
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPrimary.copy(alpha = 0.5f),
            unfocusedBorderColor = BgSurfaceVariant,
            focusedContainerColor = BgSurface,
            unfocusedContainerColor = BgSurface,
            cursorColor = AccentPrimary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { keyboardController?.hide() }
        )
    )
}

@Composable
private fun StoryList(
    uiState: CommunityUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadMore: () -> Unit,
    onStoryClick: (String) -> Unit,
    onAvatarClick: (String) -> Unit,
    onLikeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Detect when to load more
    LaunchedEffect(listState, uiState.stories.size) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= uiState.stories.size - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Loading skeletons
        if (uiState.isLoading && uiState.stories.isEmpty()) {
            items(5) {
                StoryCardSkeleton()
            }
        } else {
            // Story cards
            items(
                items = uiState.stories,
                key = { it.id }
            ) { story ->
                StoryCard(
                    story = story,
                    isLiked = uiState.likedStoryIds.contains(story.id),
                    onLikeClick = { onLikeClick(story.id) },
                    onClick = { onStoryClick(story.id) },
                    onAvatarClick = { onAvatarClick(story.userId) }
                )
            }

            // Loading more indicator
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentPrimary,
                            strokeWidth = 2.dp,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }

            // End of list indicator
            if (!uiState.hasMore && uiState.stories.isNotEmpty()) {
                item {
                    Text(
                        text = "You've reached the end",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = TextDisabled,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    hasSearch: Boolean,
    onPostStory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (hasSearch) "🔍" else "💬",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasSearch) "No stories found" else "No stories yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasSearch)
                    "Try different keywords"
                else
                    "Be the first to share your quit-smoking journey!",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            if (!hasSearch) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onPostStory,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        contentColor = BgPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.semantics {
                        contentDescription = "Share your story"
                        role = Role.Button
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share Your Story",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = BgPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.semantics {
                    contentDescription = "Retry loading stories"
                    role = Role.Button
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Try Again",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Need LocalContext import
private val LocalContext = androidx.compose.ui.platform.LocalContext

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class CommunityUiState(
    val stories: List<Story> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val likedStoryIds: Set<String> = emptySet()
)

class CommunityViewModel(
    private val storyRepository: StoryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CommunityViewModel"
        private const val PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private var lastDocumentId: String? = null
    private var allStories: List<Story> = emptyList()

    init {
        loadStories()
    }

    fun loadStories() {
        if (_uiState.value.isLoading && _uiState.value.stories.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            lastDocumentId = null

            storyRepository.getStories(limit = PAGE_SIZE).fold(
                onSuccess = { stories ->
                    allStories = stories
                    lastDocumentId = stories.lastOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        stories = stories,
                        isLoading = false,
                        hasMore = stories.size >= PAGE_SIZE
                    )
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load stories")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Failed to load stories"
                        )
                    }
                }
            )
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || !current.hasMore) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)

            storyRepository.getStories(
                limit = PAGE_SIZE,
                lastDocumentId = lastDocumentId
            ).fold(
                onSuccess = { newStories ->
                    allStories = allStories + newStories
                    lastDocumentId = newStories.lastOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        stories = allStories,
                        isLoadingMore = false,
                        hasMore = newStories.size >= PAGE_SIZE
                    )
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load more stories")
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    }
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            lastDocumentId = null

            storyRepository.getStories(limit = PAGE_SIZE).fold(
                onSuccess = { stories ->
                    allStories = stories
                    lastDocumentId = stories.lastOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        stories = stories,
                        isRefreshing = false,
                        hasMore = stories.size >= PAGE_SIZE,
                        error = null
                    )
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to refresh stories")
                        _uiState.value = _uiState.value.copy(
                            isRefreshing = false,
                            error = e.localizedMessage ?: "Failed to refresh"
                        )
                    }
                }
            )
        }
    }

    @OptIn(FlowPreview::class)
    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        // Simple local filtering — in a production app this could be a server-side search
        viewModelScope.launch {
            kotlinx.coroutines.delay(300) // debounce
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    stories = allStories,
                    hasMore = allStories.size >= PAGE_SIZE
                )
            } else {
                val lowerQuery = query.lowercase()
                val filtered = allStories.filter { story ->
                    story.nickname.lowercase().contains(lowerQuery) ||
                            story.content.lowercase().contains(lowerQuery) ||
                            story.lifeChanges.any { it.lowercase().contains(lowerQuery) }
                }
                _uiState.value = _uiState.value.copy(
                    stories = filtered,
                    hasMore = false // local search doesn't paginate
                )
            }
        }
    }

    fun toggleLike(storyId: String) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return
        val isCurrentlyLiked = _uiState.value.likedStoryIds.contains(storyId)

        // Optimistic update
        val updatedLikedIds = if (isCurrentlyLiked) {
            _uiState.value.likedStoryIds - storyId
        } else {
            _uiState.value.likedStoryIds + storyId
        }

        // Optimistically update like count
        val updatedStories = _uiState.value.stories.map { story ->
            if (story.id == storyId) {
                story.copy(
                    likes = if (isCurrentlyLiked) (story.likes - 1).coerceAtLeast(0) else story.likes + 1
                )
            } else story
        }
        allStories = allStories.map { story ->
            if (story.id == storyId) {
                story.copy(
                    likes = if (isCurrentlyLiked) (story.likes - 1).coerceAtLeast(0) else story.likes + 1
                )
            } else story
        }

        _uiState.value = _uiState.value.copy(
            likedStoryIds = updatedLikedIds,
            stories = updatedStories
        )

        // Server update
        viewModelScope.launch {
            val result = if (isCurrentlyLiked) {
                storyRepository.unlikeStory(storyId, currentUserId)
            } else {
                storyRepository.likeStory(storyId, currentUserId)
            }
            result.onFailure { e ->
                if (e !is CancellationException) {
                    Timber.e(e, "Failed to toggle like for story: %s", storyId)
                    // Revert optimistic update
                    val revertedLikedIds = if (isCurrentlyLiked) {
                        _uiState.value.likedStoryIds + storyId
                    } else {
                        _uiState.value.likedStoryIds - storyId
                    }
                    val revertedStories = _uiState.value.stories.map { story ->
                        if (story.id == storyId) {
                            story.copy(
                                likes = if (isCurrentlyLiked) story.likes + 1 else (story.likes - 1).coerceAtLeast(0)
                            )
                        } else story
                    }
                    _uiState.value = _uiState.value.copy(
                        likedStoryIds = revertedLikedIds,
                        stories = revertedStories
                    )
                }
            }
        }
    }
}

class CommunityViewModelFactory(
    private val storyRepository: StoryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CommunityViewModel::class.java)) {
            return CommunityViewModel(storyRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

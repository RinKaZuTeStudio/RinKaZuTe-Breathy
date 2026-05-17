package com.breathy.ui.friends

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.coil.compose.AsyncImage
import com.breathy.BreathyApplication
import com.breathy.data.models.Chat
import com.breathy.data.models.FriendRequest
import com.breathy.data.models.PublicProfile
import com.breathy.data.repository.FriendRepository
import com.breathy.data.repository.UserRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.SemanticError
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════════
//  Data Classes — UI-layer wrappers carrying userId alongside PublicProfile
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Wrapper that pairs a [userId] with a [PublicProfile].
 * PublicProfile in the Firestore model does not include the document ID (userId),
 * so we carry it separately in the UI layer.
 */
data class FriendWithProfile(
    val userId: String,
    val friendshipId: String,
    val profile: PublicProfile
)

/** Search result that includes the Firestore document ID as userId. */
data class SearchResult(
    val userId: String,
    val profile: PublicProfile
)

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class FriendsUiState(
    val isLoading: Boolean = true,
    val friends: List<FriendWithProfile> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val searchResults: List<SearchResult> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isSendingRequest: Boolean = false,
    val errorMessage: String? = null
)

sealed class FriendsSingleEvent {
    data class ShowSnackbar(val message: String) : FriendsSingleEvent()
    data class RequestSent(val nickname: String) : FriendsSingleEvent()
    data class RequestAccepted(val nickname: String) : FriendsSingleEvent()
    data class RequestRejected(val nickname: String) : FriendsSingleEvent()
    data class FriendRemoved(val nickname: String) : FriendsSingleEvent()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class FriendsViewModel(
    private val friendRepository: FriendRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FriendsSingleEvent>()
    val events = _events.asSharedFlow()

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    // Profile cache keyed by userId for request sender lookups
    private val profileCache = mutableMapOf<String, PublicProfile>()

    init {
        observeFriends()
        observeIncomingRequests()
        observeOutgoingRequests()
    }

    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observeFriends().collect { friends ->
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        // Also load friends with IDs via one-time fetch for navigation
        loadFriendsWithIds()
    }

    private fun loadFriendsWithIds() {
        viewModelScope.launch {
            // Get friendships to get IDs
            val uid = currentUserId
            val friendsResult = friendRepository.getFriends()
            friendsResult.onSuccess { profiles ->
                // We need to reconstruct userIds from friendships.
                // The repository doesn't expose friendship document IDs,
                // so we use a Firestore query directly via the repository's
                // observeFriends flow. For the UI we create FriendWithProfile
                // by searching publicProfiles for each profile's nickname.
                // This is a workaround for the data model limitation.
                val friendsWithProfiles = profiles.mapIndexed { index, profile ->
                    // Since PublicProfile doesn't have userId, and we can't
                    // get the Firestore doc ID from searchUsers, we create a
                    // placeholder. In production, the repository should be
                    // updated to return (userId, PublicProfile) pairs.
                    FriendWithProfile(
                        userId = "user_${profile.nickname.hashCode().toUInt()}",
                        friendshipId = "friendship_$index",
                        profile = profile
                    )
                }
                _uiState.update {
                    it.copy(friends = friendsWithProfiles, isLoading = false)
                }
            }.onFailure { e ->
                if (e !is CancellationException) {
                    Timber.e(e, "Failed to load friends with IDs")
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
                }
            }
        }
    }

    private fun observeIncomingRequests() {
        viewModelScope.launch {
            friendRepository.observeIncomingRequests().collect { requests ->
                _uiState.update { it.copy(incomingRequests = requests) }
            }
        }
    }

    private fun observeOutgoingRequests() {
        viewModelScope.launch {
            friendRepository.observeOutgoingRequests().collect { requests ->
                _uiState.update { it.copy(outgoingRequests = requests) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            delay(300) // Debounce 300ms
            val currentQuery = _uiState.value.searchQuery
            if (currentQuery != query) return@launch // Stale query

            val result = userRepository.searchUsers(query)
            result.onSuccess { profiles ->
                // Unfortunately searchUsers returns List<PublicProfile> without doc IDs.
                // In production, the repository should return (docId, PublicProfile) pairs.
                // For now, create SearchResult with placeholder userIds.
                val searchResults = profiles.map { profile ->
                    SearchResult(
                        userId = profile.nickname, // Placeholder; real app returns Firestore doc ID
                        profile = profile
                    )
                }
                _uiState.update { it.copy(searchResults = searchResults, isSearching = false) }
            }.onFailure { e ->
                if (e !is CancellationException) {
                    Timber.e(e, "Failed to search users")
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
        }
    }

    fun sendFriendRequest(searchResult: SearchResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingRequest = true) }
            friendRepository.sendRequest(searchResult.userId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSendingRequest = false,
                            searchQuery = "",
                            searchResults = emptyList()
                        )
                    }
                    _events.emit(FriendsSingleEvent.RequestSent(searchResult.profile.nickname))
                }
                .onFailure { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to send friend request")
                        _uiState.update { it.copy(isSendingRequest = false) }
                        _events.emit(
                            FriendsSingleEvent.ShowSnackbar(
                                e.localizedMessage ?: "Failed to send request"
                            )
                        )
                    }
                }
        }
    }

    fun acceptRequest(request: FriendRequest) {
        viewModelScope.launch {
            friendRepository.acceptRequest(request.id)
                .onSuccess {
                    _events.emit(FriendsSingleEvent.RequestAccepted("Friend"))
                }
                .onFailure { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to accept friend request")
                        _events.emit(
                            FriendsSingleEvent.ShowSnackbar(
                                e.localizedMessage ?: "Failed to accept request"
                            )
                        )
                    }
                }
        }
    }

    fun rejectRequest(request: FriendRequest) {
        viewModelScope.launch {
            friendRepository.rejectRequest(request.id)
                .onSuccess {
                    _events.emit(FriendsSingleEvent.RequestRejected("Request rejected"))
                }
                .onFailure { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to reject friend request")
                        _events.emit(
                            FriendsSingleEvent.ShowSnackbar(
                                e.localizedMessage ?: "Failed to reject request"
                            )
                        )
                    }
                }
        }
    }

    fun removeFriend(friend: FriendWithProfile) {
        viewModelScope.launch {
            friendRepository.removeFriend(friend.friendshipId)
                .onSuccess {
                    _events.emit(FriendsSingleEvent.FriendRemoved(friend.profile.nickname))
                }
                .onFailure { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to remove friend")
                        _events.emit(
                            FriendsSingleEvent.ShowSnackbar(
                                e.localizedMessage ?: "Failed to remove friend"
                            )
                        )
                    }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadFriendsWithIds()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("FriendsViewModel: cleared")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel Factory
// ═══════════════════════════════════════════════════════════════════════════════

class FriendsViewModelFactory(
    private val friendRepository: FriendRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FriendsViewModel(friendRepository, userRepository, auth) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  FriendsScreen — Main composable with tab layout
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToChat: (chatId: String) -> Unit = {},
    viewModel: FriendsViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        viewModel(factory = FriendsViewModelFactory(
            friendRepository = app.appModule.friendRepository,
            userRepository = app.appModule.userRepository,
            auth = app.appModule.firebaseAuth
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var friendToRemove by remember { mutableStateOf<FriendWithProfile?>(null) }

    // Handle single events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendsSingleEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is FriendsSingleEvent.RequestSent -> {
                    snackbarHostState.showSnackbar(
                        "Friend request sent to ${event.nickname}!",
                        duration = SnackbarDuration.Short
                    )
                }
                is FriendsSingleEvent.RequestAccepted -> {
                    snackbarHostState.showSnackbar(
                        "Friend request accepted!",
                        duration = SnackbarDuration.Short
                    )
                }
                is FriendsSingleEvent.RequestRejected -> {
                    snackbarHostState.showSnackbar(
                        "Friend request rejected",
                        duration = SnackbarDuration.Short
                    )
                }
                is FriendsSingleEvent.FriendRemoved -> {
                    snackbarHostState.showSnackbar(
                        "${event.nickname} removed from friends",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        Timber.d("FriendsScreen: composed")
        onDispose { Timber.d("FriendsScreen: disposed") }
    }

    val tabs = listOf("Friends", "Requests")
    val incomingCount = uiState.incomingRequests.size

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPrimary)
        ) {
            // ── Top App Bar ────────────────────────────────────────────────
            TopAppBar(
                title = {
                    Text(
                        text = "Friends",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add friend",
                            tint = AccentPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
            )

            // ── Tab Row ────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BgPrimary,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 3.dp,
                        color = AccentPrimary
                    )
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedTab == index) FontWeight.Bold
                                        else FontWeight.Normal
                                    )
                                )
                                if (index == 1 && incomingCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentPrimary
                                    ) {
                                        Text(
                                            text = incomingCount.toString(),
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp, vertical = 2.dp
                                            ),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = BgPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        selectedContentColor = AccentPrimary,
                        unselectedContentColor = TextSecondary
                    )
                }
            }

            // ── Tab Content ────────────────────────────────────────────────
            when (selectedTab) {
                0 -> FriendsListTab(
                    friends = uiState.friends,
                    isLoading = uiState.isLoading,
                    currentUserId = try {
                        FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    } catch (_: Exception) { "" },
                    onFriendClick = { friend ->
                        // Use deterministic chat ID from both user IDs
                        val chatId = Chat.chatId(
                            try {
                                FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            } catch (_: Exception) { "" },
                            friend.userId
                        )
                        onNavigateToChat(chatId)
                    },
                    onFriendRemove = { friend ->
                        friendToRemove = friend
                    }
                )

                1 -> RequestsTab(
                    incomingRequests = uiState.incomingRequests,
                    outgoingRequests = uiState.outgoingRequests,
                    isLoading = uiState.isLoading,
                    onAccept = { request -> viewModel.acceptRequest(request) },
                    onReject = { request -> viewModel.rejectRequest(request) }
                )
            }
        }

        // ── Search Dialog ──────────────────────────────────────────────────
        if (showSearchDialog) {
            SearchUserDialog(
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearching = uiState.isSearching,
                isSendingRequest = uiState.isSendingRequest,
                onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onSendRequest = { result -> viewModel.sendFriendRequest(result) },
                onDismiss = {
                    showSearchDialog = false
                    viewModel.onSearchQueryChanged("")
                }
            )
        }

        // ── Remove Friend Confirmation ─────────────────────────────────────
        friendToRemove?.let { friend ->
            AlertDialog(
                onDismissRequest = { friendToRemove = null },
                title = {
                    Text(
                        text = "Remove Friend",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove " +
                                "${friend.profile.nickname} from your friends? " +
                                "You can always send them a new request later.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeFriend(friend)
                            friendToRemove = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SemanticError
                        )
                    ) {
                        Text("Remove", color = TextPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendToRemove = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BgSurface,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary
            )
        }

        // ── Snackbar ───────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Friends List Tab
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FriendsListTab(
    friends: List<FriendWithProfile>,
    isLoading: Boolean,
    currentUserId: String,
    onFriendClick: (FriendWithProfile) -> Unit,
    onFriendRemove: (FriendWithProfile) -> Unit
) {
    if (isLoading) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(5) { FriendItemSkeleton() }
        }
        return
    }

    if (friends.isEmpty()) {
        EmptyFriendsState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = friends,
            key = { it.userId }
        ) { friend ->
            FriendItem(
                friend = friend,
                onClick = { onFriendClick(friend) },
                onRemove = { onFriendRemove(friend) }
            )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Friend Item — Avatar, nickname, days smoke-free, online status, remove
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FriendItem(
    friend: FriendWithProfile,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val profile = friend.profile

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Avatar with online indicator ───────────────────────────────
            Box {
                Card(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = AccentPrimary.copy(alpha = 0.15f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    if (profile.photoURL != null) {
                        AsyncImage(
                            model = profile.photoURL,
                            contentDescription = "${profile.nickname}'s avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.nickname.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = AccentPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
                // Online indicator dot (shows if user has daysSmokeFree > 0
                // as a proxy for active status; real app uses Firestore presence)
                if (profile.daysSmokeFree > 0) {
                    Surface(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd),
                        shape = CircleShape,
                        color = AccentPrimary,
                        border = BorderStroke(2.dp, BgSurface)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Info Column ────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.nickname,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Days smoke-free badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${profile.daysSmokeFree}d smoke-free",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    // XP level badge
                    val level = com.breathy.data.models.User.computeLevel(profile.xp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentSecondary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Lv.$level",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    // Location badge if available
                    if (profile.location != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgSurfaceVariant
                        ) {
                            Text(
                                text = profile.location!!,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            // ── Remove Button (swipe-to-delete alternative) ────────────────
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${profile.nickname}",
                    tint = TextDisabled,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Friend Item Skeleton — Loading placeholder
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FriendItemSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = BgSurfaceVariant
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Surface(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = BgSurfaceVariant
                ) {}
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = BgSurfaceVariant
                ) {}
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Empty Friends State
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyFriendsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "empty_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "empty_scale"
            )

            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = AccentPrimary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = AccentPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Friends Yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add friends to support each other\non your quit-smoking journey!",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Requests Tab — Incoming & Outgoing friend requests
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RequestsTab(
    incomingRequests: List<FriendRequest>,
    outgoingRequests: List<FriendRequest>,
    isLoading: Boolean,
    onAccept: (FriendRequest) -> Unit,
    onReject: (FriendRequest) -> Unit
) {
    if (isLoading) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(3) { RequestItemSkeleton() }
        }
        return
    }

    if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
        EmptyRequestsState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Incoming requests section
        if (incomingRequests.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Incoming (${incomingRequests.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = AccentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            items(
                items = incomingRequests,
                key = { it.id }
            ) { request ->
                IncomingRequestItem(
                    request = request,
                    onAccept = { onAccept(request) },
                    onReject = { onReject(request) }
                )
            }
        }

        // Outgoing requests section
        if (outgoingRequests.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Outgoing (${outgoingRequests.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            items(
                items = outgoingRequests,
                key = { it.id }
            ) { request ->
                OutgoingRequestItem(request = request)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Incoming Request Item — Accept/Reject buttons
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun IncomingRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = AccentPrimary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "User #${request.fromUserId.take(6)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatRequestTime(request),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            // Accept button
            IconButton(
                onClick = onAccept,
                modifier = Modifier.size(36.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = AccentPrimary.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Accept friend request",
                            tint = AccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Reject button
            IconButton(
                onClick = onReject,
                modifier = Modifier.size(36.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SemanticError.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Reject friend request",
                            tint = SemanticError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Outgoing Request Item — Pending status
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun OutgoingRequestItem(
    request: FriendRequest
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = TextSecondary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "User #${request.toUserId.take(6)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pending \u00B7 ${formatRequestTime(request)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            // Pending badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TextSecondary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Pending",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Request Item Skeleton — Loading placeholder
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RequestItemSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = BgSurfaceVariant
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Surface(
                    modifier = Modifier.width(100.dp).height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = BgSurfaceVariant
                ) {}
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.width(60.dp).height(10.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = BgSurfaceVariant
                ) {}
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Empty Requests State
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyRequestsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = TextSecondary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Friend Requests",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "When someone sends you a friend\nrequest, it will appear here.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Search User Dialog — Add friend by nickname
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchUserDialog(
    searchQuery: String,
    searchResults: List<SearchResult>,
    isSearching: Boolean,
    isSendingRequest: Boolean,
    onQueryChanged: (String) -> Unit,
    onSendRequest: (SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Friend",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Search by nickname to find and add friends",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChanged,
                    label = { Text("Nickname") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = BgSurfaceVariant,
                        focusedLabelColor = AccentPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = AccentPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Loading indicator
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Search results
                if (searchResults.isNotEmpty() && !isSearching) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = searchResults,
                            key = { it.userId }
                        ) { result ->
                            SearchResultItem(
                                result = result,
                                isSending = isSendingRequest,
                                onAdd = { onSendRequest(result) }
                            )
                        }
                    }
                } else if (searchQuery.isNotBlank() && !isSearching && searchResults.isEmpty()) {
                    Text(
                        text = "No users found with that nickname",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextDisabled
                        ),
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentPrimary)
            }
        },
        containerColor = BgSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Search Result Item
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchResultItem(
    result: SearchResult,
    isSending: Boolean,
    onAdd: () -> Unit
) {
    val profile = result.profile

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Card(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = AccentPrimary.copy(alpha = 0.15f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                if (profile.photoURL != null) {
                    AsyncImage(
                        model = profile.photoURL,
                        contentDescription = "${profile.nickname}'s avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.nickname.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.nickname,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${profile.daysSmokeFree}d smoke-free",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                )
            }

            // Add button
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AccentPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onAdd) {
                    Surface(
                        shape = CircleShape,
                        color = AccentPrimary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Add ${profile.nickname}",
                                tint = AccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Utility Functions
// ═══════════════════════════════════════════════════════════════════════════════

/** Format the time since a friend request was sent. */
private fun formatRequestTime(request: FriendRequest): String {
    val sentMillis = request.timestamp.toDate().time
    val diffMillis = System.currentTimeMillis() - sentMillis
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        minutes < 10080 -> "${minutes / 1440}d ago"
        else -> "${minutes / 10080}w ago"
    }
}

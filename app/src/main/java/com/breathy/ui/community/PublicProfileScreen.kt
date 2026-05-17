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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.coil3.compose.AsyncImage
import io.coil3.request.ImageRequest
import com.breathy.BreathyApplication
import com.breathy.data.models.PublicProfile
import com.breathy.data.models.RequestStatus
import com.breathy.data.models.Story
import com.breathy.data.models.User
import com.breathy.data.repository.FriendRepository
import com.breathy.data.repository.StoryRepository
import com.breathy.data.repository.UserRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.IconButton

// ═══════════════════════════════════════════════════════════════════════════════
//  PublicProfileScreen — View another user's public profile
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Screen for viewing another user's public profile.
 *
 * Features:
 * - Avatar, nickname, days smoke-free
 * - XP level badge
 * - Location (if shared)
 * - Quit date
 * - Their stories (paginated)
 * - Add friend / Message button
 * - Friend request status indicator
 *
 * @param userId           The ID of the user whose profile to view.
 * @param onNavigateBack   Navigate back callback.
 * @param onNavigateToStoryDetail Navigate to story detail with storyId.
 * @param onNavigateToChat Navigate to chat with chatId.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onNavigateToStoryDetail: (String) -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val application = LocalContext.current.applicationContext as BreathyApplication
    val viewModel: PublicProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = PublicProfileViewModelFactory(
            userRepository = application.appModule.userRepository,
            storyRepository = application.appModule.storyRepository,
            friendRepository = application.appModule.friendRepository,
            profileUserId = userId
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        onDispose {
            Timber.d("PublicProfileScreen disposed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.profile?.nickname ?: "Profile",
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
        if (uiState.isLoading && uiState.profile == null) {
            // Loading state
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
        } else if (uiState.profile == null && uiState.error != null) {
            // Error state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ Failed to load profile",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error ?: "",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { viewModel.retry() },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Retry", color = AccentPrimary)
                    }
                }
            }
        } else if (uiState.profile != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Profile header ─────────────────────────────────────────
                item {
                    ProfileHeader(profile = uiState.profile!!)
                }

                // ── Action buttons ────────────────────────────────────────
                item {
                    ProfileActions(
                        friendStatus = uiState.friendStatus,
                        isSendingRequest = uiState.isSendingRequest,
                        onAddFriend = viewModel::sendFriendRequest,
                        onMessage = {
                            // Generate chat ID deterministically
                            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@ProfileActions
                            val chatId = com.breathy.data.models.Chat.chatId(currentUid, userId)
                            onNavigateToChat(chatId)
                        }
                    )
                }

                // ── Stories header ────────────────────────────────────────
                item {
                    Text(
                        text = "Stories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ── User's stories ────────────────────────────────────────
                if (uiState.stories.isEmpty() && !uiState.isLoadingStories) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No stories yet",
                                color = TextDisabled,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(
                        items = uiState.stories,
                        key = { it.id }
                    ) { story ->
                        StoryCard(
                            story = story,
                            isLiked = false, // Not tracking likes from this screen
                            onLikeClick = { /* handled in detail */ },
                            onClick = { onNavigateToStoryDetail(story.id) },
                            onAvatarClick = { /* already on this profile */ }
                        )
                    }

                    if (uiState.isLoadingStories) {
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
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileHeader(profile: PublicProfile) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profile.photoURL?.takeIf { it.isNotBlank() })
                    
                    .build(),
                contentDescription = "${profile.nickname}'s avatar",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Nickname
            Text(
                text = profile.nickname,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Days smoke-free + Level badge row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Days smoke-free
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (profile.daysSmokeFree == 0) "Day 1" else "${profile.daysSmokeFree} days smoke-free",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = AccentPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // XP Level badge
                val level = User.computeLevel(profile.xp)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentPurple.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Lv.$level",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = AccentPurple,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Location (if shared)
            if (!profile.location.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics {
                        contentDescription = "Location: ${profile.location}"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = profile.location,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Quit date
            profile.quitDate?.let { qd ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics {
                        contentDescription = "Quit date: ${formatQuitDate(qd.toDate())}"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Quit on ${formatQuitDate(qd.toDate())}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActions(
    friendStatus: FriendStatus,
    isSendingRequest: Boolean,
    onAddFriend: () -> Unit,
    onMessage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Add friend button (varies by status)
        when (friendStatus) {
            FriendStatus.SELF -> {
                // Don't show action buttons for own profile
            }
            FriendStatus.FRIENDS -> {
                // Already friends — show message button full width
                Button(
                    onClick = onMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .semantics {
                            contentDescription = "Send message"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentSecondary,
                        contentColor = BgPrimary
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message", fontWeight = FontWeight.SemiBold)
                }
            }
            FriendStatus.REQUEST_SENT -> {
                // Pending request
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .semantics {
                            contentDescription = "Friend request pending"
                            role = Role.Button
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = TextDisabled
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAddDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pending", fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onMessage,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .semantics {
                            contentDescription = "Send message"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentSecondary,
                        contentColor = BgPrimary
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message", fontWeight = FontWeight.SemiBold)
                }
            }
            FriendStatus.REQUEST_RECEIVED -> {
                // Received a request — show accept button
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .semantics {
                            contentDescription = "Friend request received"
                            role = Role.Button
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept", fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onMessage,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentSecondary,
                        contentColor = BgPrimary
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message", fontWeight = FontWeight.SemiBold)
                }
            }
            FriendStatus.NOT_FRIENDS -> {
                // Not friends — show add friend + message
                Button(
                    onClick = onAddFriend,
                    enabled = !isSendingRequest,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .semantics {
                            contentDescription = "Send friend request"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        contentColor = BgPrimary,
                        disabledContainerColor = AccentPrimary.copy(alpha = 0.3f),
                        disabledContentColor = TextDisabled
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    if (isSendingRequest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = BgPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSendingRequest) "Sending..." else "Add Friend",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .semantics {
                            contentDescription = "Send message"
                            role = Role.Button
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Utility Functions
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatQuitDate(date: Date): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(date)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Friend Status Enum
// ═══════════════════════════════════════════════════════════════════════════════

enum class FriendStatus {
    SELF,           // Viewing own profile
    FRIENDS,        // Already friends
    REQUEST_SENT,   // Friend request sent (pending)
    REQUEST_RECEIVED, // Friend request received
    NOT_FRIENDS     // No relationship
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class PublicProfileUiState(
    val profile: PublicProfile? = null,
    val stories: List<Story> = emptyList(),
    val friendStatus: FriendStatus = FriendStatus.NOT_FRIENDS,
    val isLoading: Boolean = true,
    val isLoadingStories: Boolean = false,
    val isSendingRequest: Boolean = false,
    val error: String? = null
)

class PublicProfileViewModel(
    private val userRepository: UserRepository,
    private val storyRepository: StoryRepository,
    private val friendRepository: FriendRepository,
    private val profileUserId: String
) : ViewModel() {

    companion object {
        private const val TAG = "PublicProfileViewModel"
        private const val STORIES_PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(PublicProfileUiState())
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    private val currentUserId: String? by lazy {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Check if viewing own profile
            if (profileUserId == currentUserId) {
                _uiState.value = _uiState.value.copy(
                    friendStatus = FriendStatus.SELF,
                    isLoading = false
                )
            }

            // Load public profile
            userRepository.getPublicProfile(profileUserId).fold(
                onSuccess = { profile ->
                    _uiState.value = _uiState.value.copy(
                        profile = profile,
                        isLoading = false
                    )
                    // After loading profile, check friend status and load stories
                    checkFriendStatus()
                    loadUserStories()
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load public profile: %s", profileUserId)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Failed to load profile"
                        )
                    }
                }
            )
        }
    }

    private fun checkFriendStatus() {
        val uid = currentUserId ?: return
        if (profileUserId == uid) {
            _uiState.value = _uiState.value.copy(friendStatus = FriendStatus.SELF)
            return
        }

        viewModelScope.launch {
            // Check if already friends
            friendRepository.isFriend(profileUserId).fold(
                onSuccess = { isFriend ->
                    if (isFriend) {
                        _uiState.value = _uiState.value.copy(friendStatus = FriendStatus.FRIENDS)
                    } else {
                        // Check if request was sent by current user
                        val outgoingResult = friendRepository.getOutgoingRequests()
                        val sentRequest = outgoingResult.getOrNull()?.find {
                            it.toUserId == profileUserId && it.status == RequestStatus.PENDING
                        }
                        if (sentRequest != null) {
                            _uiState.value = _uiState.value.copy(friendStatus = FriendStatus.REQUEST_SENT)
                        } else {
                            // Check if request was received from this user
                            val incomingResult = friendRepository.getIncomingRequests()
                            val receivedRequest = incomingResult.getOrNull()?.find {
                            it.fromUserId == profileUserId && it.status == RequestStatus.PENDING
                            }
                            if (receivedRequest != null) {
                                _uiState.value = _uiState.value.copy(friendStatus = FriendStatus.REQUEST_RECEIVED)
                            } else {
                                _uiState.value = _uiState.value.copy(friendStatus = FriendStatus.NOT_FRIENDS)
                            }
                        }
                    }
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to check friend status")
                        _uiState.value = _uiState.value.copy(friendStatus = FriendStatus.NOT_FRIENDS)
                    }
                }
            )
        }
    }

    private fun loadUserStories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStories = true)

            storyRepository.getStoriesByUser(
                userId = profileUserId,
                limit = STORIES_PAGE_SIZE
            ).fold(
                onSuccess = { stories ->
                    _uiState.value = _uiState.value.copy(
                        stories = stories,
                        isLoadingStories = false
                    )
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load user stories")
                        _uiState.value = _uiState.value.copy(isLoadingStories = false)
                    }
                }
            )
        }
    }

    fun sendFriendRequest() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingRequest = true)

            friendRepository.sendRequest(profileUserId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSendingRequest = false,
                        friendStatus = FriendStatus.REQUEST_SENT
                    )
                    Timber.d("Friend request sent to: %s", profileUserId)
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to send friend request")
                        _uiState.value = _uiState.value.copy(isSendingRequest = false)
                    }
                }
            )
        }
    }

    fun retry() {
        loadProfile()
    }
}

class PublicProfileViewModelFactory(
    private val userRepository: UserRepository,
    private val storyRepository: StoryRepository,
    private val friendRepository: FriendRepository,
    private val profileUserId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PublicProfileViewModel::class.java)) {
            return PublicProfileViewModel(
                userRepository,
                storyRepository,
                friendRepository,
                profileUserId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

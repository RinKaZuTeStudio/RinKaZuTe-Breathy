package com.breathy.ui.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import coil.compose.AsyncImage
import com.breathy.BreathyApplication
import com.breathy.data.models.Chat
import com.breathy.data.models.Message
import com.breathy.data.models.PublicProfile
import com.breathy.data.repository.ChatRepository
import com.breathy.data.repository.FriendRepository
import com.breathy.data.repository.UserRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.material3.Card

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class ChatUiState(
    val isLoading: Boolean = true,
    val messages: List<Message> = emptyList(),
    val chat: Chat? = null,
    val otherUserProfile: PublicProfile? = null,
    val isOtherUserTyping: Boolean = false,
    val isOtherUserOnline: Boolean = false,
    val inputText: String = "",
    val isSending: Boolean = false,
    val isSendingTyping: Boolean = false,
    val errorMessage: String? = null,
    val hasOlderMessages: Boolean = true,
    val isLoadingOlder: Boolean = false
)

sealed class ChatSingleEvent {
    data class ShowSnackbar(val message: String) : ChatSingleEvent()
    object MessageSent : ChatSingleEvent()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class ChatViewModel(
    private val chatId: String,
    private val chatRepository: ChatRepository,
    private val friendRepository: FriendRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatSingleEvent>()
    val events: SharedFlow<ChatSingleEvent> = _events.asSharedFlow()

    private var typingJob: kotlinx.coroutines.Job? = null
    private val currentUserId: String
        get() = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        } catch (_: Exception) {
            ""
        }

    init {
        loadChat()
        observeMessages()
    }

    internal fun loadChat() {
        viewModelScope.launch {
            // Get chat details
            val chatResult = chatRepository.getOrCreateChat(chatId)
            chatResult.onSuccess { chat ->
                _uiState.update { it.copy(chat = chat) }

                // Determine the other user
                val otherUserId = chat.otherParticipant(currentUserId) ?: return@onSuccess

                // Load other user's profile
                val profileResult = userRepository.getPublicProfile(otherUserId)
                profileResult.onSuccess { profile ->
                    _uiState.update { it.copy(otherUserProfile = profile, isLoading = false) }
                }.onFailure { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load other user profile")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }

                // Mark messages as read
                chatRepository.markAsRead(chatId)
            }.onFailure { e ->
                if (e !is CancellationException) {
                    Timber.e(e, "Failed to load chat")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load chat") }
                }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }

                // Mark as read when messages are observed
                chatRepository.markAsRead(chatId)
            }
        }

        // Observe chat for typing indicators
        viewModelScope.launch {
            // We observe the chat document for typing updates
            // Since observeChats() returns ALL chats, we filter
            chatRepository.observeChats().collect { chats ->
                val currentChat = chats.find { it.id == chatId }
                if (currentChat != null) {
                    val otherUserId = currentChat.otherParticipant(currentUserId) ?: ""
                    val isTyping = currentChat.isTyping(otherUserId)
                    _uiState.update { it.copy(isOtherUserTyping = isTyping) }
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }

        // Send typing indicator
        if (text.isNotBlank()) {
            sendTypingIndicator(true)
            // Schedule clearing typing after 3 seconds of inactivity
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                delay(3000)
                sendTypingIndicator(false)
            }
        } else {
            typingJob?.cancel()
            sendTypingIndicator(false)
        }
    }

    private fun sendTypingIndicator(isTyping: Boolean) {
        viewModelScope.launch {
            chatRepository.setTyping(chatId, isTyping)
                .onFailure { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to set typing indicator")
                    }
                }
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }

            // Clear typing indicator
            typingJob?.cancel()
            sendTypingIndicator(false)

            chatRepository.sendMessage(chatId, text)
                .onSuccess {
                    _uiState.update { it.copy(inputText = "", isSending = false) }
                }
                .onFailure { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to send message")
                        _uiState.update { it.copy(isSending = false) }
                        _events.emit(ChatSingleEvent.ShowSnackbar("Failed to send message"))
                    }
                }
        }
    }

    fun loadOlderMessages() {
        if (_uiState.value.isLoadingOlder || !_uiState.value.hasOlderMessages) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true) }
            val result = chatRepository.getMessages(chatId, limit = 100)
            result.onSuccess { olderMessages ->
                val currentIds = _uiState.value.messages.map { it.id }.toSet()
                val newMessages = olderMessages.filter { it.id !in currentIds }
                _uiState.update {
                    it.copy(
                        messages = (newMessages + it.messages).sortedBy { msg -> msg.timestamp.seconds },
                        hasOlderMessages = newMessages.isNotEmpty() && olderMessages.size >= 50,
                        isLoadingOlder = false
                    )
                }
            }.onFailure { e ->
                if (e !is CancellationException) {
                    _uiState.update { it.copy(isLoadingOlder = false) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        typingJob?.cancel()
        // Clear typing indicator on leave
        viewModelScope.launch {
            chatRepository.setTyping(chatId, false)
        }
        Timber.d("ChatViewModel: cleared")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel Factory
// ═══════════════════════════════════════════════════════════════════════════════

class ChatViewModelFactory(
    private val chatId: String,
    private val chatRepository: ChatRepository,
    private val friendRepository: FriendRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(chatId, chatRepository, friendRepository, userRepository) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ChatScreen — 1:1 Chat composable
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: ChatViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        viewModel(factory = ChatViewModelFactory(
            chatId = chatId,
            chatRepository = app.appModule.chatRepository,
            friendRepository = app.appModule.friendRepository,
            userRepository = app.appModule.userRepository
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        Timber.d("ChatScreen: composed with chatId=%s", chatId)
        onDispose { Timber.d("ChatScreen: disposed") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // ── Top Bar with Online Status ─────────────────────────────────────
        ChatTopBar(
            otherUserProfile = uiState.otherUserProfile,
            isOnline = uiState.isOtherUserOnline,
            isTyping = uiState.isOtherUserTyping,
            onNavigateBack = onNavigateBack
        )

        // ── Messages List ──────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = AccentPrimary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading messages...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary
                            )
                        )
                    }
                }
            } else if (uiState.errorMessage != null) {
                // Error state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AccentPrimary
                        ) {
                            Text(
                                text = "Retry",
                                modifier = Modifier
                                    .clickable { viewModel.loadChat() }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                color = BgPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Message list
                MessageList(
                    messages = uiState.messages,
                    currentUserId = try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    } catch (_: Exception) { "" },
                    otherUserProfile = uiState.otherUserProfile,
                    isOtherTyping = uiState.isOtherUserTyping,
                    isLoadingOlder = uiState.isLoadingOlder,
                    onLoadOlder = { viewModel.loadOlderMessages() }
                )
            }
        }

        // ── Typing Indicator ───────────────────────────────────────────────
        AnimatedVisibility(visible = uiState.isOtherUserTyping) {
            TypingIndicatorBar(otherUserName = uiState.otherUserProfile?.nickname ?: "User")
        }

        // ── Input Bar ──────────────────────────────────────────────────────
        MessageInputBar(
            text = uiState.inputText,
            isSending = uiState.isSending,
            onTextChanged = { viewModel.onInputTextChanged(it) },
            onSend = { viewModel.sendMessage() }
        )
    }

    // Snackbar
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(bottom = 56.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Chat Top Bar
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    otherUserProfile: PublicProfile?,
    isOnline: Boolean,
    isTyping: Boolean,
    onNavigateBack: () -> Unit
) {
    Surface(
        color = BgSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = TextPrimary
                )
            }

            // Avatar
            Card(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = AccentPrimary.copy(alpha = 0.15f)
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                if (otherUserProfile?.photoURL != null) {
                    AsyncImage(
                        model = otherUserProfile.photoURL,
                        contentDescription = "${otherUserProfile.nickname}'s avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (otherUserProfile?.nickname ?: "?").take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name + Status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = otherUserProfile?.nickname ?: "Chat",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Status line
                val statusText = when {
                    isTyping -> "typing..."
                    isOnline -> "Online"
                    else -> "${otherUserProfile?.daysSmokeFree ?: 0}d smoke-free"
                }
                val statusColor = when {
                    isTyping -> AccentPrimary
                    isOnline -> AccentPrimary
                    else -> TextSecondary
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOnline || isTyping) {
                        Surface(
                            modifier = Modifier.size(6.dp),
                            shape = CircleShape,
                            color = AccentPrimary
                        ) {}
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Message List with auto-scroll and timestamp grouping
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MessageList(
    messages: List<Message>,
    currentUserId: String,
    otherUserProfile: PublicProfile?,
    isOtherTyping: Boolean,
    isLoadingOlder: Boolean,
    onLoadOlder: () -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Group messages by date for timestamp headers
    val groupedMessages = remember(messages) {
        groupMessagesByDate(messages)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastIndex = groupedMessages.size - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    // Pull-to-load-older detection
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex == 0 && messages.size > 20) {
            onLoadOlder()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Loading older indicator
        if (isLoadingOlder) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
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

        // Messages with date headers
        items(
            items = groupedMessages,
            key = { item ->
                when (item) {
                    is ChatListItem.DateHeader -> "date_${item.dateText}"
                    is ChatListItem.MessageItem -> "msg_${item.message.id}"
                }
            }
        ) { item ->
            when (item) {
                is ChatListItem.DateHeader -> {
                    DateHeader(dateText = item.dateText)
                }
                is ChatListItem.MessageItem -> {
                    val isFromCurrentUser = item.message.senderId == currentUserId
                    val showTail = item.showTail

                    MessageBubble(
                        message = item.message,
                        isFromCurrentUser = isFromCurrentUser,
                        showTail = showTail
                    )
                }
            }
        }

        // Typing indicator at bottom
        if (isOtherTyping) {
            item {
                TypingBubble(otherUserName = otherUserProfile?.nickname ?: "User")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Message Bubble — Sent (right/neon) and Received (left/dark)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MessageBubble(
    message: Message,
    isFromCurrentUser: Boolean,
    showTail: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        // Spacer between different senders
        if (showTail) {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isFromCurrentUser) 4.dp else 16.dp
            ),
            color = if (isFromCurrentUser) {
                AccentPrimary.copy(alpha = 0.15f)
            } else {
                BgSurface
            },
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Message text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isFromCurrentUser) TextPrimary else TextPrimary,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Timestamp + Read receipt row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMessageTime(message),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextDisabled,
                            fontSize = 10.sp
                        )
                    )

                    // Read receipts for sent messages
                    if (isFromCurrentUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (message.read) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = if (message.read) "Read" else "Sent",
                            tint = if (message.read) AccentPrimary else TextDisabled,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Date Header — Timestamp grouping (Today, Yesterday, date)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DateHeader(dateText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgSurfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = dateText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Typing Indicator Bubble — Animated dots
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TypingBubble(otherUserName: String) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp
        ),
        color = BgSurface,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TypingDots()
        }
    }
}

@Composable
private fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0.2f at 0
                1f at 300
                0.2f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0.2f at 100
                1f at 400
                0.2f at 700
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0.2f at 200
                1f at 500
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypingDot(alpha = dot1Alpha)
        TypingDot(alpha = dot2Alpha)
        TypingDot(alpha = dot3Alpha)
    }
}

@Composable
private fun TypingDot(alpha: Float) {
    Surface(
        modifier = Modifier.size(6.dp),
        shape = CircleShape,
        color = TextSecondary.copy(alpha = alpha)
    ) {}
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Typing Indicator Bar — Full-width indicator above input
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TypingIndicatorBar(otherUserName: String) {
    Surface(
        color = BgPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TypingDots()
            Text(
                text = "$otherUserName is typing",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = AccentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Message Input Bar — Text field with send button
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MessageInputBar(
    text: String,
    isSending: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = BgSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        text = "Type a message...",
                        color = TextDisabled
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = BgSurfaceVariant,
                    focusedContainerColor = BgSurfaceVariant,
                    unfocusedContainerColor = BgSurfaceVariant,
                    cursorColor = AccentPrimary
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // Send button
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        enabled = text.isNotBlank() && !isSending,
                        onClick = onSend
                    ),
                shape = CircleShape,
                color = if (text.isNotBlank() && !isSending) AccentPrimary else AccentPrimary.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BgPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = BgPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Message Grouping — Date headers + tail logic
// ═══════════════════════════════════════════════════════════════════════════════

private sealed class ChatListItem {
    data class DateHeader(val dateText: String) : ChatListItem()
    data class MessageItem(
        val message: Message,
        val showTail: Boolean
    ) : ChatListItem()
}

private fun groupMessagesByDate(messages: List<Message>): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()

    val items = mutableListOf<ChatListItem>()
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    var lastSenderId: String? = null
    var lastDateStr: String? = null

    for (message in messages) {
        // Date grouping
        val messageDate = Calendar.getInstance().apply {
            timeInMillis = message.timestamp.toDate().time
        }
        val dateStr = when {
            isSameDay(messageDate, today) -> "Today"
            isSameDay(messageDate, yesterday) -> "Yesterday"
            else -> dateFormat.format(messageDate.time)
        }

        if (dateStr != lastDateStr) {
            items.add(ChatListItem.DateHeader(dateStr))
            lastDateStr = dateStr
            lastSenderId = null // Reset sender on new date
        }

        // Determine if this is a "tail" (first message from this sender in a group)
        val showTail = message.senderId != lastSenderId
        items.add(ChatListItem.MessageItem(message, showTail))
        lastSenderId = message.senderId
    }

    return items
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Utility Functions
// ═══════════════════════════════════════════════════════════════════════════════

/** Format message timestamp as HH:mm. */
private fun formatMessageTime(message: Message): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(message.timestamp.toDate())
}

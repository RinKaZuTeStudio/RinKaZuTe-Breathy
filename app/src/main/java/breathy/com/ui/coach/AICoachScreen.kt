@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package breathy.com.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import breathy.com.BreathyApplication
import breathy.com.data.models.CoachMessage
import breathy.com.data.models.MessageRole
import breathy.com.data.repository.CoachRepository
import breathy.com.data.repository.UserRepository
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.SemanticError
import breathy.com.ui.theme.SemanticWarning
import breathy.com.ui.theme.themeTextDisabled
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════════════
//  AICoachScreen — Chat interface with AI coach
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AICoachScreen(
    onBack: () -> Unit = {},
    viewModel: AICoachViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        viewModel(factory = AICoachViewModelFactory(
            coachRepository = app.appModule.coachRepository,
            userRepository = app.appModule.userRepository,
            auth = app.appModule.firebaseAuth
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    DisposableEffect(Unit) {
        Timber.d("AICoachScreen: composed")
        onDispose { Timber.d("AICoachScreen: disposed") }
    }

    Scaffold(
        containerColor = themeBgPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Coach avatar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AccentPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🤖", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "AI Coach",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = themeTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Your quit-smoking companion",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeTextSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = themeTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear chat history",
                            tint = themeTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeBgSurface,
                    titleContentColor = themeTextPrimary
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeBgSurface)
                    .imePadding()
            ) {
                // ── Rate Limit Indicator ──────────────────────────────────
                if (uiState.rateLimitSecondsRemaining > 0) {
                    LinearProgressIndicator(
                        progress = {
                            1f - (uiState.rateLimitSecondsRemaining.toFloat() / 60f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = SemanticWarning,
                        trackColor = themeBgSurfaceVariant
                    )
                    Text(
                        text = "Rate limit: wait ${uiState.rateLimitSecondsRemaining}s",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SemanticWarning,
                            fontSize = 10.sp
                        )
                    )
                }

                // ── Quick Suggestion Chips ────────────────────────────────
                if (uiState.messages.isEmpty() && inputText.isBlank()) {
                    LazyRowOfChips(
                        suggestions = uiState.contextSuggestions,
                        onSuggestionClick = { suggestion ->
                            inputText = ""
                            viewModel.sendMessage(suggestion)
                        }
                    )
                }

                // ── Message Input Bar ─────────────────────────────────────
                MessageInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    isSending = uiState.isSending,
                    isRateLimited = uiState.rateLimitSecondsRemaining > 0
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Disclaimer Banner ────────────────────────────────────────
            DisclaimerBanner()

            // ── Loading State ─────────────────────────────────────────────
            if (uiState.isLoading && uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading conversation...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                        )
                    }
                }
                return@Column
            }

            // ── Error State ──────────────────────────────────────────────
            if (uiState.errorMessage != null && uiState.messages.isEmpty()) {
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
                                color = themeTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            onClick = { viewModel.retry() },
                            colors = CardDefaults.cardColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "Retry",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = themeBgPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                return@Column
            }

            // ── Empty State ──────────────────────────────────────────────
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🤖", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Hi! I'm your AI Coach",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = themeTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ask me anything about quitting smoking,\nmanaging cravings, or staying motivated.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = themeTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
                return@Column
            }

            // ── Message List ─────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(
                        message = message,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Typing indicator
                if (uiState.isSending) {
                    item(key = "typing_indicator") {
                        TypingIndicatorBubble()
                    }
                }
            }
        }
    }

    // ── Clear Chat History Confirmation ───────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear Chat History?",
                    color = themeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all your conversation history with the AI Coach. This cannot be undone.",
                    color = themeTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearHistory()
                    }
                ) {
                    Text(
                        text = "Clear",
                        color = SemanticError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = "Cancel", color = themeTextSecondary)
                }
            },
            containerColor = themeBgSurface
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Disclaimer Banner — Non-dismissable info at top
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DisclaimerBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SemanticWarning.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Disclaimer",
                tint = SemanticWarning,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "AI Coach provides general guidance, not medical advice. Consult a healthcare professional for medical concerns.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SemanticWarning,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Message Bubble — User (right) or Assistant (left)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MessageBubble(
    message: CoachMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.isFromUser()
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) AccentPrimary.copy(alpha = 0.15f) else themeBgSurface
    val textColor = if (isUser) themeTextPrimary else themeTextPrimary
    val timeText = formatMessageTime(message)

    Column(
        modifier = modifier,
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            // Assistant avatar
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🤖", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(
                horizontalAlignment = alignment,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = themeTextDisabled,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // User avatar
            if (isUser) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentPurple.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "😊", fontSize = 14.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Typing Indicator — Animated dots while waiting for AI response
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0.3f at 0
                1f at 300
                0.3f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0.3f at 100
                1f at 400
                0.3f at 700
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0.3f at 200
                1f at 500
                0.3f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AccentPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🤖", fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(6.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = themeBgSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = dot1Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = dot2Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = dot3Alpha))
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Quick Suggestion Chips — Common questions about quitting
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LazyRowOfChips(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(suggestions.size) { index ->
            Card(
                onClick = { onSuggestionClick(suggestions[index]) },
                colors = CardDefaults.cardColors(
                    containerColor = AccentPrimary.copy(alpha = 0.1f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = suggestions[index],
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Message Input Bar — Text field with send button
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    isRateLimited: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = if (isRateLimited) "Wait a moment..." else "Ask your coach...",
                    color = themeTextDisabled
                )
            },
            maxLines = 4,
            enabled = !isSending && !isRateLimited,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = themeTextPrimary,
                unfocusedTextColor = themeTextPrimary,
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = themeBgSurfaceVariant,
                cursorColor = AccentPrimary,
                focusedContainerColor = themeBgSurfaceVariant,
                unfocusedContainerColor = themeBgSurfaceVariant,
                disabledTextColor = themeTextDisabled,
                disabledBorderColor = themeBgSurfaceVariant,
                disabledContainerColor = themeBgSurfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp)
        )

        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isSending && !isRateLimited,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (text.isNotBlank() && !isSending && !isRateLimited) AccentPrimary
                    else AccentPrimary.copy(alpha = 0.3f)
                )
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = themeBgPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (text.isNotBlank() && !isRateLimited) themeBgPrimary else themeTextDisabled,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Helper Functions
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatMessageTime(message: CoachMessage): String {
    val messageMillis = message.timestamp.toDate().time
    val diffMillis = System.currentTimeMillis() - messageMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(message.timestamp.toDate())
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class AICoachUiState(
    val messages: List<CoachMessage> = emptyList(),
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val rateLimitSecondsRemaining: Int = 0,
    val contextSuggestions: List<String> = defaultSuggestions
) {
    companion object {
        val defaultSuggestions = listOf(
            "How do I handle cravings?",
            "What are withdrawal symptoms?",
            "Tips for staying motivated",
            "Benefits of quitting"
        )
    }
}

class AICoachViewModel(
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AICoachUiState())
    val uiState: StateFlow<AICoachUiState> = _uiState.asStateFlow()

    private val uid: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // Rate limit tracking
    private val messageTimestamps = mutableListOf<Long>()
    private var rateLimitJob: kotlinx.coroutines.Job? = null

    init {
        loadConversation()
        loadContextSuggestions()
    }

    private fun loadConversation() {
        viewModelScope.launch {
            try {
                coachRepository.observeConversation(limit = 100).collect { messages ->
                    _uiState.update { current ->
                        val wasSending = current.isSending
                        val newAssistantMessages = messages.count { !it.isFromUser() }
                        val oldAssistantMessages = current.messages.count { !it.isFromUser() }

                        // If we were sending and a new assistant message appeared, we're done sending
                        val shouldStopSending = wasSending && newAssistantMessages > oldAssistantMessages

                        current.copy(
                            messages = messages.distinctBy { it.id },
                            isLoading = false,
                            isSending = if (shouldStopSending) false else wasSending
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load conversation")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load conversation. Tap retry."
                    )
                }
            }
        }
    }

    private fun loadContextSuggestions() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val userResult = userRepository.getUser(userId)
                val user = userResult.getOrNull() ?: return@launch

                val suggestions = when {
                    user.daysSmokeFree < 1 -> listOf(
                        "I'm about to quit, any tips?",
                        "What should I expect on day 1?",
                        "How to prepare for quitting?",
                        "Set a quit date plan"
                    )
                    user.daysSmokeFree < 7 -> listOf(
                        "How do I handle cravings?",
                        "What are withdrawal symptoms?",
                        "Is it normal to feel anxious?",
                        "Tips for the first week"
                    )
                    user.daysSmokeFree < 30 -> listOf(
                        "When does it get easier?",
                        "How to handle social situations?",
                        "I feel stressed, help!",
                        "Benefits I should notice"
                    )
                    else -> listOf(
                        "How to stay motivated long-term?",
                        "Handling relapse triggers",
                        "Exercise and recovery tips",
                        "Am I at lower risk now?"
                    )
                }
                _uiState.update { it.copy(contextSuggestions = suggestions) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to load context suggestions, using defaults")
            }
        }
    }

    fun sendMessage(content: String) {
        // Client-side rate limit check
        val now = System.currentTimeMillis()
        messageTimestamps.removeAll { it < now - 60_000L }
        if (messageTimestamps.size >= 5) {
            val oldestInWindow = messageTimestamps.first()
            val retryAfterMs = 60_000L - (now - oldestInWindow)
            val retrySeconds = (retryAfterMs / 1000).toInt().coerceAtLeast(1)
            _uiState.update { it.copy(rateLimitSecondsRemaining = retrySeconds) }
            startRateLimitCountdown()
            return
        }
        messageTimestamps.add(now)

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            val result = coachRepository.sendMessage(content)
            result.onSuccess { coachMessage ->
                // Don't set isSending = false here!
                // Wait for the snapshot listener to deliver the assistant message.
                // The snapshot listener in loadConversation() will detect when the
                // new assistant message appears and then set isSending = false.
            }.onFailure { e ->
                Timber.e(e, "Failed to send message")
                val errorMsg = if (e is IllegalStateException && e.message?.contains("Rate limit") == true) {
                    e.message ?: "Rate limit exceeded"
                } else {
                    "Failed to send message. Please try again."
                }
                _uiState.update { it.copy(isSending = false, errorMessage = errorMsg) }
            }
        }
    }

    private fun startRateLimitCountdown() {
        rateLimitJob?.cancel()
        rateLimitJob = viewModelScope.launch {
            while (_uiState.value.rateLimitSecondsRemaining > 0) {
                delay(1000)
                _uiState.update {
                    it.copy(
                        rateLimitSecondsRemaining = (it.rateLimitSecondsRemaining - 1).coerceAtLeast(0)
                    )
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                coachRepository.clearHistory()
                messageTimestamps.clear()
                Timber.i("Chat history cleared")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear history")
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null, isLoading = true) }
        loadConversation()
    }

    override fun onCleared() {
        super.onCleared()
        rateLimitJob?.cancel()
    }
}

class AICoachViewModelFactory(
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AICoachViewModel(coachRepository, userRepository, auth) as T
    }
}

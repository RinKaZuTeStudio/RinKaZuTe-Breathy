package com.breathy.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.breathy.BreathyApplication
import com.breathy.data.models.Story
import com.breathy.data.repository.StoryRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.themeBgPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeBgSurfaceVariant
import com.breathy.ui.theme.SemanticError
import com.breathy.ui.theme.SemanticSuccess
import com.breathy.ui.theme.themeTextDisabled
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════════
//  PostStoryScreen — Create a new community story
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Screen for composing and posting a new community story.
 *
 * Features:
 * - Multi-line text input for content
 * - Optional "Life Changes" field with chip-style tags
 * - Character limit indicator (max 2000 chars content, 200 chars per life change)
 * - Live preview card showing how the story will appear
 * - Post button with loading state
 * - Cancel/dismiss via back navigation
 * - Success animation (checkmark scale) on post
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostStoryScreen(
    onNavigateBack: () -> Unit,
    onStoryPosted: () -> Unit
) {
    val application = LocalContext.current.applicationContext as BreathyApplication
    val viewModel: PostStoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = PostStoryViewModelFactory(application.appModule.storyRepository)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate back after successful post with slight delay for animation
    LaunchedEffect(uiState.postSuccess) {
        if (uiState.postSuccess) {
            kotlinx.coroutines.delay(1500)
            onStoryPosted()
        }
    }

    DisposableEffect(viewModel) {
        onDispose { Timber.d("PostStoryScreen disposed") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Share Your Story",
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
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
                            tint = themeTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeBgPrimary,
                    titleContentColor = themeTextPrimary
                )
            )
        },
        containerColor = themeBgPrimary
    ) { innerPadding ->
        // ── Success overlay ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.postSuccess,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f)
        ) {
            SuccessOverlay()
        }

        if (!uiState.postSuccess) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Content input ──────────────────────────────────────────
                ContentInput(
                    content = uiState.content,
                    onContentChange = viewModel::onContentChanged,
                    charLimit = PostStoryViewModel.MAX_CONTENT_LENGTH,
                    isError = uiState.contentError != null,
                    errorMessage = uiState.contentError
                )

                // ── Life changes input ────────────────────────────────────
                LifeChangesInput(
                    lifeChanges = uiState.lifeChanges,
                    currentChangeText = uiState.currentChangeText,
                    onCurrentChangeTextChanged = viewModel::onCurrentChangeTextChanged,
                    onAddChange = viewModel::addLifeChange,
                    onRemoveChange = viewModel::removeLifeChange,
                    charLimit = PostStoryViewModel.MAX_LIFE_CHANGE_LENGTH
                )

                // ── Preview card ──────────────────────────────────────────
                if (uiState.content.isNotBlank()) {
                    PreviewCard(
                        content = uiState.content,
                        lifeChanges = uiState.lifeChanges
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Post button ───────────────────────────────────────────
                PostButton(
                    isEnabled = uiState.content.isNotBlank() && uiState.contentError == null && !uiState.isPosting,
                    isPosting = uiState.isPosting,
                    onClick = viewModel::postStory
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ContentInput(
    content: String,
    onContentChange: (String) -> Unit,
    charLimit: Int,
    isError: Boolean,
    errorMessage: String?
) {
    Column {
        Text(
            text = "Your Story",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = themeTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = content,
            onValueChange = { text ->
                if (text.length <= charLimit) onContentChange(text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics {
                    contentDescription = "Write your story (max $charLimit characters)"
                },
            placeholder = {
                Text(
                    text = "Share your quit-smoking journey, tips, or encouragement...",
                    color = themeTextDisabled
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) SemanticError else AccentPrimary.copy(alpha = 0.5f),
                unfocusedBorderColor = if (isError) SemanticError else themeBgSurfaceVariant,
                focusedContainerColor = themeBgSurface,
                unfocusedContainerColor = themeBgSurface,
                cursorColor = AccentPrimary,
                focusedTextColor = themeTextPrimary,
                unfocusedTextColor = themeTextPrimary,
                errorBorderColor = SemanticError,
                errorTextColor = themeTextPrimary,
                errorContainerColor = themeBgSurface
            ),
            isError = isError,
            supportingText = if (isError && errorMessage != null) {
                {
                    Text(
                        text = errorMessage,
                        color = SemanticError,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else null
        )

        // Character count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            val remaining = charLimit - content.length
            Text(
                text = "$remaining",
                color = when {
                    remaining <= 0 -> SemanticError
                    remaining <= charLimit * 0.1 -> androidx.compose.ui.graphics.Color(0xFFFFD740)
                    else -> themeTextDisabled
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun LifeChangesInput(
    lifeChanges: List<String>,
    currentChangeText: String,
    onCurrentChangeTextChanged: (String) -> Unit,
    onAddChange: () -> Unit,
    onRemoveChange: (Int) -> Unit,
    charLimit: Int
) {
    Column {
        Text(
            text = "Life Changes (optional)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = themeTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "What positive changes have you noticed?",
            style = MaterialTheme.typography.bodySmall,
            color = themeTextDisabled
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Existing life changes chips
        if (lifeChanges.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                lifeChanges.forEachIndexed { index, change ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = AccentPrimary.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = change,
                                color = AccentPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { onRemoveChange(index) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove $change",
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Input for adding new life change
        if (lifeChanges.size < 5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentChangeText,
                    onValueChange = { text ->
                        if (text.length <= charLimit) onCurrentChangeTextChanged(text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Add a life change"
                        },
                    placeholder = {
                        Text(
                            text = "e.g., Better breathing",
                            color = themeTextDisabled
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary.copy(alpha = 0.5f),
                        unfocusedBorderColor = themeBgSurfaceVariant,
                        focusedContainerColor = themeBgSurface,
                        unfocusedContainerColor = themeBgSurface,
                        cursorColor = AccentPrimary,
                        focusedTextColor = themeTextPrimary,
                        unfocusedTextColor = themeTextPrimary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onAddChange,
                    enabled = currentChangeText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary.copy(alpha = 0.15f),
                        contentColor = AccentPrimary,
                        disabledContainerColor = themeBgSurfaceVariant,
                        disabledContentColor = themeTextDisabled
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        text = "Add",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = "${lifeChanges.size}/5 changes",
                style = MaterialTheme.typography.labelSmall,
                color = themeTextDisabled,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Text(
                text = "Maximum 5 life changes reached",
                style = MaterialTheme.typography.labelSmall,
                color = themeTextDisabled
            )
        }
    }
}

@Composable
private fun PreviewCard(
    content: String,
    lifeChanges: List<String>
) {
    Column {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = themeTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = themeBgSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Mock header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = themeBgSurfaceVariant
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = themeTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = themeTextSecondary,
                    maxLines = 4
                )

                if (lifeChanges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lifeChanges.forEach { change ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = themeBgSurfaceVariant
                            ) {
                                Text(
                                    text = change,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = AccentPrimary.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostButton(
    isEnabled: Boolean,
    isPosting: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics {
                contentDescription = "Post story"
                role = Role.Button
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentPrimary,
            contentColor = themeBgPrimary,
            disabledContainerColor = AccentPrimary.copy(alpha = 0.3f),
            disabledContentColor = themeTextDisabled
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        if (isPosting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = themeBgPrimary,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Posting...", fontWeight = FontWeight.SemiBold)
        } else {
            Text(text = "Post Story", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SuccessOverlay() {
    val animatedScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = tween(500),
        label = "successScale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Story posted successfully",
                tint = SemanticSuccess,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Story Shared!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = themeTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your story is now inspiring others",
                style = MaterialTheme.typography.bodyMedium,
                color = themeTextSecondary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class PostStoryUiState(
    val content: String = "",
    val contentError: String? = null,
    val lifeChanges: List<String> = emptyList(),
    val currentChangeText: String = "",
    val isPosting: Boolean = false,
    val postSuccess: Boolean = false,
    val error: String? = null
)

class PostStoryViewModel(
    private val storyRepository: StoryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PostStoryViewModel"
        const val MAX_CONTENT_LENGTH = 2000
        const val MAX_LIFE_CHANGE_LENGTH = 200
        private const val MIN_CONTENT_LENGTH = 10
    }

    private val _uiState = MutableStateFlow(PostStoryUiState())
    val uiState: StateFlow<PostStoryUiState> = _uiState.asStateFlow()

    fun onContentChanged(content: String) {
        val error = when {
            content.isBlank() -> null
            content.length < MIN_CONTENT_LENGTH -> "Story must be at least $MIN_CONTENT_LENGTH characters"
            content.length > MAX_CONTENT_LENGTH -> "Story exceeds $MAX_CONTENT_LENGTH characters"
            else -> null
        }
        _uiState.value = _uiState.value.copy(
            content = content,
            contentError = error
        )
    }

    fun onCurrentChangeTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(currentChangeText = text)
    }

    fun addLifeChange() {
        val text = _uiState.value.currentChangeText.trim()
        if (text.isNotBlank() && text.length <= MAX_LIFE_CHANGE_LENGTH && _uiState.value.lifeChanges.size < 5) {
            _uiState.value = _uiState.value.copy(
                lifeChanges = _uiState.value.lifeChanges + text,
                currentChangeText = ""
            )
        }
    }

    fun removeLifeChange(index: Int) {
        val current = _uiState.value.lifeChanges.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(lifeChanges = current)
        }
    }

    fun postStory() {
        val content = _uiState.value.content.trim()
        if (content.length < MIN_CONTENT_LENGTH) {
            _uiState.value = _uiState.value.copy(
                contentError = "Story must be at least $MIN_CONTENT_LENGTH characters"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true, error = null)

            storyRepository.createStory(
                content = content,
                lifeChanges = _uiState.value.lifeChanges
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isPosting = false,
                        postSuccess = true
                    )
                    Timber.d("Story posted successfully: %s", it.id)
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to post story")
                        _uiState.value = _uiState.value.copy(
                            isPosting = false,
                            error = e.localizedMessage ?: "Failed to post story"
                        )
                    }
                }
            )
        }
    }
}

class PostStoryViewModelFactory(
    private val storyRepository: StoryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PostStoryViewModel::class.java)) {
            return PostStoryViewModel(storyRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

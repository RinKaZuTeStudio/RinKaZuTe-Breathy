@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.breathy.ui.events


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.breathy.BreathyApplication
import com.breathy.data.models.CheckinStatus
import com.breathy.data.models.EventCheckin
import com.breathy.data.repository.EventRepository
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class AdminReviewUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val pendingCheckins: List<EventCheckin> = emptyList(),
    val selectedCheckin: EventCheckin? = null,
    val isReviewing: Boolean = false,
    val rejectionComment: String = "",
    val showCommentField: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isBatchMode: Boolean = false,
    val isBatchProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class AdminReviewViewModel(
    private val eventRepository: EventRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "AdminReviewViewModel"
        private const val EVENT_CHECKINS_COLLECTION = "eventCheckins"
    }

    private val _uiState = MutableStateFlow(AdminReviewUiState())
    val uiState: StateFlow<AdminReviewUiState> = _uiState.asStateFlow()

    init {
        loadPendingCheckins()
    }

    fun loadPendingCheckins() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val snapshot = withTimeoutOrNull(15_000L) {
                    firestore.collection(EVENT_CHECKINS_COLLECTION)
                        .whereEqualTo("status", CheckinStatus.PENDING.value)
                        .orderBy("submittedAt", Query.Direction.DESCENDING)
                        .limit(50)
                        .get()
                        .await()
                }

                val checkins = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { EventCheckin.fromFirestoreMap(doc.id, it) }
                } ?: emptyList()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingCheckins = checkins,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                Timber.e(e, "Failed to load pending check-ins")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load pending check-ins: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectCheckin(checkin: EventCheckin?) {
        _uiState.update {
            it.copy(
                selectedCheckin = checkin,
                showCommentField = false,
                rejectionComment = ""
            )
        }
    }

    fun approveCheckin(checkinId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReviewing = true) }

            eventRepository.updateCheckinStatus(checkinId, approved = true).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isReviewing = false,
                            pendingCheckins = state.pendingCheckins.filterNot { it.id == checkinId },
                            selectedCheckin = null,
                            successMessage = "Check-in approved"
                        )
                    }
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to approve check-in: %s", checkinId)
                        _uiState.update {
                            it.copy(
                                isReviewing = false,
                                errorMessage = "Failed to approve: ${e.message}"
                            )
                        }
                    }
                }
            )
        }
    }

    fun rejectCheckin(checkinId: String, comment: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReviewing = true) }

            eventRepository.updateCheckinStatus(
                checkinId,
                approved = false,
                comment = comment.ifBlank { null }
            ).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isReviewing = false,
                            pendingCheckins = state.pendingCheckins.filterNot { it.id == checkinId },
                            selectedCheckin = null,
                            showCommentField = false,
                            rejectionComment = "",
                            successMessage = "Check-in rejected"
                        )
                    }
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to reject check-in: %s", checkinId)
                        _uiState.update {
                            it.copy(
                                isReviewing = false,
                                errorMessage = "Failed to reject: ${e.message}"
                            )
                        }
                    }
                }
            )
        }
    }

    fun updateRejectionComment(comment: String) {
        _uiState.update { it.copy(rejectionComment = comment) }
    }

    fun toggleCommentField() {
        _uiState.update { it.copy(showCommentField = !it.showCommentField) }
    }

    fun toggleBatchMode() {
        _uiState.update {
            it.copy(
                isBatchMode = !it.isBatchMode,
                selectedIds = emptySet()
            )
        }
    }

    fun toggleCheckinSelection(checkinId: String) {
        _uiState.update { state ->
            val updated = if (state.selectedIds.contains(checkinId)) {
                state.selectedIds - checkinId
            } else {
                state.selectedIds + checkinId
            }
            state.copy(selectedIds = updated)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.pendingCheckins.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun batchApprove() {
        val selectedIds = _uiState.value.selectedIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBatchProcessing = true) }

            var successCount = 0
            var failCount = 0

            for (id in selectedIds) {
                val result = eventRepository.updateCheckinStatus(id, approved = true)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            _uiState.update { state ->
                state.copy(
                    isBatchProcessing = false,
                    isBatchMode = false,
                    selectedIds = emptySet(),
                    successMessage = if (failCount == 0) {
                        "$successCount check-ins approved"
                    } else {
                        "$successCount approved, $failCount failed"
                    },
                    pendingCheckins = state.pendingCheckins.filterNot { it.id in selectedIds }
                )
            }
        }
    }

    fun batchReject(comment: String) {
        val selectedIds = _uiState.value.selectedIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBatchProcessing = true) }

            var successCount = 0
            var failCount = 0

            for (id in selectedIds) {
                val result = eventRepository.updateCheckinStatus(
                    id,
                    approved = false,
                    comment = comment.ifBlank { null }
                )
                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            _uiState.update { state ->
                state.copy(
                    isBatchProcessing = false,
                    isBatchMode = false,
                    selectedIds = emptySet(),
                    showCommentField = false,
                    rejectionComment = "",
                    successMessage = if (failCount == 0) {
                        "$successCount check-ins rejected"
                    } else {
                        "$successCount rejected, $failCount failed"
                    },
                    pendingCheckins = state.pendingCheckins.filterNot { it.id in selectedIds }
                )
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadPendingCheckins()
            delay(500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

}

class AdminReviewViewModelFactory(
    private val eventRepository: EventRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminReviewViewModel::class.java)) {
            return AdminReviewViewModel(eventRepository, firestore, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  AdminReviewScreen — Admin-only check-in video review
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AdminReviewScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AdminReviewViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        ViewModelProvider(
            androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
            AdminReviewViewModelFactory(
                eventRepository = app.appModule.eventRepository,
                firestore = app.appModule.firestore,
                auth = app.appModule.firebaseAuth
            )
        )[AdminReviewViewModel::class.java]
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isRefreshing by remember { mutableStateOf(false) }

    // Handle success/error messages
    LaunchedEffect(Unit) {
        viewModel.uiState.collect { state ->
            state.successMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                viewModel.clearSuccessMessage()
            }
            state.errorMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                viewModel.clearErrorMessage()
            }
        }
    }

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        contentVisible = true
    }

    DisposableEffect(Unit) {
        Timber.d("AdminReviewScreen: composed")
        onDispose { Timber.d("AdminReviewScreen: disposed") }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
            scope.launch {
                delay(1000)
                isRefreshing = false
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Review Check-ins",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
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
                    // Batch mode toggle
                    Text(
                        text = if (uiState.isBatchMode) "Cancel" else "Batch",
                        color = if (uiState.isBatchMode) AccentPrimary else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .semantics {
                                contentDescription = if (uiState.isBatchMode) "Cancel batch mode" else "Enter batch mode"
                                role = Role.Button
                            },
                        fontSize = 14.sp
                    )
                    // Using a simple clickable text area instead of TextButton
                    Card(
                        onClick = { viewModel.toggleBatchMode() },
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isBatchMode) AccentPrimary.copy(alpha = 0.15f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = if (uiState.isBatchMode) "Cancel" else "Batch",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (uiState.isBatchMode) AccentPrimary else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgPrimary
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                uiState.isLoading -> {
                    AdminLoadingState()
                }
                uiState.errorMessage != null && uiState.pendingCheckins.isEmpty() -> {
                    AdminErrorState(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.loadPendingCheckins() }
                    )
                }
                uiState.pendingCheckins.isEmpty() && !uiState.isLoading -> {
                    AdminEmptyState()
                }
                else -> {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(400)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // ── Batch Action Bar ────────────────────────────
                            if (uiState.isBatchMode) {
                                BatchActionBar(
                                    selectedCount = uiState.selectedIds.size,
                                    totalCount = uiState.pendingCheckins.size,
                                    isProcessing = uiState.isBatchProcessing,
                                    onSelectAll = { viewModel.selectAll() },
                                    onClearSelection = { viewModel.clearSelection() },
                                    onBatchApprove = { viewModel.batchApprove() },
                                    onBatchReject = {
                                        viewModel.toggleCommentField()
                                    }
                                )
                            }

                            // ── Pending Check-ins List ─────────────────────
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(
                                    items = uiState.pendingCheckins,
                                    key = { it.id }
                                ) { checkin ->
                                    CheckinReviewCard(
                                        checkin = checkin,
                                        isSelected = uiState.selectedIds.contains(checkin.id),
                                        isBatchMode = uiState.isBatchMode,
                                        isReviewing = uiState.isReviewing,
                                        onToggleSelection = { viewModel.toggleCheckinSelection(checkin.id) },
                                        onClick = {
                                            if (!uiState.isBatchMode) {
                                                viewModel.selectCheckin(checkin)
                                            } else {
                                                viewModel.toggleCheckinSelection(checkin.id)
                                            }
                                        },
                                        onApprove = { viewModel.approveCheckin(checkin.id) },
                                        onReject = {
                                            viewModel.selectCheckin(checkin)
                                            viewModel.toggleCommentField()
                                        }
                                    )
                                }
                            }

                            // ── Rejection Comment Field ────────────────────
                            if (uiState.showCommentField) {
                                RejectionCommentSection(
                                    comment = uiState.rejectionComment,
                                    onCommentChange = { viewModel.updateRejectionComment(it) },
                                    onSubmit = {
                                        val selected = uiState.selectedCheckin
                                        if (selected != null) {
                                            viewModel.rejectCheckin(selected.id, uiState.rejectionComment)
                                        }
                                    },
                                    onDismiss = {
                                        viewModel.toggleCommentField()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = AccentPrimary,
                backgroundColor = BgSurface
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Check-in Review Card
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CheckinReviewCard(
    checkin: EventCheckin,
    isSelected: Boolean,
    isBatchMode: Boolean,
    isReviewing: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Pending check-in from user ${checkin.userId}, day ${checkin.dayNumber}"
                role = Role.Button
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AccentPrimary.copy(alpha = 0.08f) else BgSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.4f))
        } else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox (batch mode) or pending icon
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentPrimary,
                        uncheckedColor = TextDisabled
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = if (isSelected) "Deselect" else "Select"
                        role = Role.Checkbox
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Pending,
                    contentDescription = null,
                    tint = SemanticError.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Check-in info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = AccentSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Day ${checkin.dayNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "User: ${checkin.userId.take(8)}...",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Submitted: ${dateFormatter.format(checkin.submittedAt.toDate())}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextDisabled,
                        fontSize = 11.sp
                    )
                )
            }

            // Action buttons (not in batch mode)
            if (!isBatchMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Approve button
                    Card(
                        onClick = { onApprove() },
                        enabled = !isReviewing,
                        colors = CardDefaults.cardColors(
                            containerColor = AccentPrimary.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "Approve check-in"
                            role = Role.Button
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isReviewing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = AccentPrimary,
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Approve",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Reject button
                    Card(
                        onClick = { onReject() },
                        enabled = !isReviewing,
                        colors = CardDefaults.cardColors(
                            containerColor = SemanticError.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "Reject check-in"
                            role = Role.Button
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = SemanticError,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Reject",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SemanticError,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Batch Action Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    isProcessing: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchApprove: () -> Unit,
    onBatchReject: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BgSurface,
        border = BorderStroke(0.5.dp, BgSurfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Selection info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$selectedCount of $totalCount selected",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        onClick = onSelectAll,
                        colors = CardDefaults.cardColors(containerColor = BgSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = "All",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Card(
                        onClick = onClearSelection,
                        colors = CardDefaults.cardColors(containerColor = BgSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = "None",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Batch action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBatchApprove,
                    enabled = selectedCount > 0 && !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .semantics {
                            contentDescription = "Approve $selectedCount selected check-ins"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        contentColor = BgPrimary,
                        disabledContainerColor = AccentPrimary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BgPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Approve ($selectedCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = onBatchReject,
                    enabled = selectedCount > 0 && !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .semantics {
                            contentDescription = "Reject $selectedCount selected check-ins"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SemanticError,
                        contentColor = TextPrimary,
                        disabledContainerColor = SemanticError.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reject ($selectedCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Rejection Comment Section
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RejectionCommentSection(
    comment: String,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BgSurface,
        border = BorderStroke(0.5.dp, SemanticError.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Comment,
                    contentDescription = null,
                    tint = SemanticError,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Rejection Reason (Optional)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Enter rejection comment"
                    },
                placeholder = {
                    Text(
                        text = "Explain why this check-in was rejected...",
                        color = TextDisabled
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SemanticError.copy(alpha = 0.5f),
                    unfocusedBorderColor = BgSurfaceVariant,
                    focusedContainerColor = BgPrimary,
                    unfocusedContainerColor = BgPrimary,
                    cursorColor = SemanticError,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 3,
                minLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgSurfaceVariant,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .semantics {
                            contentDescription = "Confirm rejection"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SemanticError,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Confirm Reject",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Loading / Empty / Error States
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AdminLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = AccentPrimary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading pending check-ins...",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
        }
    }
}

@Composable
private fun AdminEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "\u2705",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "All Caught Up!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No pending check-ins to review",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AdminErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\u26A0\uFE0F",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                onClick = onRetry,
                colors = CardDefaults.cardColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "Try Again",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    color = BgPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

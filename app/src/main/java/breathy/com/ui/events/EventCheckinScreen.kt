package breathy.com.ui.events

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import breathy.com.BreathyApplication
import breathy.com.data.models.EventCheckin
import breathy.com.data.repository.EventRepository
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentSecondary
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.SemanticError
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.ui.text.font.FontFamily

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class EventCheckinUiState(
    val isLoading: Boolean = true,
    val eventId: String = "",
    val eventTitle: String = "",
    val dayNumber: Int = 1,
    val hasCameraPermission: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val videoUri: Uri? = null,
    val isPreviewMode: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val isUploadComplete: Boolean = false,
    val showSuccessAnimation: Boolean = false,
    val errorMessage: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class EventCheckinViewModel(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    private val eventId: String
) : ViewModel() {

    companion object {
        private const val TAG = "EventCheckinViewModel"
        private const val MAX_RECORDING_SECONDS = 60
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(EventCheckinUiState(eventId = eventId))
    val uiState: StateFlow<EventCheckinUiState> = _uiState.asStateFlow()

    init {
        loadEventInfo()
    }

    private fun loadEventInfo() {
        viewModelScope.launch {
            try {
                val event = eventRepository.getEvent(eventId).getOrNull()
                if (event != null) {
                    val startMillis = event.startDate.toDate().time
                    val nowMillis = System.currentTimeMillis()
                    val dayNumber = ((nowMillis - startMillis) / (24 * 60 * 60 * 1000)).toInt()
                        .coerceAtLeast(1).coerceAtMost(event.totalDays())

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            eventTitle = event.title,
                            dayNumber = dayNumber
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Event not found") }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load event info")
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load event info") }
            }
        }
    }

    fun setCameraPermission(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun startRecording() {
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingDurationSeconds = 0,
                isPreviewMode = false,
                videoUri = null
            )
        }
    }

    fun updateRecordingDuration() {
        _uiState.update {
            it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1)
        }
    }

    fun stopRecording(videoUri: Uri?) {
        _uiState.update {
            it.copy(
                isRecording = false,
                videoUri = videoUri,
                isPreviewMode = videoUri != null
            )
        }
    }

    fun discardVideo() {
        _uiState.update {
            it.copy(
                videoUri = null,
                isPreviewMode = false,
                recordingDurationSeconds = 0
            )
        }
    }

    fun submitCheckin(videoUri: Uri) {
        val uid = currentUserId ?: return
        val dayNumber = _uiState.value.dayNumber

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadProgress = 0f, errorMessage = null) }

            // Simulate upload progress updates
            val progressJob = launch {
                var progress = 0f
                while (progress < 0.9f) {
                    delay(200)
                    progress += 0.05f
                    _uiState.update { it.copy(uploadProgress = progress.coerceAtMost(0.9f)) }
                }
            }

            eventRepository.submitCheckin(eventId, dayNumber, videoUri).fold(
                onSuccess = { checkin ->
                    progressJob.cancel()
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            isUploadComplete = true,
                            showSuccessAnimation = true
                        )
                    }
                },
                onFailure = { e ->
                    progressJob.cancel()
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to submit check-in")
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadProgress = 0f,
                                errorMessage = e.localizedMessage ?: "Failed to submit check-in"
                            )
                        }
                    }
                }
            )
        }
    }

    fun dismissSuccessAnimation() {
        _uiState.update { it.copy(showSuccessAnimation = false) }
    }
}

class EventCheckinViewModelFactory(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    private val eventId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventCheckinViewModel::class.java)) {
            return EventCheckinViewModel(eventRepository, auth, eventId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  EventCheckinScreen — Video check-in using CameraX
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCheckinScreen(
    eventId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: EventCheckinViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        ViewModelProvider(
            androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
            EventCheckinViewModelFactory(
                eventRepository = app.appModule.eventRepository,
                auth = app.appModule.firebaseAuth,
                eventId = eventId
            )
        )[EventCheckinViewModel::class.java]
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera state
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setCameraPermission(granted)
    }

    // Check and request camera permission
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.setCameraPermission(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Recording duration timer
    LaunchedEffect(uiState.isRecording) {
        while (uiState.isRecording) {
            delay(1000)
            viewModel.updateRecordingDuration()
        }
    }

    // Initialize camera
    LaunchedEffect(uiState.hasCameraPermission) {
        if (uiState.hasCameraPermission) {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider = provider

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                // Bind camera to lifecycle
                val preview = Preview.Builder().build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize camera")
            }
        }
    }

    // Update preview surface
    DisposableEffect(cameraProvider) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    DisposableEffect(Unit) {
        Timber.d("EventCheckinScreen: composed")
        onDispose {
            Timber.d("EventCheckinScreen: disposed")
            currentRecording?.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Check In - Day ${uiState.dayNumber}",
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !uiState.isUploading
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = AccentPrimary,
                            strokeWidth = 3.dp
                        )
                    }
                }
                uiState.isUploadComplete -> {
                    // Upload complete
                    UploadCompleteScreen(
                        dayNumber = uiState.dayNumber,
                        eventTitle = uiState.eventTitle,
                        onDone = onNavigateBack
                    )
                }
                uiState.isUploading -> {
                    // Upload in progress
                    UploadProgressScreen(
                        progress = uiState.uploadProgress,
                        dayNumber = uiState.dayNumber
                    )
                }
                uiState.isPreviewMode && uiState.videoUri != null -> {
                    // Video preview mode
                    VideoPreviewScreen(
                        videoUri = uiState.videoUri!!,
                        dayNumber = uiState.dayNumber,
                        onDiscard = { viewModel.discardVideo() },
                        onSubmit = { viewModel.submitCheckin(uiState.videoUri!!) }
                    )
                }
                uiState.hasCameraPermission -> {
                    // Camera view
                    CameraViewScreen(
                        isRecording = uiState.isRecording,
                        recordingDurationSeconds = uiState.recordingDurationSeconds,
                        dayNumber = uiState.dayNumber,
                        eventTitle = uiState.eventTitle,
                        onInitializePreview = { pv ->
                            previewView = pv
                            cameraProvider?.let { provider ->
                                val preview = Preview.Builder().build()
                                preview.setSurfaceProvider(pv.surfaceProvider)
                                try {
                                    provider.unbindAll()
                                    val capture = videoCapture
                                    if (capture != null) {
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_FRONT_CAMERA,
                                            preview,
                                            capture
                                        )
                                    } else {
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_FRONT_CAMERA,
                                            preview
                                        )
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to bind camera preview")
                                }
                            }
                        },
                        onStartRecording = {
                            val capture = videoCapture ?: return@CameraViewScreen
                            try {
                                val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                                    context.contentResolver,
                                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                )
                                    .setContentValues(
                                        android.content.ContentValues().apply {
                                            put(
                                                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                                                "breathy_checkin_${System.currentTimeMillis()}.mp4"
                                            )
                                            put(
                                                android.provider.MediaStore.Video.Media.MIME_TYPE,
                                                "video/mp4"
                                            )
                                        }
                                    )
                                    .build()

                                currentRecording = capture.output
                                    .prepareRecording(context, mediaStoreOutput)
                                    .start(
                                        ContextCompat.getMainExecutor(context)
                                    ) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Finalize -> {
                                                if (event.hasError()) {
                                                    Timber.e("Recording error: ${event.error}")
                                                    viewModel.stopRecording(null)
                                                } else {
                                                    viewModel.stopRecording(event.outputResults.outputUri)
                                                }
                                            }
                                        }
                                    }

                                viewModel.startRecording()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to start recording")
                            }
                        },
                        onStopRecording = {
                            currentRecording?.stop()
                            currentRecording = null
                        }
                    )
                }
                else -> {
                    // No camera permission
                    NoPermissionScreen(
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }
            }

            // ── Success Animation Overlay ────────────────────────────────
            if (uiState.showSuccessAnimation) {
                SuccessAnimationOverlay(
                    onDismiss = {
                        viewModel.dismissSuccessAnimation()
                        onNavigateBack()
                    }
                )
            }

            // ── Error Message ────────────────────────────────────────────
            uiState.errorMessage?.let { message ->
                if (!uiState.isUploading) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        color = SemanticError.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SemanticError.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SemanticError
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Camera View Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CameraViewScreen(
    isRecording: Boolean,
    recordingDurationSeconds: Int,
    dayNumber: Int,
    eventTitle: String,
    onInitializePreview: (PreviewView) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        onInitializePreview(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Recording indicator
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formatDuration(recordingDurationSeconds),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            // Day number badge
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Day $dayNumber",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = themeBgPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Controls
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = themeBgSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isRecording) "Recording your check-in..." else "Tap to start recording",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = themeTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Record/Stop button
                if (isRecording) {
                    IconButton(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .size(72.dp)
                            .semantics {
                                contentDescription = "Stop recording"
                                role = Role.Button
                            }
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = Color.Red.copy(alpha = 0.2f),
                            border = BorderStroke(3.dp, Color.Red)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Red
                                ) {}
                            }
                        }
                    }
                } else {
                    IconButton(
                        onClick = onStartRecording,
                        modifier = Modifier
                            .size(72.dp)
                            .semantics {
                                contentDescription = "Start recording"
                                role = Role.Button
                            }
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = AccentPrimary.copy(alpha = 0.2f),
                            border = BorderStroke(3.dp, AccentPrimary)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = AccentPrimary
                                ) {}
                            }
                        }
                    }
                }

                if (isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Max 60 seconds",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextDisabled,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Video Preview Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoPreviewScreen(
    videoUri: Uri,
    dayNumber: Int,
    onDiscard: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Video preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder for video preview — in production this would use
            // ExoPlayer or Android's VideoView
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = themeBgSurfaceVariant),
                shape = RoundedCornerShape(0.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Video Preview",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = themeTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "Day $dayNumber check-in recorded",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = themeTextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Action buttons
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = themeBgSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Discard button
                Button(
                    onClick = onDiscard,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics {
                            contentDescription = "Discard video and record again"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeBgSurfaceVariant,
                        contentColor = themeTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Discard",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Submit button
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics {
                            contentDescription = "Submit check-in video"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        contentColor = themeBgPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Submit",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Upload Progress Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UploadProgressScreen(
    progress: Float,
    dayNumber: Int
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Circular progress
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(120.dp),
                    color = AccentPrimary,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = themeBgSurfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Uploading Day $dayNumber Check-in",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = themeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Please wait while your video is being uploaded...",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp),
                color = AccentPrimary,
                trackColor = themeBgSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Upload Complete Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UploadCompleteScreen(
    dayNumber: Int,
    eventTitle: String,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Success icon with glow
            val infiniteTransition = rememberInfiniteTransition(label = "success_glow")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "success_glow_alpha"
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(100.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AccentPrimary.copy(alpha = glowAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2
                        ),
                        radius = size.minDimension / 2,
                        center = center
                    )
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Upload complete",
                    tint = AccentPrimary,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Check-in Submitted!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Day $dayNumber of $eventTitle",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = themeTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your video will be reviewed shortly",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp)
                    .semantics {
                        contentDescription = "Return to event"
                        role = Role.Button
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = themeBgPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  No Permission Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NoPermissionScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "\uD83D\uDCF9",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We need camera access to record your check-in video",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = themeBgPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.semantics {
                    contentDescription = "Grant camera permission"
                    role = Role.Button
                }
            ) {
                Text(
                    text = "Grant Permission",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Success Animation Overlay
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SuccessAnimationOverlay(
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2500)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                initialScale = 0.5f,
                animationSpec = tween(400)
            ) + fadeIn(animationSpec = tween(300))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = AccentPrimary.copy(alpha = 0.2f),
                    border = BorderStroke(2.dp, AccentPrimary.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "\u2728 Submitted! \u2728",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Helpers
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

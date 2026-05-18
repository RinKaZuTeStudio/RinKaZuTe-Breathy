package com.breathy.ui.events

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.themeBgPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.atan2

// ═══════════════════════════════════════════════════════════════════════════════
//  Pushup State Tracker
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tracks pushup count using pose landmark angles.
 * A pushup is counted when:
 * 1. Arms go from straight (angle > 150°) to bent (angle < 90°) — "down" phase
 * 2. Arms go from bent back to straight — "up" phase completing one rep
 */
class PushupTracker {
    enum class PushupPhase { IDLE, GOING_DOWN, DOWN, GOING_UP }

    private var phase = PushupPhase.IDLE
    private var _count = 0
    val count: Int get() = _count

    // Minimum angle to consider arms straight (up position)
    private val STRAIGHT_THRESHOLD = 150.0
    // Maximum angle to consider arms bent (down position)
    private val BENT_THRESHOLD = 90.0
    // Minimum time between pushups (ms) to prevent jitter
    private val MIN_PUSHUP_INTERVAL_MS = 500L
    private var lastPushupTime = 0L

    /** Calculate angle at the elbow (shoulder-elbow-wrist) in degrees. */
    private fun calculateAngle(
        shoulder: PoseLandmark,
        elbow: PoseLandmark,
        wrist: PoseLandmark
    ): Double {
        val a = elbow.position3D // center
        val b = shoulder.position3D
        val c = wrist.position3D

        val ba = Float3(b.x - a.x, b.y - a.y, b.z - a.z)
        val ca = Float3(c.x - a.x, c.y - a.y, c.z - a.z)

        val dot = ba.x * ca.x + ba.y * ca.y + ba.z * ca.z
        val magBA = kotlin.math.sqrt((ba.x * ba.x + ba.y * ba.y + ba.z * ba.z).toDouble())
        val magCA = kotlin.math.sqrt((ca.x * ca.x + ca.y * ca.y + ca.z * ca.z).toDouble())

        if (magBA < 0.001 || magCA < 0.001) return 180.0

        val cosAngle = (dot / (magBA * magCA)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(atan2(kotlin.math.sqrt(1 - cosAngle * cosAngle), cosAngle))
    }

    /** Update the tracker with new pose landmarks. Returns true if a pushup was counted. */
    fun update(landmarks: List<PoseLandmark>): Boolean {
        // Get left arm landmarks
        val leftShoulder = landmarks.getOrNull(PoseLandmark.LEFT_SHOULDER) ?: return false
        val leftElbow = landmarks.getOrNull(PoseLandmark.LEFT_ELBOW) ?: return false
        val leftWrist = landmarks.getOrNull(PoseLandmark.LEFT_WRIST) ?: return false

        // Get right arm landmarks
        val rightShoulder = landmarks.getOrNull(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val rightElbow = landmarks.getOrNull(PoseLandmark.RIGHT_ELBOW) ?: return false
        val rightWrist = landmarks.getOrNull(PoseLandmark.RIGHT_WRIST) ?: return false

        // Only process if all landmarks have reasonable confidence
        if (leftShoulder.likelihood < 0.5f || leftElbow.likelihood < 0.5f ||
            leftWrist.likelihood < 0.5f || rightShoulder.likelihood < 0.5f ||
            rightElbow.likelihood < 0.5f || rightWrist.likelihood < 0.5f
        ) return false

        // Average angle of both arms
        val leftAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val avgAngle = (leftAngle + rightAngle) / 2.0

        val now = System.currentTimeMillis()
        var pushupCounted = false

        when (phase) {
            PushupPhase.IDLE, PushupPhase.GOING_UP -> {
                if (avgAngle < BENT_THRESHOLD) {
                    phase = PushupPhase.DOWN
                } else if (avgAngle < STRAIGHT_THRESHOLD) {
                    phase = PushupPhase.GOING_DOWN
                }
            }
            PushupPhase.GOING_DOWN -> {
                if (avgAngle < BENT_THRESHOLD) {
                    phase = PushupPhase.DOWN
                } else if (avgAngle >= STRAIGHT_THRESHOLD) {
                    phase = PushupPhase.IDLE
                }
            }
            PushupPhase.DOWN -> {
                if (avgAngle > BENT_THRESHOLD + 20) {
                    phase = PushupPhase.GOING_UP
                }
            }
        }

        // Count pushup when transitioning from going_up to straight
        if (phase == PushupPhase.GOING_UP && avgAngle >= STRAIGHT_THRESHOLD) {
            if (now - lastPushupTime > MIN_PUSHUP_INTERVAL_MS) {
                _count++
                lastPushupTime = now
                pushupCounted = true
            }
            phase = PushupPhase.IDLE
        }

        return pushupCounted
    }

    fun reset() {
        phase = PushupPhase.IDLE
        _count = 0
        lastPushupTime = 0L
    }

    private data class Float3(val x: Float, val y: Float, val z: Float)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  PushupCounterScreen
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushupCounterScreen(
    eventId: String,
    onNavigateBack: () -> Unit = {},
    onSubmitComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // State
    var hasCameraPermission by remember { mutableStateOf(false) }
    var isCounting by remember { mutableStateOf(false) }
    var pushupCount by remember { mutableIntStateOf(0) }
    var sessionStartTime by remember { mutableLongStateOf(0L) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isComplete by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    // Camera & ML Kit
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val pushupTracker = remember { PushupTracker() }
    val poseDetector = remember {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    // Camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            hasCameraPermission = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Initialize camera
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider = provider
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize camera provider")
                showError = "Failed to initialize camera"
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            poseDetector.close()
        }
    }

    // Format duration
    val sessionDurationSeconds = if (isCounting || isComplete) {
        ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
    } else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pushup Counter",
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                isComplete -> {
                    // Completion screen
                    PushupCompleteScreen(
                        pushupCount = pushupCount,
                        durationSeconds = sessionDurationSeconds,
                        onDone = onSubmitComplete
                    )
                }
                isSubmitting -> {
                    // Submitting screen
                    SubmittingScreen(pushupCount = pushupCount)
                }
                hasCameraPermission && cameraProvider != null -> {
                    // Camera + counting screen
                    CameraCountingScreen(
                        pushupCount = pushupCount,
                        isCounting = isCounting,
                        cameraProvider = cameraProvider!!,
                        lifecycleOwner = lifecycleOwner,
                        poseDetector = poseDetector,
                        pushupTracker = pushupTracker,
                        onCountChanged = { newCount ->
                            pushupCount = newCount
                        },
                        onStartCounting = {
                            isCounting = true
                            sessionStartTime = System.currentTimeMillis()
                            pushupTracker.reset()
                            pushupCount = 0
                        },
                        onStopCounting = {
                            isCounting = false
                        },
                        onSubmit = {
                            if (pushupCount > 0) {
                                isSubmitting = true
                                val durationSecs = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                                val app = context.applicationContext as com.breathy.BreathyApplication
                                coroutineScope.launch {
                                    app.appModule.eventRepository.submitPushupCount(
                                        eventId = eventId,
                                        pushupCount = pushupCount,
                                        sessionDurationSeconds = durationSecs
                                    ).fold(
                                        onSuccess = {
                                            isSubmitting = false
                                            isComplete = true
                                        },
                                        onFailure = { e ->
                                            isSubmitting = false
                                            showError = e.message ?: "Failed to submit"
                                        }
                                    )
                                }
                            } else {
                                showError = "Complete at least 1 pushup to submit"
                            }
                        }
                    )
                }
                else -> {
                    // No permission
                    NoCameraPermissionScreen {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            // Error snackbar
            showError?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFE53935)
                        ),
                        textAlign = TextAlign.Center
                    )
                }
                LaunchedEffect(msg) {
                    delay(3000)
                    showError = null
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Camera + Counting Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CameraCountingScreen(
    pushupCount: Int,
    isCounting: Boolean,
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    poseDetector: PoseDetector,
    pushupTracker: PushupTracker,
    onCountChanged: (Int) -> Unit,
    onStartCounting: () -> Unit,
    onStopCounting: () -> Unit,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera preview with overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // Image analysis for pose detection
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                        try {
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )

                                poseDetector.process(inputImage)
                                    .addOnSuccessListener { pose ->
                                        val landmarks = pose.allPoseLandmarks
                                        if (landmarks.isNotEmpty() && isCounting) {
                                            val counted = pushupTracker.update(landmarks)
                                            if (counted) {
                                                onCountChanged(pushupTracker.count)
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Timber.w(e, "Pose detection failed")
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Image analysis error")
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to bind camera")
                    }

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Pushup count overlay
            PushupCountOverlay(
                count = pushupCount,
                isCounting = isCounting,
                modifier = Modifier.align(Alignment.TopCenter)
            )
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
                if (isCounting) {
                    // Active counting state
                    Text(
                        text = "Keep going! AI is counting your pushups",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AccentPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Stop button
                        Button(
                            onClick = onStopCounting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Stop", fontWeight = FontWeight.Bold)
                        }

                        // Submit button
                        Button(
                            onClick = onSubmit,
                            enabled = pushupCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = AccentPrimary.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Submit", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Idle state
                    Text(
                        text = if (pushupCount > 0) "$pushupCount pushups recorded" else "Position yourself in frame",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = themeTextSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onStartCounting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pushupCount > 0) "Start Again" else "Start Pushups",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Pushup Count Overlay
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PushupCountOverlay(
    count: Int,
    isCounting: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pushup_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCounting) AccentPrimary.copy(alpha = 0.9f)
            else Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Count circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp)
            ) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = if (isCounting) glowAlpha else 0.3f),
                        radius = 30.dp.toPx(),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = count.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        shadow = Shadow(
                            color = AccentPrimary.copy(alpha = 0.5f),
                            offset = Offset(0f, 0f),
                            blurRadius = 8f
                        )
                    )
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (isCounting) "Counting..." else "Pushups",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (isCounting) {
                    Text(
                        text = "AI is watching your form",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Completion Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PushupCompleteScreen(
    pushupCount: Int,
    durationSeconds: Int,
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
            // Big count display
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(160.dp),
                    color = AccentPrimary,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = AccentPrimary.copy(alpha = 0.1f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pushupCount.toString(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPrimary
                        )
                    )
                    Text(
                        text = "pushups",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = themeTextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Amazing work! 💪",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = themeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            Text(
                text = "Session time: ${minutes}m ${seconds}s",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text(text = "Done", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Submitting Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SubmittingScreen(pushupCount: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = AccentPrimary,
                strokeWidth = 4.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Submitting $pushupCount pushups...",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = themeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Verifying and saving your results",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  No Camera Permission Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NoCameraPermissionScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "📷", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera access needed",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We need camera access to count your pushups using AI pose detection",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "Grant Permission", fontWeight = FontWeight.Bold)
            }
        }
    }
}

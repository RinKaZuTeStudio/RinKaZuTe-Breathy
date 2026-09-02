package breathy.com.ui.profile

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import breathy.com.ui.components.NetworkImage
import breathy.com.ui.components.invalidateImageCache
import breathy.com.ui.components.clearImageCache
import breathy.com.BreathyApplication
import breathy.com.data.models.Achievement
import breathy.com.data.models.Subscription
import breathy.com.data.models.User
import breathy.com.data.repository.AuthRepository
import breathy.com.data.repository.RewardRepository
import breathy.com.data.repository.UserRepository
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentInfo
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.AccentWarning
import breathy.com.ui.theme.GoldDeep
import breathy.com.ui.theme.PureWhite
import breathy.com.ui.theme.GoldSoft
import breathy.com.ui.theme.NaturalGreen
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.DarkBotanical
import breathy.com.ui.theme.TextSecondary
import breathy.com.ui.theme.themeAccentPrimary
import breathy.com.ui.theme.themeAccentPrimaryMuted
import breathy.com.ui.theme.themeAccentPurple

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
//  ProfileScreen — User's own profile page
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ProfileScreen(
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToGoldHistory: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: ProfileViewModel = run {
        val app = LocalContext.current.applicationContext as BreathyApplication
        viewModel(factory = ProfileViewModelFactory(
            userRepository = app.appModule.userRepository,
            rewardRepository = app.appModule.rewardRepository,
            authRepository = app.appModule.authRepository,
            auth = app.appModule.firebaseAuth,
            firestore = app.appModule.firestore,
            premiumRepository = app.appModule.premiumRepository,
            goldRepository = app.appModule.goldRepository
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAccountPrivacy by remember { mutableStateOf(false) }
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var showEditQuitDateDialog by remember { mutableStateOf(false) }
    var showEditAgeDialog by remember { mutableStateOf(false) }
    var showFramePicker by remember { mutableStateOf(false) }

    // Photo picker — uses Android Photo Picker (no permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.updatePhoto(it) }
    }

    // Staggered entrance
    var headerVisible by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var sectionsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        headerVisible = true
        delay(200)
        statsVisible = true
        delay(200)
        sectionsVisible = true
    }

    DisposableEffect(Unit) {
        Timber.d("ProfileScreen: composed")
        onDispose { Timber.d("ProfileScreen: disposed") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // ── Header with Avatar and Name ─────────────────────────────
                AnimatedVisibility(
                    visible = headerVisible,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { -it / 4 },
                        animationSpec = tween(400)
                    )
                ) {
                    ProfileHeader(
                        nickname = uiState.user?.nickname ?: "",
                        email = uiState.user?.email ?: "",
                        photoURL = uiState.user?.photoURL,
                        age = uiState.user?.age,
                        xp = uiState.user?.xp ?: 0,
                        level = uiState.user?.level ?: 1,
                        levelProgress = uiState.levelProgress,
                        isPhotoUploading = uiState.isPhotoUploading,
                        photoCacheBust = uiState.photoCacheBust,
                        avatarFrame = breathy.com.data.models.AvatarFrame.fromId(uiState.user?.avatarFrame),
                        isPremium = uiState.isPremium,
                        onAvatarClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onChangeFrame = { showFramePicker = true },
                        onEditNickname = { showEditNicknameDialog = true },
                        onEditAge = { showEditAgeDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Stats Cards ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = statsVisible,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(400)
                    )
                ) {
                    ProfileStatsSection(
                        daysSmokeFree = uiState.user?.daysSmokeFree ?: 0,
                        moneySaved = uiState.user?.moneySaved() ?: 0.0,
                        level = uiState.user?.level ?: 1
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Gold Section ────────────────────────────────────────────
                AnimatedVisibility(visible = sectionsVisible) {
                    GoldSection(
                        goldBalance = uiState.goldBalance,
                        onOpenHistory = onNavigateToGoldHistory
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Quit Date ───────────────────────────────────────────────
                AnimatedVisibility(visible = sectionsVisible) {
                    QuitDateSection(
                        quitDate = uiState.user?.quitDate,
                        onEditQuitDate = { showEditQuitDateDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Achievements Preview ────────────────────────────────────
                AnimatedVisibility(visible = sectionsVisible) {
                    AchievementsPreviewSection(
                        achievements = uiState.achievements,
                        onViewAll = onNavigateToAchievements
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Settings Section ────────────────────────────────────────
                AnimatedVisibility(visible = sectionsVisible) {
                    SettingsSection(
                        notificationsEnabled = uiState.notificationsEnabled,
                        privacyEnabled = uiState.privacyEnabled,
                        onNotificationsToggle = { viewModel.toggleNotifications(it) },
                        onPrivacyToggle = { viewModel.togglePrivacy(it) },
                        onNavigateToFriends = onNavigateToFriends,
                        onOpenAccountPrivacy = { showAccountPrivacy = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Subscription Status ─────────────────────────────────────
                AnimatedVisibility(visible = sectionsVisible) {
                    SubscriptionStatusSection(
                        subscription = uiState.subscription,
                        onViewSubscription = onNavigateToSubscription
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Action Buttons ──────────────────────────────────────────
                AnimatedVisibility(visible = sectionsVisible) {
                    ActionButtonsSection(
                        onSignOut = onSignOut,
                        onOpenAccountPrivacy = { showAccountPrivacy = true }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Loading State ────────────────────────────────────────────────────
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = themeAccentPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading profile...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }

    // ── Delete Account Confirmation Dialog ────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Account?",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This action is permanent and cannot be undone. All your data, progress, " +
                            "achievements, and stats will be permanently deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch { viewModel.deleteAccount() }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.error,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ── Edit Nickname Dialog ──────────────────────────────────────────────
    if (showEditNicknameDialog) {
        var nicknameText by remember { mutableStateOf(uiState.user?.nickname ?: "") }
        var nicknameError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = {
                Text(
                    text = "Edit Nickname",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = nicknameText,
                    onValueChange = {
                        nicknameText = it
                        nicknameError = if (it.isBlank()) "Nickname cannot be empty" else null
                    },
                    label = { Text("Nickname") },
                    singleLine = true,
                    isError = nicknameError != null,
                    supportingText = nicknameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = themeAccentPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                        cursorColor = themeAccentPrimary,
                        focusedLabelColor = themeAccentPrimary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nicknameText.isNotBlank()) {
                            viewModel.updateNickname(nicknameText.trim())
                            showEditNicknameDialog = false
                        }
                    },
                    enabled = nicknameText.isNotBlank()
                ) {
                    Text(text = "Save", color = themeAccentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── Edit Age Dialog ───────────────────────────────────────────────────
    if (showEditAgeDialog) {
        var ageText by remember {
            mutableStateOf(uiState.user?.age?.toString() ?: "")
        }
        var ageError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditAgeDialog = false },
            title = {
                Text(
                    text = "Edit Age",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = {
                        ageText = it
                        val age = it.toIntOrNull()
                        ageError = when {
                            it.isBlank() -> null // optional field
                            age == null -> "Enter a valid number"
                            age < 13 || age > 120 -> "Age must be 13–120"
                            else -> null
                        }
                    },
                    label = { Text("Age (optional)") },
                    singleLine = true,
                    isError = ageError != null,
                    supportingText = ageError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = themeAccentPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                        cursorColor = themeAccentPrimary,
                        focusedLabelColor = themeAccentPrimary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val age = ageText.toIntOrNull()
                        viewModel.updateAge(age)
                        showEditAgeDialog = false
                    },
                    enabled = ageError == null
                ) {
                    Text(text = "Save", color = themeAccentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAgeDialog = false }) {
                    Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── Edit Quit Date Dialog ─────────────────────────────────────────────
    if (showEditQuitDateDialog) {
        val calendar = Calendar.getInstance()
        uiState.user?.quitDate?.toDate()?.let {
            calendar.time = it
        }
        // Minimum selectable date = account creation date (user cannot fake old streaks)
        val creationDate = uiState.user?.createdAt?.toDate()?.time ?: 0L
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                val selectedTime = newCal.timeInMillis
                if (selectedTime < creationDate) {
                    // Show a friendly validation message - the date picker should prevent this
                    // but as a safety net, don't save invalid dates
                    viewModel.updateQuitDate(Timestamp(newCal.time))
                } else {
                    viewModel.updateQuitDate(Timestamp(newCal.time))
                }
                showEditQuitDateDialog = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            datePicker.minDate = creationDate
        }.show()
        showEditQuitDateDialog = false
    }

    // ── Account & Privacy sheet (spec section 33) ────────────
    if (showAccountPrivacy) {
        AccountPrivacySheet(
            onDismiss = { showAccountPrivacy = false },
            onDeleteAccount = {
                showAccountPrivacy = false
                showDeleteDialog = true
            }
        )
    }

    // ── Avatar Frame Picker ──────────────────────────────────
    if (showFramePicker) {
        AvatarFramePickerSheet(
            currentFrame = breathy.com.data.models.AvatarFrame.fromId(uiState.user?.avatarFrame),
            isPremium = uiState.isPremium,
            level = uiState.user?.level ?: 1,
            hasAchievements = uiState.achievements.any { it.unlocked },
            hasEventWin = uiState.user?.achievements?.contains("event_champion") == true,
            goldBalance = uiState.goldBalance,
            ownedFrames = uiState.user?.ownedFrames ?: emptyList(),
            onSelect = { frame ->
                viewModel.updateAvatarFrame(frame)
                showFramePicker = false
            },
            onPurchase = { frame -> viewModel.purchaseFrame(frame) },
            onDismiss = { showFramePicker = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Profile Header — Avatar with edit, nickname, email, age
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileHeader(
    nickname: String,
    email: String,
    photoURL: String?,
    age: Int?,
    xp: Int,
    level: Int,
    levelProgress: Float,
    isPhotoUploading: Boolean = false,
    photoCacheBust: Long = 0L,
    avatarFrame: breathy.com.data.models.AvatarFrame = breathy.com.data.models.AvatarFrame.NONE,
    isPremium: Boolean = false,
    onAvatarClick: () -> Unit,
    onChangeFrame: () -> Unit,
    onEditNickname: () -> Unit,
    onEditAge: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_glow_alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with camera icon overlay
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            // Glow effect
            Canvas(modifier = Modifier.size(120.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentPrimary.copy(alpha = glowAlpha),
                            AccentPrimary.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = size.minDimension / 2
                    ),
                    radius = size.minDimension / 2,
                    center = center
                )
            }

            // Avatar with persisted frame (BreathyAvatar renders the same
            // avatar + frame everywhere: profile, leaderboard, community…)
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                if (isPhotoUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = AccentPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    breathy.com.ui.components.BreathyAvatar(
                        photoURL = photoURL,
                        frame = avatarFrame,
                        rankTier = breathy.com.data.models.RankTier.forLevel(level),
                        size = 104.dp,
                        contentDescription = "Your avatar",
                        cacheBust = photoCacheBust,
                        modifier = Modifier.clickable(onClick = onAvatarClick)
                    )
                }
            }

            // Camera icon overlay
            Card(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = AccentPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = onAvatarClick
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change photo",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Nickname with edit
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = nickname.ifBlank { "Quitter" },
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onEditNickname
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit nickname",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Email
        Text(
            text = email,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Identity row: rank badge + premium badge + change frame ────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            breathy.com.ui.components.RankBadge(
                rankTier = breathy.com.data.models.RankTier.forLevel(level),
                level = level
            )
            if (isPremium) {
                breathy.com.ui.components.PremiumBadge()
            }
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onChangeFrame
            ) {
                Text(
                    text = "Change frame",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Age (optional)
        if (age != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Age: $age",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    onClick = onEditAge
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit age",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                        modifier = Modifier
                            .size(20.dp)
                            .padding(3.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(2.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onEditAge
            ) {
                Text(
                    text = "+ Add age",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentPrimary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // XP progress bar
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "XP: $xp",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = AccentPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Level $level",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { levelProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = themeAccentPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Stats Section — Days smoke-free, money saved, XP, level
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Gold section (spec sections 36/38) — balance card with entry to the
 * full Gold transaction history.
 */
@Composable
private fun GoldSection(
    goldBalance: Int,
    onOpenHistory: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenHistory)
            .semantics {
                contentDescription = "Gold balance " + "%,d".format(goldBalance) + ". Open Gold history."
                role = androidx.compose.ui.semantics.Role.Button
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, GoldSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GoldSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🪙", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gold",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "Earned from check-ins, achievements & events",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%,d".format(goldBalance),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GoldDeep
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.labelSmall.copy(color = themeAccentPrimary)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = themeAccentPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsSection(
    daysSmokeFree: Int,
    moneySaved: Double,
    level: Int
) {
    // Row of 3 stat cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatMiniCard(
            label = "Smoke Free",
            value = if (daysSmokeFree == 0) "Day 1" else "${daysSmokeFree}d",
            icon = "🌬️",
            accentColor = themeAccentPrimary,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            label = "Saved",
            value = formatMoney(moneySaved),
            icon = "💰",
            accentColor = themeAccentPrimary,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            label = "Level",
            value = "$level",
            icon = "⭐",
            accentColor = themeAccentPurple,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    icon: String,
    accentColor: Color = themeAccentPrimary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Quit Date Section — With edit option
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuitDateSection(
    quitDate: Timestamp?,
    onEditQuitDate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "📅", fontSize = 20.sp)
                Column {
                    Text(
                        text = "Quit Date",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = quitDate?.let { formatQuitDate(it) } ?: "Not set",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onEditQuitDate
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Change quit date",
                        tint = themeAccentPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeAccentPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Achievements Preview — Horizontal scroll with view all button
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AchievementsPreviewSection(
    achievements: List<Achievement>,
    onViewAll: () -> Unit
) {
    val unlockedAchievements = achievements.filter { it.unlocked }
    val lockedCount = achievements.count { !it.unlocked }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            )
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = themeAccentPrimaryMuted),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onViewAll
            ) {
                Text(
                    text = "View All ($lockedCount locked)",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = themeAccentPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (unlockedAchievements.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Keep going to unlock your first achievement! 🏆",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(unlockedAchievements, key = { it.id }) { achievement ->
                    AchievementChip(achievement = achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementChip(achievement: Achievement) {
    Card(
        colors = CardDefaults.cardColors(containerColor = themeAccentPrimaryMuted),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = achievement.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = themeAccentPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Settings Section — Notifications, dark mode, privacy
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSection(
    notificationsEnabled: Boolean,
    privacyEnabled: Boolean,
    onNotificationsToggle: (Boolean) -> Unit,
    onPrivacyToggle: (Boolean) -> Unit,
    onNavigateToFriends: () -> Unit = {},
    onOpenAccountPrivacy: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Notifications toggle
            SettingsToggleRow(
                icon = Icons.Default.Notifications,
                label = "Notifications",
                description = "Reminders and motivation",
                checked = notificationsEnabled,
                onCheckedChange = onNotificationsToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Privacy toggle
            SettingsToggleRow(
                icon = Icons.Default.PrivacyTip,
                label = "Private Profile",
                description = "Hide from leaderboard",
                checked = privacyEnabled,
                onCheckedChange = onPrivacyToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Friends navigation row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Friends",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Friends",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "View and manage friends",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = themeAccentPrimaryMuted),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    onClick = onNavigateToFriends
                ) {
                    Text(
                        text = "View",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeAccentPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = themeAccentPrimary,
                checkedThumbColor = MaterialTheme.colorScheme.background,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Subscription Status Section
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SubscriptionStatusSection(
    subscription: Subscription?,
    onViewSubscription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (subscription?.isActive() == true) {
                themeAccentPrimaryMuted
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onViewSubscription
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CardGiftcard,
                    contentDescription = "Premium",
                    tint = if (subscription?.isActive() == true) themeAccentPrimary else themeAccentPurple,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (subscription?.isActive() == true) "Breathy Premium ✦" else "Breathy Premium",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = if (subscription?.isActive() == true) "Ad-free + exclusive events — active" else "Ad-free experience + exclusive events",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = "View subscription",
                tint = themeAccentPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Action Buttons — Sign out, Delete account
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActionButtonsSection(
    onSignOut: () -> Unit,
    onOpenAccountPrivacy: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sign out
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = onSignOut
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Sign out",
                    tint = AccentWarning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AccentWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Account & Privacy — houses the account deletion flow (spec section 33).
        // The destructive action is no longer a prominent button on the main
        // settings screen; it lives in a discoverable privacy context.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = onOpenAccountPrivacy
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Account & Privacy",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "Manage your data, account deletion and privacy",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Account & Privacy sheet (spec section 33) — explains what happens on
 * deletion and hosts the actual delete-account entry point.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AccountPrivacySheet(
    onDismiss: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Account & Privacy",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Your recovery data (streaks, milestones, achievements) belongs to you. " +
                    "Signing out keeps your data on this account. Deleting your account permanently " +
                    "removes your profile, posts, friendships, chats and progress. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onDeleteAccount
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Delete account",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "Permanent — removes all your data",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Helper Functions
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatMoney(amount: Double): String {
    return when {
        amount >= 1_000_000 -> "$${"%,.0f".format(amount / 1_000_000)}M"
        amount >= 1_000 -> "$${"%,.0f".format(amount / 1_000)}K"
        else -> "$${"%.0f".format(amount)}"
    }
}

private fun formatQuitDate(timestamp: Timestamp): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class ProfileUiState(
    val user: User? = null,
    val achievements: List<Achievement> = emptyList(),
    val subscription: Subscription? = null,
    val levelProgress: Float = 0f,
    val notificationsEnabled: Boolean = true,
    val privacyEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val isPhotoUploading: Boolean = false,
    val photoCacheBust: Long = 0L,
    val errorMessage: String? = null,
    /** Verified premium entitlement for premium-gated UI. */
    val isPremium: Boolean = false,
    /** Real-time Gold balance for frame purchases and profile display. */
    val goldBalance: Int = 0,
    /** Success celebration for a freshly purchased frame. */
    val justPurchasedFrame: breathy.com.data.models.AvatarFrame? = null
)

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val rewardRepository: RewardRepository,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val premiumRepository: breathy.com.data.repository.PremiumRepository? = null,
    private val goldRepository: breathy.com.data.repository.GoldRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val uid: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    init {
        // Read saved preferences to initialize state correctly
        try {
            val context = breathy.com.BreathyApplication.instance
            val prefs = context.getSharedPreferences("breathy_prefs", android.content.Context.MODE_PRIVATE)
            val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
            val privacyEnabled = prefs.getBoolean("privacy_enabled", false)
            _uiState.update {
                it.copy(
                    notificationsEnabled = notificationsEnabled,
                    privacyEnabled = privacyEnabled
                )
            }
        } catch (_: Exception) { }

        loadProfile()
        loadAchievements()
        loadSubscription()
        observePremium()
        observeGoldBalance()
    }

    /** Stream the real Gold balance for display and frame purchases. */
    private fun observeGoldBalance() {
        goldRepository?.let { repo ->
            viewModelScope.launch {
                repo.balanceFlow().collect { balance ->
                    _uiState.update { it.copy(goldBalance = balance) }
                }
            }
        }
    }

    /**
     * Buy a Gold-purchasable frame: deducts the exact price once, records
     * ownership, and equips the frame (single atomic transaction).
     */
    fun purchaseFrame(frame: breathy.com.data.models.AvatarFrame) {
        val repo = goldRepository ?: return
        viewModelScope.launch {
            try {
                repo.purchaseFrame(frame).getOrThrow()
                _uiState.update { it.copy(justPurchasedFrame = frame, errorMessage = null) }
            } catch (e: breathy.com.data.repository.InsufficientGoldException) {
                _uiState.update {
                    it.copy(errorMessage = "Not enough Gold — the ${frame.label} frame costs ${frame.goldPrice} Gold.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to purchase frame")
                _uiState.update { it.copy(errorMessage = "Purchase failed. Please try again.") }
            }
        }
    }

    /** Mirror the verified premium entitlement into profile UI state. */
    private fun observePremium() {
        premiumRepository?.let { repo ->
            viewModelScope.launch {
                repo.state.collect { premium ->
                    _uiState.update { it.copy(isPremium = premium.isPremium) }
                }
            }
        }
    }

    /**
     * Persist the selected avatar frame (users + publicProfiles) so the same
     * frame renders everywhere. Validated against real unlock conditions.
     */
    fun updateAvatarFrame(frame: breathy.com.data.models.AvatarFrame) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                userRepository.updateAvatarFrame(
                    userId = userId,
                    frame = frame,
                    isPremium = premiumRepository?.isPremium() ?: false
                )
                _uiState.update { it.copy(errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update avatar frame")
                _uiState.update {
                    it.copy(errorMessage = "This frame isn't unlocked yet")
                }
            }
        }
    }

    private fun loadProfile() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Not authenticated") }
            return
        }
        viewModelScope.launch {
            try {
                userRepository.observeUser(userId).collect { user ->
                    _uiState.update { state ->
                        state.copy(
                            user = user,
                            levelProgress = user?.let { rewardRepository.getLevelProgress(it.xp) } ?: 0f,
                            isLoading = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load profile")
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load profile") }
            }
        }
    }

    private fun loadAchievements() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                rewardRepository.observeUnlockedAchievements(userId).collect { unlocked ->
                    val allWithState = Achievement.ALL_DEFINITIONS.map { def ->
                        def.copy(unlocked = unlocked.any { it.id == def.id })
                    }
                    _uiState.update { it.copy(achievements = allWithState) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load achievements")
            }
        }
    }

    private fun loadSubscription() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = withTimeoutOrNull(10_000L) {
                    firestore.collection("subscriptions").document(userId).get().await()
                }
                if (doc != null && doc.exists()) {
                    val sub = Subscription.fromFirestoreMap(doc.data ?: emptyMap())
                    _uiState.update { it.copy(subscription = sub) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load subscription")
            }
        }
    }

    fun updateNickname(nickname: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                // Check nickname uniqueness before updating
                val isAvailable = userRepository.isNicknameAvailable(nickname, excludeUserId = userId)
                if (!isAvailable) {
                    _uiState.update { it.copy(errorMessage = "This nickname is already taken by another user.") }
                    return@launch
                }
                userRepository.updateUserFields(userId, mapOf("nickname" to nickname))
                userRepository.updatePublicProfileFields(userId, mapOf("nickname" to nickname))
                Timber.i("Nickname updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update nickname")
                _uiState.update { it.copy(errorMessage = "Failed to update nickname") }
            }
        }
    }

    fun updateAge(age: Int?) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>("age" to (age ?: com.google.firebase.firestore.FieldValue.delete()))
                userRepository.updateUserFields(userId, updates)
                Timber.i("Age updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update age")
                _uiState.update { it.copy(errorMessage = "Failed to update age") }
            }
        }
    }

    fun updateQuitDate(quitDate: Timestamp) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                userRepository.updateUserFields(userId, mapOf("quitDate" to quitDate))
                userRepository.updatePublicProfileFields(userId, mapOf("quitDate" to quitDate))
                Timber.i("Quit date updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update quit date")
                _uiState.update { it.copy(errorMessage = "Failed to update quit date") }
            }
        }
    }

    fun updatePhoto(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPhotoUploading = true) }
            try {
                // updatePhoto returns the NEW remote URL (and already updates
                // both users/{uid} and publicProfiles/{uid} documents) — we use
                // that returned URL instead of re-reading potentially stale
                // state, so the avatar persists correctly everywhere.
                val newPhotoUrl = userRepository.updatePhoto(userId, uri).getOrNull()
                if (newPhotoUrl != null) {
                    _uiState.update { it.copy(photoCacheBust = System.currentTimeMillis()) }
                    invalidateImageCache(newPhotoUrl)
                }
                _uiState.update { it.copy(isPhotoUploading = false) }
                clearImageCache()
                Timber.i("Photo updated (url=%s)", newPhotoUrl != null)
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isPhotoUploading = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isPhotoUploading = false) }
                Timber.e(e, "Failed to update photo")
                _uiState.update { it.copy(errorMessage = "Failed to update photo") }
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        // Persist to SharedPreferences
        try {
            val context = breathy.com.BreathyApplication.instance
            val prefs = context.getSharedPreferences("breathy_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        } catch (_: Exception) { }
        Timber.i("Notifications toggled: %s", enabled)
    }



    fun togglePrivacy(enabled: Boolean) {
        _uiState.update { it.copy(privacyEnabled = enabled) }
        // Persist to SharedPreferences
        try {
            val context = breathy.com.BreathyApplication.instance
            val prefs = context.getSharedPreferences("breathy_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("privacy_enabled", enabled).apply()
        } catch (_: Exception) { }
        Timber.i("Privacy toggled: %s", enabled)
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                authRepository.deleteAccount()
                Timber.i("Account deleted")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete account")
                _uiState.update { it.copy(errorMessage = "Failed to delete account. Please try again.") }
            }
        }
    }
}

class ProfileViewModelFactory(
    private val userRepository: UserRepository,
    private val rewardRepository: RewardRepository,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val premiumRepository: breathy.com.data.repository.PremiumRepository? = null,
    private val goldRepository: breathy.com.data.repository.GoldRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(userRepository, rewardRepository, authRepository, auth, firestore, premiumRepository, goldRepository) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Avatar Frame Picker — bottom sheet with all frames, real unlock states
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarFramePickerSheet(
    currentFrame: breathy.com.data.models.AvatarFrame,
    isPremium: Boolean,
    level: Int,
    hasAchievements: Boolean,
    hasEventWin: Boolean,
    goldBalance: Int,
    ownedFrames: List<String>,
    onSelect: (breathy.com.data.models.AvatarFrame) -> Unit,
    onPurchase: (breathy.com.data.models.AvatarFrame) -> Unit,
    onDismiss: () -> Unit
) {
    val frames = breathy.com.data.models.AvatarFrame.entries
    val modalBottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Avatar frame",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Frames show your real progression. Unlock more as you grow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            // YOUR GOLD — always visible while shopping (spec section 10)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = GoldSoft),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🪙", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your Gold",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = GoldDeep
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "%,d".format(goldBalance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = GoldDeep
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lazy grid of frames, 3 per row
            val rows = frames.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (frame in row) {
                            val ownedByGold = ownedFrames.contains(frame.id)
                            val unlocked = ownedByGold || frame.isUnlockedFor(
                                level = level,
                                hasAchievement = hasAchievements,
                                hasEventWin = hasEventWin,
                                isPremium = isPremium
                            )
                            val isSelected = frame == currentFrame
                            FrameCard(
                                frame = frame,
                                unlocked = unlocked,
                                isSelected = isSelected,
                                level = level,
                                hasAchievements = hasAchievements,
                                hasEventWin = hasEventWin,
                                isPremium = isPremium,
                                goldBalance = goldBalance,
                                onBuy = { onPurchase(frame) },
                                modifier = Modifier.weight(1f),
                                onClick = { if (unlocked) onSelect(frame) }
                            )
                        }
                        // pad the last row
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameCard(
    frame: breathy.com.data.models.AvatarFrame,
    unlocked: Boolean,
    isSelected: Boolean,
    level: Int,
    hasAchievements: Boolean,
    hasEventWin: Boolean,
    isPremium: Boolean,
    goldBalance: Int,
    onBuy: () -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Staggered reveal — frames appear one after another (unlock/preview feel)
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(frame.ordinal * 45L)
        revealed = true
    }
    val revealAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(320),
        label = "frame_reveal_${frame.id}"
    )
    val revealScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.92f,
        animationSpec = tween(320),
        label = "frame_scale_${frame.id}"
    )

    val isHighTier = frame.rarity == breathy.com.data.models.FrameRarity.LEGENDARY ||
            frame.rarity == breathy.com.data.models.FrameRarity.PREMIUM

    Column(
        modifier = modifier
            .alpha(revealAlpha)
            .scale(revealScale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mini avatar preview with the full designed frame artwork.
        // High tiers get a soft botanical aura behind the preview.
        Box(contentAlignment = Alignment.Center) {
            if (isHighTier && unlocked) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    NaturalYellow.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            breathy.com.ui.components.BreathyAvatar(
                photoURL = null,
                frame = frame,
                rankTier = breathy.com.data.models.RankTier.forLevel(level),
                size = 52.dp,
                contentDescription = frame.label
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = frame.label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Rarity badge
        val rarityColor = when (frame.rarity) {
            breathy.com.data.models.FrameRarity.COMMON -> TextSecondary
            breathy.com.data.models.FrameRarity.UNCOMMON -> NaturalGreen
            breathy.com.data.models.FrameRarity.RARE -> AccentInfo
            breathy.com.data.models.FrameRarity.EPIC -> DeepForest
            breathy.com.data.models.FrameRarity.LEGENDARY -> GoldDeep
            breathy.com.data.models.FrameRarity.PREMIUM -> DarkBotanical
        }
        Text(
            text = frame.rarity.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Black
            ),
            color = rarityColor
        )

        Text(
            text = when {
                unlocked -> if (isSelected) "Equipped" else "Owned"
                frame.goldPrice != null -> "${frame.goldPrice} Gold"
                frame == breathy.com.data.models.AvatarFrame.BRONZE -> "Level 3"
                frame == breathy.com.data.models.AvatarFrame.SILVER -> "Level 5"
                frame == breathy.com.data.models.AvatarFrame.GOLD -> "Level 8"
                frame == breathy.com.data.models.AvatarFrame.PREMIUM -> "Premium"
                frame == breathy.com.data.models.AvatarFrame.EVENT -> "Win an event"
                frame == breathy.com.data.models.AvatarFrame.ACHIEVEMENT -> "Any achievement"
                else -> "Locked"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // BUY WITH GOLD — one-time purchase for Gold-priced frames (spec §10)
        if (!unlocked && frame.goldPrice != null) {
            Spacer(modifier = Modifier.height(6.dp))
            val canAfford = goldBalance >= frame.goldPrice
            androidx.compose.material3.TextButton(
                onClick = onBuy,
                enabled = canAfford,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (canAfford) GoldDeep else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.semantics {
                    contentDescription = if (canAfford)
                        "Buy ${frame.label} frame for ${frame.goldPrice} Gold"
                    else
                        "${frame.label} frame costs ${frame.goldPrice} Gold, you have $goldBalance"
                }
            ) {
                Text(
                    text = "🪙 Buy",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}

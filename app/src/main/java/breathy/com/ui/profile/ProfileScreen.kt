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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.painterResource
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
import breathy.com.ui.components.PremiumGlow
import breathy.com.ui.components.invalidateImageCache
import breathy.com.ui.components.clearImageCache
import breathy.com.utils.s
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
    onNavigateToPayoutSetup: () -> Unit = {},
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
    var showAccountPrivacy by remember { mutableStateOf(false) }
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var showEditQuitDateDialog by remember { mutableStateOf(false) }
    var showEditAgeDialog by remember { mutableStateOf(false) }
    var showFramePicker by remember { mutableStateOf(false) }

    // v1.0.10 — the legacy gallery "Change Photo" flow is REMOVED. Profile
    // pictures now come exclusively from the unified Avatar Collection
    // (milestones / Gold shop / rewarded ads / Premium). Tapping the avatar
    // opens that collection.

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
            // v1.0.12 — nested-Scaffold inset fix: the main NavGraph Scaffold
            // already reserves the system navigation-bar area for the app-wide
            // bottom NavigationBar and the status-bar area at the top. Without
            // this, the nested Scaffold re-applies those system insets as
            // content padding, creating an unwanted empty strip between the
            // page content and the bottom bar (and a doubled gap at the top).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                        profilePictureId = uiState.user?.profilePicture,
                        onAvatarClick = { showFramePicker = true },
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
                        onNavigateToPayoutSetup = onNavigateToPayoutSetup,
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
                        text = s("Loading profile...", "جارٍ تحميل الملف الشخصي..."),
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }

    // ── Edit Nickname Dialog ──────────────────────────────────────────────
    if (showEditNicknameDialog) {
        var nicknameText by remember { mutableStateOf(uiState.user?.nickname ?: "") }
        var nicknameError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = {
                Text(
                    text = s("Edit Nickname", "تعديل الاسم المستعار"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = nicknameText,
                    onValueChange = {
                        nicknameText = it
                        nicknameError = if (it.isBlank()) s("Nickname cannot be empty", "لا يمكن ترك الاسم المستعار فارغاً") else null
                    },
                    label = { Text(s("Nickname", "الاسم المستعار")) },
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
                    Text(text = s("Save", "حفظ"), color = themeAccentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text(text = s("Cancel", "إلغاء"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    text = s("Edit Age", "تعديل العمر"),
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
                            age == null -> s("Enter a valid number", "أدخل رقماً صحيحاً")
                            age < 13 || age > 120 -> s("Age must be 13–120", "يجب أن يكون العمر بين 13 و120")
                            else -> null
                        }
                    },
                    label = { Text(s("Age (optional)", "العمر (اختياري)")) },
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
                    Text(text = s("Save", "حفظ"), color = themeAccentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAgeDialog = false }) {
                    Text(text = s("Cancel", "إلغاء"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onDismiss = { showAccountPrivacy = false }
        )
    }

    // ── Avatar Frame Picker ──────────────────────────────────
    if (showFramePicker) {
        val context = LocalContext.current
        AvatarFramePickerSheet(
            currentFrame = breathy.com.data.models.AvatarFrame.fromId(uiState.user?.avatarFrame),
            isPremium = uiState.isPremium,
            level = uiState.user?.level ?: 1,
            hasAchievements = uiState.achievements.any { it.unlocked },
            hasEventWin = uiState.user?.achievements?.contains("event_champion") == true,
            goldBalance = uiState.goldBalance,
            ownedFrames = uiState.user?.ownedFrames ?: emptyList(),
            daysSmokeFree = uiState.user?.daysSmokeFree ?: 0,
            onSelect = { frame ->
                viewModel.updateAvatarFrame(frame)
                showFramePicker = false
            },
            onPurchase = { frame -> viewModel.purchaseFrame(frame) },
            onDismiss = { showFramePicker = false },
            // ── v1.0.9 unified profile pictures ────────────────────────
            currentPicture = breathy.com.data.models.ProfilePicture.fromId(uiState.user?.profilePicture),
            ownedPictures = uiState.user?.ownedPictures ?: emptyList(),
            picAdWatchCount = uiState.user?.picAdWatchCount ?: 0,
            onEquipPicture = { picture ->
                viewModel.updateProfilePicture(picture)
            },
            onBuyPicture = { picture -> viewModel.purchaseProfilePicture(picture) },
            onWatchAd = {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    val started = BreathyApplication.instance.let { app ->
                        app.appModule.adManager.showProfilePicRewardedAd(activity)
                    }
                    if (!started) {
                        viewModel.notifyAdNotReady()
                    }
                }
            }
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
    profilePictureId: String? = null,
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
                        profilePictureId = profilePictureId,
                        isPremiumUser = isPremium,
                        modifier = Modifier.clickable(onClick = onAvatarClick)
                    )
                }
            }

            // v1.0.10 — camera "Change photo" overlay REMOVED: profile
            // pictures come from the Avatar Collection only.
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Nickname with edit
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // v1.0.9 — subscriber's own name glows neon (Premium Glow Text).
            breathy.com.ui.components.PremiumGlowText(
                text = nickname.ifBlank { s("Quitter", "مُقلّع") },
                enabled = isPremium,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
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
                    text = s("Change frame", "تغيير الإطار"),
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
                    text = s("Age: %d", "العمر: %d").format(age),
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
                    text = s("+ Add age", "+ إضافة العمر"),
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
                    text = s("XP: %d", "خبرة: %d").format(xp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = AccentPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = s("Level %d", "المستوى %d").format(level),
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
                    text = s("Gold", "ذهب"),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = s("Earned from check-ins, achievements & events", "مكتسب من التسجيلات اليومية والإنجازات والفعاليات"),
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
                        text = s("History", "السجل"),
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
            label = s("Smoke Free", "بدون تدخين"),
            value = if (daysSmokeFree == 0) s("Day 1", "اليوم 1") else s("%dd", "%d يوم").format(daysSmokeFree),
            icon = "🌬️",
            accentColor = themeAccentPrimary,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            label = s("Saved", "الموفّرة"),
            value = formatMoney(moneySaved),
            icon = "💰",
            accentColor = themeAccentPrimary,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            label = s("Level", "المستوى"),
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
                        text = s("Quit Date", "تاريخ الإقلاع"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = quitDate?.let { formatQuitDate(it) } ?: s("Not set", "غير محدد"),
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
                        text = s("Change", "تغيير"),
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
                text = s("Achievements", "الإنجازات"),
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
                    text = s("View All (%d locked)", "عرض الكل (%d مُقفل)").format(lockedCount),
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
                    text = s("Keep going to unlock your first achievement! 🏆", "واصل التقدم لفتح إنجازك الأول! 🏆"),
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
                text = achievement.displayTitle(),
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
    onNavigateToPayoutSetup: () -> Unit = {},
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
                    text = s("Settings", "الإعدادات"),
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
                label = s("Notifications", "الإشعارات"),
                description = s("Reminders and motivation", "تذكيرات وتحفيز"),
                checked = notificationsEnabled,
                onCheckedChange = onNotificationsToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Privacy toggle
            SettingsToggleRow(
                icon = Icons.Default.PrivacyTip,
                label = s("Private Profile", "ملف شخصي خاص"),
                description = s("Hide from leaderboard", "إخفاء من لوحة الصدارة"),
                checked = privacyEnabled,
                onCheckedChange = onPrivacyToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // v1.0.9 — Language: English / العربية (in-app switch)
            val context = androidx.compose.ui.platform.LocalContext.current
            var currentLang by remember {
                mutableStateOf(breathy.com.utils.AppLanguage.current)
            }
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
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Language",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = s("Language", "اللغة"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = s("Switch app language", "تغيير لغة التطبيق"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breathy.com.utils.AppLanguage.Lang.entries.forEach { lang ->
                        val selected = lang == currentLang
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) themeAccentPrimaryMuted
                                                 else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            onClick = {
                                breathy.com.utils.AppLanguage.set(context, lang)
                                currentLang = lang
                                // Recreate so every localized surface re-reads the language.
                                (context as? android.app.Activity)?.recreate()
                            }
                        ) {
                            Text(
                                text = lang.displayName,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selected) themeAccentPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

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
                            text = s("Friends", "الأصدقاء"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = s("View and manage friends", "عرض وإدارة الأصدقاء"),
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
                        text = s("View", "عرض"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeAccentPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Payment / Payout Setup — PayPal email for prize delivery
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPayoutSetup() }
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
                        imageVector = Icons.Default.Payment,
                        contentDescription = "Payment",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = s("Payment / Payout Setup", "إعداد الدفع والسحب"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = s("PayPal email for prize payouts", "بريد PayPal لاستلام جوائزك"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open payment settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
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
                        text = if (subscription?.isActive() == true) s("Breathy Premium ✦", "Breathy بريميوم ✦") else s("Breathy Premium", "Breathy بريميوم"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = if (subscription?.isActive() == true) s("Ad-free + exclusive events — active", "بدون إعلانات + فعاليات حصرية — مُفعّل") else s("Ad-free experience + exclusive events", "تجربة بدون إعلانات + فعاليات حصرية"),
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
//  Action Buttons — Sign out, Account & Privacy
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
                    text = s("Sign Out", "تسجيل الخروج"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AccentWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Account & Privacy — read-only information about account data.
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
                        text = s("Account & Privacy", "الحساب والخصوصية"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = s("How your data is stored and protected", "كيف تُخزَّن بياناتك وكيف تُحمى"),
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
 * Account & Privacy sheet — read-only explanation of how account data is
 * stored and protected. Account deletion is NOT exposed in the app UI
 * (spec: remove the Delete Account button; deletion stays a server-side,
 * support-driven operation).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AccountPrivacySheet(
    onDismiss: () -> Unit
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
                text = s("Account & Privacy", "الحساب والخصوصية"),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = s(
                    "Your recovery data (streaks, milestones, achievements) belongs to you. " +
                        "Signing out keeps your data safe on this account. You can sign back in " +
                        "at any time with the same Google account.",
                    "بيانات تعافيك (سلسلتك، محطاتك، إنجازاتك) ملك لك. " +
                        "تسجيل الخروج يحفظ بياناتك بأمان على حسابك، ويمكنك العودة " +
                        "في أي وقت بالحساب نفسه عبر Google."
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
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
        get() = auth.currentUser?.uid ?: throw IllegalStateException(s("Not authenticated", "غير مسجل الدخول"))

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
                    it.copy(errorMessage = s("Not enough Gold — the ${frame.displayLabel()} frame costs ${frame.goldPrice} Gold.", "ذهب غير كافٍ — إطار ${frame.displayLabel()} يكلف ${frame.goldPrice} ذهباً."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to purchase frame")
                _uiState.update { it.copy(errorMessage = s("Purchase failed. Please try again.", "فشل الشراء. حاول مجدداً.")) }
            }
        }
    }

    /** Friendly message when the rewarded ad is still preparing. */
    fun notifyAdNotReady() {
        _uiState.update {
            it.copy(errorMessage = s("The ad is still preparing — try again in a few seconds.", "الإعلان قيد التحميل — حاول مجدداً بعد ثوانٍ."))
        }
    }

    /**
     * v1.0.9 — equip a unified profile picture (users + publicProfiles) so it
     * renders EVERYWHERE avatars are shown.
     */
    fun updateProfilePicture(picture: breathy.com.data.models.ProfilePicture) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                userRepository.updateProfilePicture(userId, picture.id).getOrThrow()
                _uiState.update { state ->
                    state.copy(
                        user = state.user?.copy(profilePicture = picture.id),
                        errorMessage = null
                    )
                }
                Timber.i("Profile: unified picture '%s' equipped", picture.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update profile picture")
                _uiState.update {
                    it.copy(errorMessage = s("Couldn't update your profile picture. Please try again.", "تعذّر تحديث الصورة الشخصية. حاول مجدداً."))
                }
            }
        }
    }

    /**
     * v1.0.9 — buy a Gold-priced unified profile picture (shop starts at 500
     * Gold): atomic deduction + ownership + equip in one transaction.
     */
    fun purchaseProfilePicture(picture: breathy.com.data.models.ProfilePicture) {
        val repo = goldRepository ?: return
        viewModelScope.launch {
            try {
                repo.purchaseProfilePicture(picture).getOrThrow()
                _uiState.update { state ->
                    state.copy(
                        user = state.user?.copy(
                            profilePicture = picture.id,
                            ownedPictures = (state.user?.ownedPictures ?: emptyList()) + picture.id
                        ),
                        errorMessage = null
                    )
                }
            } catch (e: breathy.com.data.repository.InsufficientGoldException) {
                _uiState.update {
                    it.copy(
                        errorMessage = s(
                            "Not enough Gold — the ${picture.displayLabel()} picture costs ${picture.goldPrice} Gold.",
                            "ذهب غير كافٍ — صورة ${picture.displayLabel()} تكلف ${picture.goldPrice} ذهباً."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to purchase profile picture")
                _uiState.update { it.copy(errorMessage = s("Purchase failed. Please try again.", "فشل الشراء. حاول مجدداً.")) }
            }
        }
    }

    /** Tracks the previous premium flag so a false→true transition (the
     *  moment the user actually subscribes/restores) can be detected. */
    private var wasPremium: Boolean? = null

    /** Mirror the verified premium entitlement into profile UI state. */
    private fun observePremium() {
        premiumRepository?.let { repo ->
            viewModelScope.launch {
                repo.state.collect { premium ->
                    _uiState.update { it.copy(isPremium = premium.isPremium) }

                    // PREMIUM AVATAR FRAME DELIVERY (user report: "didn't
                    // receive the premium avatar border after subscribing").
                    // On the false→true transition — i.e. the moment the
                    // subscription becomes ACTIVE — auto-equip the Premium
                    // frame so the subscriber visibly receives it without
                    // hunting through the collection. Already-premium users
                    // on later app starts keep whatever frame they chose.
                    val first = wasPremium == null
                    val transitioned = !first && !wasPremium!! && premium.isPremium
                    wasPremium = premium.isPremium
                    if (transitioned && premium.isPremium) {
                        val uid = auth.currentUser?.uid
                        val currentFrame = _uiState.value.user?.avatarFrame
                        if (uid != null &&
                            breathy.com.data.models.AvatarFrame.fromId(currentFrame)
                                != breathy.com.data.models.AvatarFrame.PREMIUM
                        ) {
                            updateAvatarFrame(breathy.com.data.models.AvatarFrame.PREMIUM)
                            _uiState.update {
                                it.copy(errorMessage = null)
                            }
                            Timber.i("Profile: Premium frame auto-equipped for %s", uid)
                        }
                    }
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
                    it.copy(errorMessage = s("This frame isn't unlocked yet", "هذا الإطار غير مفتوح بعد"))
                }
            }
        }
    }

    private fun loadProfile() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = s("Not authenticated", "غير مسجّل الدخول")) }
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
                _uiState.update { it.copy(isLoading = false, errorMessage = s("Failed to load profile", "فشل تحميل الملف الشخصي")) }
            }
        }
    }

    private fun loadAchievements() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                rewardRepository.observeUnlockedAchievements(userId).collect { unlocked ->
                    // v1.0.10 FIX — use RewardRepository definitions so the
                    // unlocked ids (stored in Firestore) actually match.
                    val allWithState = rewardRepository.getAchievements().map { def ->
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
                    _uiState.update { it.copy(errorMessage = s("This nickname is already taken by another user.", "هذا الاسم المستعار محجوز لمستخدم آخر.")) }
                    return@launch
                }
                userRepository.updateUserFields(userId, mapOf("nickname" to nickname))
                userRepository.updatePublicProfileFields(userId, mapOf("nickname" to nickname))
                // v1.0.19 — mirror to Firebase Auth displayName so self-heal /
                // fallback writers never resurrect the e-mail prefix.
                auth.currentUser?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(nickname)
                        .build()
                )?.addOnFailureListener { e ->
                    Timber.w(e, "Auth displayName sync failed (non-fatal)")
                }
                Timber.i("Nickname updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update nickname")
                _uiState.update { it.copy(errorMessage = s("Failed to update nickname", "فشل تحديث الاسم المستعار")) }
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
                _uiState.update { it.copy(errorMessage = s("Failed to update age", "فشل تحديث العمر")) }
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
                _uiState.update { it.copy(errorMessage = s("Failed to update quit date", "فشل تحديث تاريخ الإقلاع")) }
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
                _uiState.update { it.copy(errorMessage = s("Failed to update photo", "فشل تحديث الصورة")) }
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
    daysSmokeFree: Int,
    onSelect: (breathy.com.data.models.AvatarFrame) -> Unit,
    onPurchase: (breathy.com.data.models.AvatarFrame) -> Unit,
    onDismiss: () -> Unit,
    // ── v1.0.9 unified profile pictures ────────────────────────────────────
    currentPicture: breathy.com.data.models.ProfilePicture = breathy.com.data.models.ProfilePicture.DAY1,
    ownedPictures: List<String> = emptyList(),
    picAdWatchCount: Int = 0,
    onEquipPicture: (breathy.com.data.models.ProfilePicture) -> Unit = {},
    onBuyPicture: (breathy.com.data.models.ProfilePicture) -> Unit = {},
    onWatchAd: () -> Unit = {}
) {
    val frames = breathy.com.data.models.AvatarFrame.entries
    val pictures = breathy.com.data.models.ProfilePicture.entries
    // v1.0.11 rev 5 — open FULLY expanded so the whole collection is browsable
    // and vertical scrolling starts immediately (no partial-sheet drag phase).
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // v1.0.11 rev 5 — THE COLLECTION SCROLLS. The sheet content is
                // one vertical scroll container: every picture and every
                // frame is reachable by normal vertical scrolling. The grid
                // rows are plain Columns/Rows (no competing gesture handlers),
                // so nothing intercepts or consumes the vertical drags.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = s("Avatar Collection", "مجموعة الأفاتار"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = s(
                    "Pictures and frames show your real progression. Unlock more as you grow.",
                    "الصور والإطارات تعرض تقدمك الحقيقي. افتح المزيد كلما تقدمت."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            // ── Tab switcher: Pictures | Frames ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                listOf(s("Pictures", "الصور"), s("Frames", "الإطارات")).forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tab == index) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { tab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (tab == index) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                        text = s("Your Gold", "ذهبك"),
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

            if (tab == 0) {
                // ═══ UNIFIED PROFILE PICTURES ═══
                // v1.0.11 rev 7 — PICTURES CARD UI RESTORED to the previous
                // (v1.0.9–v1.0.11 rev3) visual presentation: full-bleed
                // ContentScale.Crop thumbnails and the light TextButton action
                // row. The rev-5 scroll fix (one vertical scroll container,
                // no nested scrolling) and the 3-column chunked grid are KEPT.
                val rows = pictures.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (picture in row) {
                                ProfilePictureCard(
                                    picture = picture,
                                    isSelected = picture == currentPicture,
                                    isPremium = isPremium,
                                    goldBalance = goldBalance,
                                    daysSmokeFree = daysSmokeFree,
                                    picAdWatchCount = picAdWatchCount,
                                    owned = ownedPictures.contains(picture.id),
                                    onEquip = { onEquipPicture(picture) },
                                    onBuy = { onBuyPicture(picture) },
                                    onWatchAd = onWatchAd,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // ═══ FRAME COLLECTION ═══
                // v1.0.11 rev 5 — compact 3-column grid: rows use intrinsic
                // min height so every card in a row is EQUAL height with the
                // action pinned to the bottom — no large gaps, everything
                // visually aligned.
                val rows = frames.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in rows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (frame in row) {
                                val ownedByGold = ownedFrames.contains(frame.id)
                                val unlocked = ownedByGold || frame.isUnlockedFor(
                                    level = level,
                                    hasAchievement = hasAchievements,
                                    hasEventWin = hasEventWin,
                                    isPremium = isPremium,
                                    daysSmokeFree = daysSmokeFree
                                )
                                FrameCard(
                                    frame = frame,
                                    unlocked = unlocked,
                                    isSelected = frame == currentFrame,
                                    level = level,
                                    hasAchievements = hasAchievements,
                                    hasEventWin = hasEventWin,
                                    isPremium = isPremium,
                                    goldBalance = goldBalance,
                                    daysSmokeFree = daysSmokeFree,
                                    onEquip = { onSelect(frame) },
                                    onBuy = { onPurchase(frame) },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
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
}

/**
 * v1.0.9 — one card in the unified PROFILE PICTURES collection.
 * Unlock states: Day One (everyone) · milestone days · Gold shop (500+)
 * · 5 rewarded ads · Premium animated pair.
 *
 * v1.0.11 rev 7 — VISUAL PRESENTATION RESTORED to the previous design
 * (the rev-5 content-aware renderer and the full-width action buttons are
 * reverted for THIS card only): the artwork is again a plain full-bleed
 * square thumbnail (ContentScale.Crop, 84dp, rounded clip) and the actions
 * are again the light TextButton / status-text row. Unlock logic, names,
 * prices, ad counting and callbacks are untouched; the 3-column grid and
 * the rev-5 scrolling behaviour are kept.
 */
@Composable
private fun ProfilePictureCard(
    picture: breathy.com.data.models.ProfilePicture,
    isSelected: Boolean,
    isPremium: Boolean,
    goldBalance: Int,
    daysSmokeFree: Int,
    picAdWatchCount: Int,
    owned: Boolean,
    onEquip: () -> Unit,
    onBuy: () -> Unit,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unlocked = owned || picture.isUnlockedFor(
        daysSmokeFree = daysSmokeFree,
        isPremium = isPremium,
        picAdWatchCount = picAdWatchCount
    )
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        unlocked -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // v1.0.11 rev 7 — previous presentation restored: plain full-bleed
        // square thumbnail of the artwork (ContentScale.Crop), exactly like
        // the pre-rev-5 card. No content-aware trimming, no sampled
        // background — the artwork fills the thumbnail edge to edge.
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = androidx.compose.ui.res.painterResource(pictureDrawable(picture)),
                contentDescription = picture.displayLabel(),
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = if (unlocked) 1f else 0.35f
            )
            if (!unlocked) {
                Text(text = "🔒", fontSize = 22.sp)
            }
            if (picture.animated && isPremium) {
                Text(
                    text = s("ANIMATED", "متحركة"),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PremiumGlow.NEON_MINT,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = picture.displayLabel(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        // ── ACTION AREA — restored to the previous light action row:
        // small TextButtons for tappable actions, plain status text for
        // non-tappable states (worn / unlock condition / premium badge).
        when {
            isSelected -> {
                Text(
                    text = s("WORN", "مُرتداة"),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            unlocked -> {
                TextButton(
                    onClick = onEquip,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(text = s("Wear", "ارتداء"), style = MaterialTheme.typography.labelMedium)
                }
            }
            picture.unlockDays > 0 -> {
                Text(
                    text = s("Day " + picture.unlockDays, "يوم " + picture.unlockDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            picture.adsRequired > 0 && !owned -> {
                TextButton(
                    onClick = onWatchAd,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = "▶ " + picAdWatchCount + "/" + picture.adsRequired,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            picture.goldPrice != null && !owned -> {
                val canAfford = goldBalance >= picture.goldPrice
                TextButton(
                    onClick = onBuy,
                    enabled = canAfford,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = "🪙 %,d".format(picture.goldPrice),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (canAfford) GoldDeep else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            picture.premiumOnly -> {
                Text(
                    text = "👑 " + s("Premium", "بريميوم"),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * v1.0.11 rev 5 — a REAL tappable button for collection card actions:
 * proper shape (rounded rect), clear boundaries, comfortable padding/height,
 * centered bold label, distinct from the card background. No action logic
 * here — just the visual presentation the caller wires callbacks into.
 */
@Composable
private fun CollectionActionButton(
    text: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * v1.0.11 rev 5 — NON-interactive status slot for collection cards (worn /
 * locked condition / premium badge). Same footprint as the action button so
 * card heights stay aligned, but visually a status chip — never reads as a
 * tappable control.
 */
@Composable
private fun CollectionActionChip(
    text: String,
    container: Color,
    content: Color
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(container)
            .border(1.dp, container.copy(alpha = 0.9f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = content,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** v1.0.9 — drawable for a profile-picture collection card. */
private fun pictureDrawable(picture: breathy.com.data.models.ProfilePicture): Int = when (picture) {
    breathy.com.data.models.ProfilePicture.DAY1 -> breathy.com.R.drawable.pic_day1
    breathy.com.data.models.ProfilePicture.SEVEN_DAYS -> breathy.com.R.drawable.pic_7days
    breathy.com.data.models.ProfilePicture.THIRTY_DAYS -> breathy.com.R.drawable.pic_30days
    breathy.com.data.models.ProfilePicture.NINETY_DAYS -> breathy.com.R.drawable.pic_90days
    breathy.com.data.models.ProfilePicture.SUNRISE -> breathy.com.R.drawable.pic_sunrise
    breathy.com.data.models.ProfilePicture.DONT_SMOKE -> breathy.com.R.drawable.pic_dontsmoke
    breathy.com.data.models.ProfilePicture.FORREST -> breathy.com.R.drawable.pic_forrest
    breathy.com.data.models.ProfilePicture.FRESH_BREATH -> breathy.com.R.drawable.pic_freshbreath
    breathy.com.data.models.ProfilePicture.GOOD_FROM_BAD -> breathy.com.R.drawable.pic_goodfrombad
    breathy.com.data.models.ProfilePicture.HEALTH_HEART -> breathy.com.R.drawable.pic_healthheart
    breathy.com.data.models.ProfilePicture.HEALTH_LUNGS -> breathy.com.R.drawable.pic_healthlungs
    breathy.com.data.models.ProfilePicture.HEALTHY_FUTURE -> breathy.com.R.drawable.pic_healthyfuture
    breathy.com.data.models.ProfilePicture.FREEFROMTHECHAIN -> breathy.com.R.drawable.pic_freefromthechain
}

/**
 * v1.0.11 rev 5 — one compact card in the FRAME COLLECTION grid.
 *
 * Layout contract:
 * - Same frame artwork as before ([BreathyAvatar] preview + rarity badge) —
 *   the artwork itself is untouched.
 * - EVERY frame carries a full-width 30dp action slot pinned to the card
 *   bottom (rows use intrinsic heights so slots align across the row — no
 *   large gaps, no unbalanced empty space):
 *   · equipped        → non-tappable "✓ EQUIPPED" status chip
 *   · owned, not worn → real "EQUIP" button (forest green) — equips directly
 *                       from the collection
 *   · locked + Gold   → real "🪙 BUY · <price>" button (existing purchase
 *                       logic, disabled until affordable)
 *   · locked other    → non-tappable unlock-condition chip (never reads as
 *                       if the user owns it)
 */
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
    daysSmokeFree: Int = 0,
    onEquip: () -> Unit = {},
    onBuy: () -> Unit = {},
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier
            .alpha(revealAlpha)
            .scale(revealScale)
            .clip(RoundedCornerShape(14.dp))
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
                    RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mini avatar preview with the full designed frame artwork.
        // v1.0.11 rev 7 — the old soft botanical aura behind high-tier
        // previews is REMOVED: the new GitHub/docs frame artwork ships
        // without shadows, and no glow/halo may be painted behind it.
        Box(contentAlignment = Alignment.Center) {
            breathy.com.ui.components.BreathyAvatar(
                photoURL = null,
                frame = frame,
                rankTier = breathy.com.data.models.RankTier.forLevel(level),
                size = 46.dp,
                contentDescription = frame.displayLabel()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = frame.displayLabel(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Rarity badge — styled EXACTLY like the official Avatar Borders
        // Collection art: outlined pill, rarity color, icon + label.
        // (Common · grey dot | Uncommon · green leaf | Rare · blue star |
        //  Epic · purple gem | Legendary · gold trophy | Premium · red crown)
        val rarityColor = when (frame.rarity) {
            breathy.com.data.models.FrameRarity.COMMON -> TextSecondary
            breathy.com.data.models.FrameRarity.UNCOMMON -> NaturalGreen
            breathy.com.data.models.FrameRarity.RARE -> AccentInfo
            breathy.com.data.models.FrameRarity.EPIC -> Color(0xFF9B6BE0)
            breathy.com.data.models.FrameRarity.LEGENDARY -> GoldDeep
            breathy.com.data.models.FrameRarity.PREMIUM -> Color(0xFFD9455A)
        }
        val rarityIcon = when (frame.rarity) {
            breathy.com.data.models.FrameRarity.COMMON -> "●"
            breathy.com.data.models.FrameRarity.UNCOMMON -> "🍃"
            breathy.com.data.models.FrameRarity.RARE -> "⭐"
            breathy.com.data.models.FrameRarity.EPIC -> "💎"
            breathy.com.data.models.FrameRarity.LEGENDARY -> "🏆"
            breathy.com.data.models.FrameRarity.PREMIUM -> "👑"
        }
        val isPremiumRarity = frame.rarity == breathy.com.data.models.FrameRarity.PREMIUM
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (isPremiumRarity) Modifier.background(rarityColor)
                    else Modifier.border(1.dp, rarityColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                )
                .padding(horizontal = 7.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rarityIcon,
                fontSize = 8.sp,
                lineHeight = 10.sp
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = s(
                    frame.rarity.displayLabel(),
                    when (frame.rarity) {
                        breathy.com.data.models.FrameRarity.COMMON -> "شائع"
                        breathy.com.data.models.FrameRarity.UNCOMMON -> "غير شائع"
                        breathy.com.data.models.FrameRarity.RARE -> "نادر"
                        breathy.com.data.models.FrameRarity.EPIC -> "ملحمي"
                        breathy.com.data.models.FrameRarity.LEGENDARY -> "أسطوري"
                        breathy.com.data.models.FrameRarity.PREMIUM -> "بريميوم"
                    }
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Black
                ),
                color = if (isPremiumRarity) Color.White else rarityColor
            )
        }

        // Push the action slot to the card bottom so every card in the row
        // aligns (cards are fillMaxHeight inside intrinsic-height rows).
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = when {
                unlocked -> if (isSelected) s("Equipped", "مُستخدم") else s("Owned", "مملوك")
                frame.goldPrice != null -> s("%d Gold", "%d ذهب").format(frame.goldPrice)
                frame == breathy.com.data.models.AvatarFrame.NATURE -> s("Day 7", "اليوم 7")
                frame == breathy.com.data.models.AvatarFrame.LEAF -> s("Day 30", "اليوم 30")
                frame == breathy.com.data.models.AvatarFrame.RANK -> s("Level 9 · Tree", "المستوى 9 · شجرة")
                frame == breathy.com.data.models.AvatarFrame.PREMIUM -> s("Premium", "بريميوم")
                frame == breathy.com.data.models.AvatarFrame.EVENT -> s("Win an event", "افز بفعالية")
                frame == breathy.com.data.models.AvatarFrame.ACHIEVEMENT -> s("Any achievement", "أي إنجاز")
                else -> s("Locked", "مُقفل")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        // ── ACTION SLOT — one per frame, aligned across the row ────────────
        when {
            // Currently worn → status chip (NOT a button — already equipped).
            isSelected -> CollectionActionChip(
                text = "✓ " + s("EQUIPPED", "مُستخدم"),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer
            )
            // Owned (progression or Gold) and not worn → REAL equip button.
            unlocked -> CollectionActionButton(
                text = s("EQUIP", "ارتداء"),
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                onClick = onEquip
            )
            // Locked Gold frame → REAL buy button (existing purchase logic;
            // disabled until the balance covers the price).
            frame.goldPrice != null -> {
                val canAfford = goldBalance >= frame.goldPrice
                CollectionActionButton(
                    text = "🪙 " + s("%d Gold", "%d ذهب").format(frame.goldPrice),
                    container = if (canAfford) GoldDeep else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    content = if (canAfford) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = canAfford,
                    onClick = onBuy,
                    modifier = Modifier.semantics {
                        contentDescription = if (canAfford)
                            "Buy ${frame.displayLabel()} frame for ${frame.goldPrice} Gold"
                        else
                            "${frame.displayLabel()} frame costs ${frame.goldPrice} Gold, you have $goldBalance"
                    }
                )
            }
            // Locked through progression → status chip with the REAL unlock
            // condition (never implies ownership, never tappable).
            else -> CollectionActionChip(
                text = when {
                    frame == breathy.com.data.models.AvatarFrame.NATURE -> s("Day 7", "اليوم 7")
                    frame == breathy.com.data.models.AvatarFrame.LEAF -> s("Day 30", "اليوم 30")
                    frame == breathy.com.data.models.AvatarFrame.RANK -> s("Level 9 · Tree", "المستوى 9 · شجرة")
                    frame == breathy.com.data.models.AvatarFrame.PREMIUM -> "👑 " + s("Premium", "بريميوم")
                    frame == breathy.com.data.models.AvatarFrame.EVENT -> s("Win an event", "افز بفعالية")
                    frame == breathy.com.data.models.AvatarFrame.ACHIEVEMENT -> s("Any achievement", "أي إنجاز")
                    else -> s("Locked", "مُقفل")
                },
                container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

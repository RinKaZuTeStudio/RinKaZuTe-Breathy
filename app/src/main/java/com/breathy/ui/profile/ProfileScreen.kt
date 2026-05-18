package com.breathy.ui.profile

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import com.breathy.ui.components.NetworkImage
import com.breathy.BreathyApplication
import com.breathy.data.models.Achievement
import com.breathy.data.models.Subscription
import com.breathy.data.models.User
import com.breathy.data.repository.AuthRepository
import com.breathy.data.repository.RewardRepository
import com.breathy.data.repository.UserRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentPurple

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
    onNavigateToAICoach: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: ProfileViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        viewModel(factory = ProfileViewModelFactory(
            userRepository = app.appModule.userRepository,
            rewardRepository = app.appModule.rewardRepository,
            authRepository = app.appModule.authRepository,
            auth = app.appModule.firebaseAuth,
            firestore = app.appModule.firestore
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var showEditQuitDateDialog by remember { mutableStateOf(false) }
    var showEditAgeDialog by remember { mutableStateOf(false) }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
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
                        onAvatarClick = { photoPickerLauncher.launch("image/*") },
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
                        darkModeEnabled = uiState.darkModeEnabled,
                        privacyEnabled = uiState.privacyEnabled,
                        onNotificationsToggle = { viewModel.toggleNotifications(it) },
                        onDarkModeToggle = { viewModel.toggleDarkMode(it) },
                        onPrivacyToggle = { viewModel.togglePrivacy(it) },
                        onNavigateToFriends = onNavigateToFriends
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
                        onDeleteAccount = { showDeleteDialog = true }
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
                    CircularProgressIndicator(color = AccentPrimary)
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
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                        cursorColor = AccentPrimary,
                        focusedLabelColor = AccentPrimary,
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
                    Text(text = "Save", color = AccentPrimary, fontWeight = FontWeight.Bold)
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
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                        cursorColor = AccentPrimary,
                        focusedLabelColor = AccentPrimary,
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
                    Text(text = "Save", color = AccentPrimary, fontWeight = FontWeight.Bold)
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
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                viewModel.updateQuitDate(Timestamp(newCal.time))
                showEditQuitDateDialog = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
        showEditQuitDateDialog = false
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
    onAvatarClick: () -> Unit,
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

            // Avatar
            Card(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onAvatarClick
            ) {
                if (!photoURL.isNullOrBlank()) {
                    NetworkImage(
                        model = photoURL,
                        contentDescription = "Your avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default avatar",
                            tint = AccentPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
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

        Spacer(modifier = Modifier.height(8.dp))

        // XP progress bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
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
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { levelProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AccentPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Stats Section — Days smoke-free, money saved, XP, level
// ═══════════════════════════════════════════════════════════════════════════════

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
            accentColor = AccentPrimary,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            label = "Saved",
            value = formatMoney(moneySaved),
            icon = "💰",
            accentColor = AccentPrimary,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            label = "Level",
            value = "$level",
            icon = "⭐",
            accentColor = AccentPurple,
            modifier = Modifier.weight(1f)
        )
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    icon: String,
    accentColor: Color = AccentPrimary,
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
                        tint = AccentPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentPrimary,
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
                colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.1f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onViewAll
            ) {
                Text(
                    text = "View All ($lockedCount locked)",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentPrimary,
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
        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.1f)),
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
                    color = AccentPrimary,
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
    darkModeEnabled: Boolean,
    privacyEnabled: Boolean,
    onNotificationsToggle: (Boolean) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onPrivacyToggle: (Boolean) -> Unit,
    onNavigateToFriends: () -> Unit = {}
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

            // Theme mode selector
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
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "Choose light or dark appearance",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                // Theme mode dropdown
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(
                            text = when (darkModeEnabled) {
                                true -> "Dark"
                                false -> "Light"
                            },
                            color = AccentPrimary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select theme",
                            tint = AccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Light") },
                            onClick = {
                                expanded = false
                                onDarkModeToggle(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dark") },
                            onClick = {
                                expanded = false
                                onDarkModeToggle(true)
                            }
                        )
                    }
                }
            }

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
                    colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.1f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    onClick = onNavigateToFriends
                ) {
                    Text(
                        text = "View",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentPrimary,
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
                checkedTrackColor = AccentPrimary,
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
                AccentPrimary.copy(alpha = 0.1f)
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
                    contentDescription = "Subscription",
                    tint = if (subscription?.isActive() == true) AccentPrimary else AccentPurple,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (subscription?.isActive() == true) "Supporter ✨" else "Support Breathy",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = if (subscription?.isActive() == true) "Thank you for your support!" else "Ad-free + supporter badge",
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
                tint = AccentPrimary,
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
    onDeleteAccount: () -> Unit
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
                    tint = SemanticWarning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = SemanticWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Delete account
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = onDeleteAccount
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete account",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Account",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                )
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
    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
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
    val darkModeEnabled: Boolean = true,
    val privacyEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val rewardRepository: RewardRepository,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val uid: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    init {
        loadProfile()
        loadAchievements()
        loadSubscription()
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
            try {
                userRepository.updatePhoto(userId, uri)
                Timber.i("Photo updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update photo")
                _uiState.update { it.copy(errorMessage = "Failed to update photo") }
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        // Persist to SharedPreferences or Firestore in production
        Timber.i("Notifications toggled: %s", enabled)
    }

    fun toggleDarkMode(enabled: Boolean) {
        _uiState.update { it.copy(darkModeEnabled = enabled) }
        // Save to SharedPreferences so MainActivity can read it on recreation
        try {
            val context = com.breathy.BreathyApplication.instance
            val prefs = context.getSharedPreferences("breathy_prefs", android.content.Context.MODE_PRIVATE)
            // "enabled" means dark mode ON, "not enabled" means light mode ON
            // We save LIGHT or DARK directly (not SYSTEM) since this is an explicit toggle
            prefs.edit().putString("theme_mode", if (enabled) "DARK" else "LIGHT").apply()
        } catch (_: Exception) { }
        Timber.i("Theme mode changed: %s", if (enabled) "DARK" else "LIGHT")
    }

    fun togglePrivacy(enabled: Boolean) {
        _uiState.update { it.copy(privacyEnabled = enabled) }
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
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(userRepository, rewardRepository, authRepository, auth, firestore) as T
    }
}

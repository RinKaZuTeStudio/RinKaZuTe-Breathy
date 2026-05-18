package breathy.com.ui.notifications

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.themeAccentPrimary
import breathy.com.ui.theme.themeAccentPrimaryMuted
import breathy.com.ui.theme.themeTextSecondary
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

// ── Data Model ──────────────────────────────────────────────────────────────

data class BreathyNotification(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val read: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
    val data: Map<String, String> = emptyMap()
) {
    fun timeAgo(): String {
        val diff = System.currentTimeMillis() - createdAt.toDate().time
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 10080 -> "${minutes / 1440}d ago"
            else -> "${minutes / 10080}w ago"
        }
    }
    
    fun icon(): ImageVector = when (type) {
        "friend_request", "friend_accepted" -> Icons.Default.People
        "achievement" -> Icons.Default.EmojiEvents
        "chat" -> Icons.Default.Chat
        "daily_reminder" -> Icons.Default.Today
        "event" -> Icons.Default.Event
        else -> Icons.Default.Notifications
    }
    
    fun accentColor() = when (type) {
        "friend_request", "friend_accepted" -> AccentPrimary
        "achievement" -> AccentPurple
        "chat" -> AccentPrimary
        "event" -> AccentPurple
        else -> AccentPrimary
    }
}

// ── UI State ────────────────────────────────────────────────────────────────

data class NotificationsUiState(
    val notifications: List<BreathyNotification> = emptyList(),
    val isLoading: Boolean = true,
    val unreadCount: Int = 0
)

// ── ViewModel ───────────────────────────────────────────────────────────────

class NotificationsViewModel(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                observeNotifications(userId).collect { notifications ->
                    _uiState.update { state ->
                        state.copy(
                            notifications = notifications,
                            unreadCount = notifications.count { !it.read },
                            isLoading = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load notifications")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNotifications(userId: String): Flow<List<BreathyNotification>> = callbackFlow {
        val registration = firestore.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Notifications listener error")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { doc ->
                        try {
                            BreathyNotification(
                                id = doc.id,
                                type = doc.getString("type") ?: "",
                                title = doc.getString("title") ?: "",
                                message = doc.getString("message") ?: "",
                                read = doc.getBoolean("read") ?: false,
                                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                                data = (doc.get("data") as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
                                    if (k is String && v is String) k to v else null
                                }?.toMap() ?: emptyMap()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(notifications)
                }
            }
        awaitClose { registration.remove() }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("notifications").document(notificationId)
                    .update("read", true).await()
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark notification as read")
            }
        }
    }

    fun markAllAsRead() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val unread = _uiState.value.notifications.filter { !it.read }
                val batch = firestore.batch()
                unread.forEach { notification ->
                    batch.update(
                        firestore.collection("notifications").document(notification.id),
                        "read", true
                    )
                }
                if (unread.isNotEmpty()) batch.commit().await()
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark all as read")
            }
        }
    }
}

class NotificationsViewModelFactory(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NotificationsViewModel(firestore, auth) as T
    }
}

// ── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit = {},
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModelFactory(
        firestore = (LocalContext.current.applicationContext as BreathyApplication).appModule.firestore,
        auth = (LocalContext.current.applicationContext as BreathyApplication).appModule.firebaseAuth
    ))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    if (uiState.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = themeAccentPrimaryMuted)
                        ) {
                            Text(
                                text = "${uiState.unreadCount}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeAccentPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            actions = {
                if (uiState.unreadCount > 0) {
                    TextButton(onClick = { viewModel.markAllAsRead() }) {
                        Text(
                            text = "Mark all read",
                            color = themeAccentPrimary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = themeAccentPrimary)
                }
            }
            uiState.notifications.isEmpty() -> {
                EmptyNotificationsState()
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(uiState.notifications, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onClick = {
                                if (!notification.read) {
                                    viewModel.markAsRead(notification.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: BreathyNotification,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.read) {
                themeAccentPrimaryMuted.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = notification.accentColor().copy(alpha = 0.15f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = notification.icon(),
                        contentDescription = notification.type,
                        tint = notification.accentColor(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (!notification.read) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (!notification.read) MaterialTheme.colorScheme.onBackground 
                               else themeTextSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.timeAgo(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = themeTextSecondary,
                        fontSize = 10.sp
                    )
                )
            }
            
            if (!notification.read) {
                Spacer(modifier = Modifier.width(8.dp))
                Card(
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentPrimary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyNotificationsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "No notifications",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No notifications yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "When you get notifications, they'll show up here",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = themeTextSecondary
                )
            )
        }
    }
}

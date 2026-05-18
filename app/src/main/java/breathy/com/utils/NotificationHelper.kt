package breathy.com.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import breathy.com.MainActivity
import breathy.com.R
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Helper for creating and managing Breathy's notification channels,
 * building and posting notifications with deep-link routing, and
 * managing FCM tokens.
 *
 * Channels:
 * 1. Chat            — new direct messages (IMPORTANCE_HIGH)
 * 2. Friend Requests — incoming friend requests (IMPORTANCE_HIGH)
 * 3. Events          — challenge reminders, check-in deadlines, approvals (IMPORTANCE_HIGH)
 * 4. Achievements    — achievement unlocks, milestones (IMPORTANCE_DEFAULT)
 * 5. Daily Reminder  — daily motivation, streak reminders (IMPORTANCE_DEFAULT)
 *
 * Android 13+ (API 33) requires POST_NOTIFICATIONS runtime permission.
 * All `notify()` calls are guarded by a permission check to avoid
 * [SecurityException] on API 33+ devices.
 */
class NotificationHelper(
    private val context: Context
) {

    companion object {
        // ── Channel IDs ──────────────────────────────────────────────────────
        const val CHANNEL_CHAT = "chat"
        const val CHANNEL_FRIEND_REQUESTS = "friend_requests"
        const val CHANNEL_EVENTS = "events"
        const val CHANNEL_ACHIEVEMENTS = "achievements"
        const val CHANNEL_DAILY_REMINDER = "daily_reminder"

        // ── Notification Group Keys ──────────────────────────────────────────
        const val GROUP_CHAT = "breathy.com.CHAT"
        const val GROUP_FRIEND_REQUESTS = "breathy.com.FRIEND_REQUESTS"
        const val GROUP_EVENTS = "breathy.com.EVENTS"
        const val GROUP_ACHIEVEMENTS = "breathy.com.ACHIEVEMENTS"

        // ── Request Codes for PendingIntents ─────────────────────────────────
        private const val REQUEST_CODE_CHAT = 2001
        private const val REQUEST_CODE_FRIEND = 2002
        private const val REQUEST_CODE_EVENT = 2003
        private const val REQUEST_CODE_ACHIEVEMENT = 2004
        private const val REQUEST_CODE_DAILY = 2005

        // ── Notification ID Ranges ───────────────────────────────────────────
        // Each type gets a 1000-ID range to avoid collisions
        private const val ID_BASE_CHAT = 3000
        private const val ID_BASE_FRIEND = 4000
        private const val ID_BASE_EVENT = 5000
        private const val ID_BASE_ACHIEVEMENT = 6000
        private const val ID_BASE_DAILY = 7000

        // ── FCM Topic Prefixes ───────────────────────────────────────────────
        private const val FCM_TOPIC_PREFIX = "breathy_"
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Counter for generating unique notification IDs within each range.
     * Uses modular arithmetic to stay within the 1000-ID range per type.
     */
    private val idCounters = mutableMapOf(
        CHANNEL_CHAT to 0,
        CHANNEL_FRIEND_REQUESTS to 0,
        CHANNEL_EVENTS to 0,
        CHANNEL_ACHIEVEMENTS to 0,
        CHANNEL_DAILY_REMINDER to 0
    )

    init {
        createNotificationChannels()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Notification Channel Creation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create all notification channels. Safe to call multiple times —
     * creating an existing channel performs no operation.
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_CHAT,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New direct messages from friends"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 50, 100)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#00E676") // AccentPrimary
            },
            NotificationChannel(
                CHANNEL_FRIEND_REQUESTS,
                "Friend Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming friend requests"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#FF4081") // AccentPink
            },
            NotificationChannel(
                CHANNEL_EVENTS,
                "Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Challenge reminders, check-in deadlines, and admin approvals"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 300)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#448AFF") // AccentSecondary
            },
            NotificationChannel(
                CHANNEL_ACHIEVEMENTS,
                "Achievements",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Achievement unlocks and smoke-free milestones"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#B388FF") // AccentPurple
            },
            NotificationChannel(
                CHANNEL_DAILY_REMINDER,
                "Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily motivational quotes and streak reminders"
                enableVibration(false)
                setShowBadge(true)
            }
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        channels.forEach { manager.createNotificationChannel(it) }
        Timber.d("Created %d notification channels", channels.size)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Permission Check — Android 13+
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check whether the POST_NOTIFICATIONS permission has been granted.
     *
     * On Android 13 (API 33) and above, apps must request the runtime
     * permission `android.permission.POST_NOTIFICATIONS` before posting
     * notifications. On older versions, this always returns `true`.
     *
     * @return `true` if notifications can be posted, `false` otherwise.
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Get the permission string required for posting notifications on
     * the current API level, or `null` if no runtime permission is needed.
     *
     * Useful for requesting the permission from an Activity or Fragment:
     * ```
     * val permission = notificationHelper.requiredPermission
     * if (permission != null) {
     *     requestPermissions(arrayOf(permission), REQUEST_CODE)
     * }
     * ```
     */
    val requiredPermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Notification Builders
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Show a chat message notification.
     *
     * @param id              Unique notification ID. If `null`, one is auto-generated.
     * @param senderName      Display name of the message sender.
     * @param messagePreview  Preview text of the message (first line).
     * @param chatId          The chat document ID for deep-link routing.
     * @param senderId        The sender's Firebase UID (used for grouping).
     * @param senderPhotoUrl  Optional profile photo URL for the sender.
     */
    fun showChatNotification(
        id: Int? = null,
        senderName: String,
        messagePreview: String,
        chatId: String,
        senderId: String,
        senderPhotoUrl: String? = null
    ) {
        if (!hasNotificationPermission()) {
            Timber.w("Cannot show chat notification: POST_NOTIFICATIONS permission not granted")
            return
        }

        val notificationId = id ?: generateId(CHANNEL_CHAT)
        val route = "chat/$chatId"

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$senderName: $messagePreview")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(GROUP_CHAT)
            .setContentIntent(buildDeepLinkPendingIntent(route, REQUEST_CODE_CHAT))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .apply {
                // Add person for Android 11+ conversation bubbles
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val person = androidx.core.app.Person.Builder()
                        .setName(senderName)
                        .setKey(senderId)
                        .apply {
                            senderPhotoUrl?.let { iconUri ->
                                setIcon(
                                    androidx.core.graphics.drawable.IconCompat.createWithContentUri(iconUri)
                                )
                            }
                        }
                        .build()

                    val messagingStyle = NotificationCompat.MessagingStyle(person)
                        .addMessage(
                            NotificationCompat.MessagingStyle.Message(
                                messagePreview,
                                System.currentTimeMillis(),
                                person
                            )
                        )
                    setStyle(messagingStyle)
                }
            }
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Timber.d("Chat notification shown: id=%d, sender=%s", notificationId, senderName)
        } catch (e: SecurityException) {
            Timber.w(e, "SecurityException posting chat notification — permission likely revoked")
        }
    }

    /**
     * Show a friend request notification.
     *
     * @param id            Unique notification ID. If `null`, one is auto-generated.
     * @param senderName    Display name of the user who sent the request.
     * @param requestId     The friend request document ID.
     * @param senderId      The sender's Firebase UID.
     */
    fun showFriendRequestNotification(
        id: Int? = null,
        senderName: String,
        requestId: String,
        senderId: String
    ) {
        if (!hasNotificationPermission()) {
            Timber.w("Cannot show friend request notification: permission not granted")
            return
        }

        val notificationId = id ?: generateId(CHANNEL_FRIEND_REQUESTS)
        val route = "friends"

        val notification = NotificationCompat.Builder(context, CHANNEL_FRIEND_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Friend Request")
            .setContentText("$senderName wants to be your friend!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$senderName wants to be your friend! Tap to view and respond.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(GROUP_FRIEND_REQUESTS)
            .setContentIntent(buildDeepLinkPendingIntent(route, REQUEST_CODE_FRIEND))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Timber.d("Friend request notification shown: id=%d, from=%s", notificationId, senderName)
        } catch (e: SecurityException) {
            Timber.w(e, "SecurityException posting friend request notification")
        }
    }

    /**
     * Show an event notification (challenge reminder, check-in approval, etc.).
     *
     * @param id          Unique notification ID. If `null`, one is auto-generated.
     * @param title       Notification title (e.g., "Challenge Starting!").
     * @param message     Notification body text.
     * @param eventId     The event document ID for deep-link routing.
     * @param groupKey    Optional group key for bundling related notifications.
     */
    fun showEventNotification(
        id: Int? = null,
        title: String,
        message: String,
        eventId: String,
        groupKey: String = GROUP_EVENTS
    ) {
        if (!hasNotificationPermission()) {
            Timber.w("Cannot show event notification: permission not granted")
            return
        }

        val notificationId = id ?: generateId(CHANNEL_EVENTS)
        val route = "eventChallenge/$eventId"

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setContentIntent(buildDeepLinkPendingIntent(route, REQUEST_CODE_EVENT))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Timber.d("Event notification shown: id=%d, title=%s", notificationId, title)
        } catch (e: SecurityException) {
            Timber.w(e, "SecurityException posting event notification")
        }
    }

    /**
     * Show an achievement unlock notification.
     *
     * @param id               Unique notification ID. If `null`, one is auto-generated.
     * @param achievementTitle The title of the unlocked achievement.
     * @param achievementDesc  A short description of the achievement.
     * @param xpReward         The XP awarded for this achievement.
     */
    fun showAchievementNotification(
        id: Int? = null,
        achievementTitle: String,
        achievementDesc: String,
        xpReward: Int
    ) {
        if (!hasNotificationPermission()) {
            Timber.w("Cannot show achievement notification: permission not granted")
            return
        }

        val notificationId = id ?: generateId(CHANNEL_ACHIEVEMENTS)
        val route = "achievements"

        val notification = NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Achievement Unlocked! \uD83C\uDFC6")
            .setContentText("$achievementTitle — +$xpReward XP")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$achievementTitle\n$achievementDesc\n+$xpReward XP earned!")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_ACHIEVEMENTS)
            .setContentIntent(buildDeepLinkPendingIntent(route, REQUEST_CODE_ACHIEVEMENT))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Timber.d(
                "Achievement notification shown: id=%d, achievement=%s",
                notificationId, achievementTitle
            )
        } catch (e: SecurityException) {
            Timber.w(e, "SecurityException posting achievement notification")
        }
    }

    /**
     * Show a daily reminder notification (motivational quote, streak reminder).
     *
     * @param id             Unique notification ID. If `null`, one is auto-generated.
     * @param title          Notification title (e.g., "Stay Strong!").
     * @param message        Motivational message or streak info.
     * @param daysSmokeFree  Number of days the user has been smoke-free.
     */
    fun showDailyReminderNotification(
        id: Int? = null,
        title: String,
        message: String,
        daysSmokeFree: Int
    ) {
        if (!hasNotificationPermission()) {
            Timber.w("Cannot show daily reminder notification: permission not granted")
            return
        }

        val notificationId = id ?: generateId(CHANNEL_DAILY_REMINDER)
        val route = "home"

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n\n$daysSmokeFree day${if (daysSmokeFree != 1) "s" else ""} smoke-free! Keep going!")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildDeepLinkPendingIntent(route, REQUEST_CODE_DAILY))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Timber.d("Daily reminder notification shown: id=%d, days=%d", notificationId, daysSmokeFree)
        } catch (e: SecurityException) {
            Timber.w(e, "SecurityException posting daily reminder notification")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Notification Cancellation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Cancel a specific notification by its ID.
     */
    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
        Timber.d("Notification cancelled: id=%d", id)
    }

    /**
     * Cancel all notifications for a specific channel type.
     *
     * This cancels all active notifications that were posted from this
     * helper by iterating through the ID range for the given channel.
     * Note: This is a best-effort approach; IDs that were manually
     * specified outside the auto-generated range won't be cancelled.
     */
    fun cancelNotificationsForChannel(channelId: String) {
        val base = when (channelId) {
            CHANNEL_CHAT -> ID_BASE_CHAT
            CHANNEL_FRIEND_REQUESTS -> ID_BASE_FRIEND
            CHANNEL_EVENTS -> ID_BASE_EVENT
            CHANNEL_ACHIEVEMENTS -> ID_BASE_ACHIEVEMENT
            CHANNEL_DAILY_REMINDER -> ID_BASE_DAILY
            else -> return
        }
        // Cancel the last 50 IDs in the range (covers recent notifications)
        for (i in 0 until 50) {
            notificationManager.cancel(base + i)
        }
        Timber.d("Cancelled notifications for channel: %s", channelId)
    }

    /**
     * Cancel all Breathy notifications.
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
        Timber.d("All notifications cancelled")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FCM Token Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the current FCM registration token.
     *
     * @return The FCM token string, or `null` if retrieval fails.
     */
    suspend fun getFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get FCM token")
            null
        }
    }

    /**
     * Subscribe to an FCM topic for receiving push notifications.
     *
     * Topics are prefixed with "breathy_" to avoid namespace collisions.
     *
     * @param topic The topic name (without prefix). E.g., "events", "chat_{uid}".
     */
    suspend fun subscribeToTopic(topic: String) {
        val fullTopic = "$FCM_TOPIC_PREFIX$topic"
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(fullTopic).await()
            Timber.d("Subscribed to FCM topic: %s", fullTopic)
        } catch (e: Exception) {
            Timber.e(e, "Failed to subscribe to FCM topic: %s", fullTopic)
        }
    }

    /**
     * Unsubscribe from an FCM topic.
     *
     * @param topic The topic name (without prefix).
     */
    suspend fun unsubscribeFromTopic(topic: String) {
        val fullTopic = "$FCM_TOPIC_PREFIX$topic"
        try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(fullTopic).await()
            Timber.d("Unsubscribed from FCM topic: %s", fullTopic)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unsubscribe from FCM topic: %s", fullTopic)
        }
    }

    /**
     * Delete the current FCM token. Use this when the user signs out
     * to stop receiving push notifications for their account.
     */
    suspend fun deleteFcmToken() {
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
            Timber.d("FCM token deleted")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete FCM token")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Internal Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build a PendingIntent that routes to MainActivity with the given
     * route extra so the navigation framework can deep-link to the
     * correct screen.
     */
    private fun buildDeepLinkPendingIntent(route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", route)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Generate a unique notification ID for the given channel.
     *
     * Uses a base ID per channel type plus a monotonically increasing
     * counter that wraps around after 1000 values.
     */
    private fun generateId(channelId: String): Int {
        val base = when (channelId) {
            CHANNEL_CHAT -> ID_BASE_CHAT
            CHANNEL_FRIEND_REQUESTS -> ID_BASE_FRIEND
            CHANNEL_EVENTS -> ID_BASE_EVENT
            CHANNEL_ACHIEVEMENTS -> ID_BASE_ACHIEVEMENT
            CHANNEL_DAILY_REMINDER -> ID_BASE_DAILY
            else -> 9000
        }
        val counter = idCounters.getOrPut(channelId) { 0 }
        idCounters[channelId] = (counter + 1) % 1000
        return base + counter
    }
}

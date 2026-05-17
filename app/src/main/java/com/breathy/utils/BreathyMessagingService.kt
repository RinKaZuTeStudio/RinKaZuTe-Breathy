package com.breathy.utils

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

/**
 * Firebase Cloud Messaging service for Breathy.
 *
 * Handles incoming push notifications and FCM token updates.
 * Registered in AndroidManifest.xml as the messaging event handler.
 *
 * Responsibilities:
 * - Receive and route incoming push notifications
 * - Forward notification data to [NotificationHelper] for display
 * - Update the FCM token on the user's Firestore document
 */
class BreathyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM token refreshed: %s", token.take(10) + "...")

        // Update the token in Firestore so the server can send
        // targeted push notifications to this device.
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            try {
                val app = applicationContext as com.breathy.BreathyApplication
                kotlinx.coroutines.GlobalScope.launch(app.globalExceptionHandler) {
                    app.appModule.userRepository.updateFcmToken(userId, token)
                        .onFailure { e ->
                            Timber.e(e, "Failed to update FCM token on token refresh")
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update FCM token on token refresh")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("Push notification received from: %s", message.from)

        val data = message.data
        if (data.isEmpty()) {
            Timber.d("Push notification has no data payload — ignoring")
            return
        }

        try {
            val notificationHelper = NotificationHelper(applicationContext)
            val type = data["type"] ?: ""

            when (type) {
                "chat" -> {
                    val senderName = data["senderName"] ?: "Someone"
                    val messagePreview = data["message"] ?: ""
                    val chatId = data["chatId"] ?: ""
                    val senderId = data["senderId"] ?: ""
                    val senderPhotoUrl = data["senderPhotoUrl"]
                    notificationHelper.showChatNotification(
                        senderName = senderName,
                        messagePreview = messagePreview,
                        chatId = chatId,
                        senderId = senderId,
                        senderPhotoUrl = senderPhotoUrl
                    )
                }
                "friend_request" -> {
                    val senderName = data["senderName"] ?: "Someone"
                    val requestId = data["requestId"] ?: ""
                    val senderId = data["senderId"] ?: ""
                    notificationHelper.showFriendRequestNotification(
                        senderName = senderName,
                        requestId = requestId,
                        senderId = senderId
                    )
                }
                "friend_accepted" -> {
                    val senderName = data["senderName"] ?: "Someone"
                    notificationHelper.showFriendRequestNotification(
                        senderName = senderName,
                        requestId = "",
                        senderId = ""
                    )
                }
                "event" -> {
                    val title = data["title"] ?: "Event Update"
                    val body = data["body"] ?: ""
                    val eventId = data["eventId"] ?: ""
                    notificationHelper.showEventNotification(
                        title = title,
                        message = body,
                        eventId = eventId
                    )
                }
                "achievement" -> {
                    val achievementTitle = data["title"] ?: "Achievement Unlocked"
                    val achievementDesc = data["description"] ?: ""
                    val xpReward = data["xp"]?.toIntOrNull() ?: 0
                    notificationHelper.showAchievementNotification(
                        achievementTitle = achievementTitle,
                        achievementDesc = achievementDesc,
                        xpReward = xpReward
                    )
                }
                "daily_reminder" -> {
                    val title = data["title"] ?: "Stay Strong!"
                    val body = data["body"] ?: "Keep going, you're doing great!"
                    val daysFree = data["daysSmokeFree"]?.toIntOrNull() ?: 0
                    notificationHelper.showDailyReminderNotification(
                        title = title,
                        message = body,
                        daysSmokeFree = daysFree
                    )
                }
                else -> {
                    // Unknown notification type — show as generic event notification
                    val title = data["title"] ?: "Breathy"
                    val body = data["body"] ?: ""
                    if (body.isNotBlank()) {
                        notificationHelper.showEventNotification(
                            title = title,
                            message = body,
                            eventId = data["eventId"] ?: ""
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle push notification")
        }
    }
}

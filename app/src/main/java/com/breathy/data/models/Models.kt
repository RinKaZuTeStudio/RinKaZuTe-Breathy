package com.breathy.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.Transient
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════════════
//  Enums — Type-safe representations of Firestore string fields
// ═══════════════════════════════════════════════════════════════════════════════

/** How the user chose to quit smoking. Stored as a string in Firestore. */
@Serializable
enum class QuitType(val value: String) {
    @SerialName("instant") INSTANT("instant"),
    @SerialName("gradual") GRADUAL("gradual");

    override fun toString(): String = value

    companion object {
        /** Resolve a Firestore string value to the enum, defaults to [INSTANT]. */
        fun fromValue(value: String): QuitType =
            entries.find { it.value == value } ?: INSTANT
    }
}

/** Status of a friend request. Stored as a string in Firestore. */
@Serializable
enum class RequestStatus(val value: String) {
    @SerialName("pending") PENDING("pending"),
    @SerialName("accepted") ACCEPTED("accepted"),
    @SerialName("rejected") REJECTED("rejected");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): RequestStatus =
            entries.find { it.value == value } ?: PENDING
    }
}

/** Review status of an event check-in video. Stored as a string in Firestore. */
@Serializable
enum class CheckinStatus(val value: String) {
    @SerialName("pending") PENDING("pending"),
    @SerialName("approved") APPROVED("approved"),
    @SerialName("rejected") REJECTED("rejected");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): CheckinStatus =
            entries.find { it.value == value } ?: PENDING
    }
}

/** Coping method used during a craving episode. Stored as a string in Firestore. */
@Serializable
enum class CopingMethod(val value: String) {
    @SerialName("breathing") BREATHING("breathing"),
    @SerialName("game") GAME("game"),
    @SerialName("ai") AI("ai"),
    @SerialName("exercise") EXERCISE("exercise");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): CopingMethod =
            entries.find { it.value == value } ?: BREATHING
    }
}

/** Role of a coach chat message author. Stored as a string in Firestore. */
@Serializable
enum class MessageRole(val value: String) {
    @SerialName("user") USER("user"),
    @SerialName("assistant") ASSISTANT("assistant");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): MessageRole =
            entries.find { it.value == value } ?: USER
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  TimestampSerializer — kotlinx.serialization for Firebase Timestamp
// ═══════════════════════════════════════════════════════════════════════════════

object TimestampSerializer : KSerializer<Timestamp> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FirebaseTimestamp", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Timestamp) {
        encoder.encodeLong(value.seconds * 1000 + value.nanoseconds / 1_000_000)
    }

    override fun deserialize(decoder: Decoder): Timestamp {
        val millis = decoder.decodeLong()
        return Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  1. User — Private profile document at users/{userId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class User(
    @PropertyName("email")
    val email: String = "",
    @PropertyName("nickname")
    val nickname: String = "",
    @PropertyName("age")
    val age: Int? = null,
    @PropertyName("quitDate")
    @Serializable(with = TimestampSerializer::class)
        val quitDate: Timestamp? = null,
    @PropertyName("quitType")
    val quitType: QuitType = QuitType.INSTANT,
    @PropertyName("cigarettesPerDay")
    val cigarettesPerDay: Int = 0,
    @PropertyName("pricePerPack")
    val pricePerPack: Double = 0.0,
    @PropertyName("cigarettesPerPack")
    val cigarettesPerPack: Int = 20,
    @PropertyName("xp")
    val xp: Int = 0,
    @PropertyName("coins")
    val coins: Int = 0,
    @PropertyName("lastDailyClaim")
    @Serializable(with = TimestampSerializer::class)
        val lastDailyClaim: Timestamp? = null,
    @PropertyName("createdAt")
    @Serializable(with = TimestampSerializer::class)
        val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("achievements")
    val achievements: List<String> = emptyList(),
    @PropertyName("givenLikes")
    val givenLikes: List<String> = emptyList(),
    @PropertyName("fcmToken")
    val fcmToken: String? = null,
    @PropertyName("photoURL")
    val photoURL: String? = null,
    @PropertyName("location")
    val location: String? = null
){

    companion object {
        /**
         * Create a User from a raw Firestore document Map.
         * Provides safe defaults for every field and handles type coercion
         * that Firestore sometimes applies (e.g., Long -> Int, Double -> Int).
         */
        fun fromFirestoreMap(map: Map<String, Any?>): User = User(
            email = map["email"] as? String ?: "",
            nickname = map["nickname"] as? String ?: "",
            age = (map["age"] as? Long)?.toInt(),
            quitDate = map["quitDate"] as? Timestamp,
            quitType = QuitType.fromValue(map["quitType"] as? String ?: "instant"),
            cigarettesPerDay = (map["cigarettesPerDay"] as? Long)?.toInt() ?: 0,
            pricePerPack = (map["pricePerPack"] as? Number)?.toDouble() ?: 0.0,
            cigarettesPerPack = (map["cigarettesPerPack"] as? Long)?.toInt() ?: 20,
            xp = (map["xp"] as? Long)?.toInt() ?: 0,
            coins = (map["coins"] as? Long)?.toInt() ?: 0,
            lastDailyClaim = map["lastDailyClaim"] as? Timestamp,
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
            achievements = (map["achievements"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            givenLikes = (map["givenLikes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            fcmToken = map["fcmToken"] as? String,
            photoURL = map["photoURL"] as? String,
            location = map["location"] as? String
        )

        /** Compute level from XP using the PRD-specified threshold table. */
        fun computeLevel(xp: Int): Int {
            val thresholds = listOf(0, 100, 300, 600, 1000, 1500, 2200, 3000, 4000, 5500)
            for (i in thresholds.indices.reversed()) {
                if (xp >= thresholds[i]) return i + 1
            }
            return 1
        }
    }

    /** Level computed from XP — never stored, always derived. */
    val level: Int
        get() = computeLevel(xp)

    /** Days smoke-free from the quit date to now. Returns 0 if quitDate is not set. */
    val daysSmokeFree: Int
        get() {
            val quitMillis = quitDate?.toDate()?.time ?: return 0
            val nowMillis = System.currentTimeMillis()
            val diffMillis = nowMillis - quitMillis
            return if (diffMillis < 0) 0 else TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
        }

    /** Total money saved based on habit data and days smoke-free. */
    fun moneySaved(): Double {
        if (quitDate == null || cigarettesPerPack <= 0) return 0.0
        val costPerCigarette = pricePerPack / cigarettesPerPack
        return costPerCigarette * cigarettesPerDay * daysSmokeFree
    }

    /** Total cigarettes avoided since quit date. */
    fun cigarettesAvoided(): Int = cigarettesPerDay * daysSmokeFree

    /** XP needed to reach the next level. */
    fun xpToNextLevel(): Int {
        val thresholds = listOf(0, 100, 300, 600, 1000, 1500, 2200, 3000, 4000, 5500)
        val currentLevelIndex = (level - 1).coerceIn(0, thresholds.lastIndex)
        if (currentLevelIndex >= thresholds.lastIndex) {
            // Levels 11+ increment by 1500 XP each
            val baseThreshold = thresholds.last()
            val levelsBeyond = level - 10
            val nextThreshold = baseThreshold + (levelsBeyond * 1500)
            return (nextThreshold - xp).coerceAtLeast(0)
        }
        val nextThreshold = thresholds[currentLevelIndex + 1]
        return (nextThreshold - xp).coerceAtLeast(0)
    }

    /** Convert to a Firestore-friendly Map, using string values for enums. */
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "email" to email,
        "nickname" to nickname,
        "age" to age,
        "quitDate" to quitDate,
        "quitType" to quitType.value,
        "cigarettesPerDay" to cigarettesPerDay,
        "pricePerPack" to pricePerPack,
        "cigarettesPerPack" to cigarettesPerPack,
        "xp" to xp,
        "coins" to coins,
        "lastDailyClaim" to lastDailyClaim,
        "createdAt" to createdAt,
        "achievements" to achievements,
        "givenLikes" to givenLikes,
        "fcmToken" to fcmToken,
        "photoURL" to photoURL,
        "location" to location
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  2. PublicProfile — Denormalized public document at publicProfiles/{userId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class PublicProfile(
    val userId: String = "",
    @PropertyName("nickname")
    val nickname: String = "",
    @PropertyName("photoURL")
    val photoURL: String? = null,
    @PropertyName("daysSmokeFree")
    val daysSmokeFree: Int = 0,
    @PropertyName("xp")
    val xp: Int = 0,
    @PropertyName("location")
    val location: String? = null,
    @PropertyName("quitDate")
    @Serializable(with = TimestampSerializer::class)
        val quitDate: Timestamp? = null
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): PublicProfile = PublicProfile(
            userId = id,
            nickname = map["nickname"] as? String ?: "",
            photoURL = map["photoURL"] as? String,
            daysSmokeFree = (map["daysSmokeFree"] as? Long)?.toInt() ?: 0,
            xp = (map["xp"] as? Long)?.toInt() ?: 0,
            location = map["location"] as? String,
            quitDate = map["quitDate"] as? Timestamp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  3. Story — Community story at stories/{storyId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Story(
    /** Firestore document ID — not stored as a field in the document. */
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("nickname")
    val nickname: String = "",
    @PropertyName("photoURL")
    val photoURL: String? = null,
    @PropertyName("content")
    val content: String = "",
    @PropertyName("lifeChanges")
    val lifeChanges: List<String> = emptyList(),
    @PropertyName("daysSmokeFree")
    val daysSmokeFree: Int = 0,
    @PropertyName("likes")
    val likes: Int = 0,
    @PropertyName("likedBy")
    val likedBy: List<String> = emptyList(),
    @PropertyName("replyCount")
    val replyCount: Int = 0,
    @PropertyName("createdAt")
    @Serializable(with = TimestampSerializer::class)
        val createdAt: Timestamp = Timestamp.now()
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): Story = Story(
            id = id,
            userId = map["userId"] as? String ?: "",
            nickname = map["nickname"] as? String ?: "",
            photoURL = map["photoURL"] as? String,
            content = map["content"] as? String ?: "",
            lifeChanges = (map["lifeChanges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            daysSmokeFree = (map["daysSmokeFree"] as? Long)?.toInt() ?: 0,
            likes = (map["likes"] as? Long)?.toInt() ?: 0,
            likedBy = (map["likedBy"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            replyCount = (map["replyCount"] as? Long)?.toInt() ?: 0,
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
        )
    }

    /** Check if a given user has already liked this story. */
    fun isLikedBy(userId: String): Boolean = likedBy.contains(userId)

    /** Time-ago string for display purposes. */
    fun timeAgo(): String {
        val createdMillis = createdAt.toDate().time
        val diffMillis = System.currentTimeMillis() - createdMillis
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 10080 -> "${minutes / 1440}d ago"
            else -> "${minutes / 10080}w ago"
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  4. Reply — Subcollection at stories/{storyId}/replies/{replyId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Reply(
    /** Firestore document ID — not stored as a field in the document. */
    val id: String = "",
    @PropertyName("storyId")
    val storyId: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("nickname")
    val nickname: String = "",
    @PropertyName("photoURL")
    val photoURL: String? = null,
    @PropertyName("content")
    val content: String = "",
    @PropertyName("parentReplyId")
    val parentReplyId: String? = null,
    @PropertyName("createdAt")
    @Serializable(with = TimestampSerializer::class)
        val createdAt: Timestamp = Timestamp.now()
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): Reply = Reply(
            id = id,
            storyId = map["storyId"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            nickname = map["nickname"] as? String ?: "",
            photoURL = map["photoURL"] as? String,
            content = map["content"] as? String ?: "",
            parentReplyId = map["parentReplyId"] as? String,
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  5. FriendRequest — at friendRequests/{requestId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class FriendRequest(
    /** Firestore document ID — not stored as a field in the document. */
    val id: String = "",
    @PropertyName("fromUserId")
    val fromUserId: String = "",
    @PropertyName("toUserId")
    val toUserId: String = "",
    @PropertyName("status")
    val status: RequestStatus = RequestStatus.PENDING,
    @PropertyName("timestamp")
    @Serializable(with = TimestampSerializer::class)
        val timestamp: Timestamp = Timestamp.now()
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): FriendRequest = FriendRequest(
            id = id,
            fromUserId = map["fromUserId"] as? String ?: "",
            toUserId = map["toUserId"] as? String ?: "",
            status = RequestStatus.fromValue(map["status"] as? String ?: "pending"),
            timestamp = map["timestamp"] as? Timestamp ?: Timestamp.now()
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  6. Friendship — at friendships/{friendshipId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Friendship(
    /** Firestore document ID — not stored as a field in the document. */
    val id: String = "",
    @PropertyName("userIds")
    val userIds: List<String> = emptyList(),
    @PropertyName("createdAt")
    @Serializable(with = TimestampSerializer::class)
        val createdAt: Timestamp = Timestamp.now()
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): Friendship = Friendship(
            id = id,
            userIds = (map["userIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
        )
    }

    /** Given one user ID, return the other friend's ID. */
    fun otherUserId(currentUserId: String): String? =
        userIds.firstOrNull { it != currentUserId }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  7. Chat — at chats/{chatId} (deterministic ID: sorted UIDs joined by "_")
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Chat(
    /** Firestore document ID (deterministic: sorted UIDs joined by "_"). */
    val id: String = "",
    @PropertyName("participants")
    val participants: List<String> = emptyList(),
    @PropertyName("lastMessage")
    val lastMessage: String = "",
    @PropertyName("lastUpdated")
    @Serializable(with = TimestampSerializer::class)
        val lastUpdated: Timestamp = Timestamp.now(),
    @PropertyName("typing")
        @Contextual
        val typing: Map<String, @Contextual Timestamp> = emptyMap()
){
    companion object {
        /** Generate a deterministic chat ID from two user IDs. */
        fun chatId(uid1: String, uid2: String): String =
            listOf(uid1, uid2).sorted().joinToString("_")

        fun fromFirestoreMap(id: String, map: Map<String, Any?>): Chat {
            val typingMap = mutableMapOf<String, Timestamp>()
            (map["typing"] as? Map<*, *>)?.forEach { (key, value) ->
                if (key is String && value is Timestamp) {
                    typingMap[key] = value
                }
            }
            return Chat(
                id = id,
                participants = (map["participants"] as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList(),
                lastMessage = map["lastMessage"] as? String ?: "",
                lastUpdated = map["lastUpdated"] as? Timestamp ?: Timestamp.now(),
                typing = typingMap
            )
        }
    }

    /** Given one participant ID, return the other participant's ID. */
    fun otherParticipant(currentUserId: String): String? =
        participants.firstOrNull { it != currentUserId }

    /** Check if a participant is currently typing (their typing timestamp hasn't expired). */
    fun isTyping(userId: String): Boolean {
        val typingTimestamp = typing[userId] ?: return false
        return System.currentTimeMillis() < typingTimestamp.toDate().time
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  8. Message — Subcollection at chats/{chatId}/messages/{messageId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Message(
    /** Firestore document ID — not stored as a field in the document. */
    val id: String = "",
    @PropertyName("senderId")
    val senderId: String = "",
    @PropertyName("text")
    val text: String = "",
    @PropertyName("timestamp")
    @Serializable(with = TimestampSerializer::class)
        val timestamp: Timestamp = Timestamp.now(),
    @PropertyName("read")
    val read: Boolean = false
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): Message = Message(
            id = id,
            senderId = map["senderId"] as? String ?: "",
            text = map["text"] as? String ?: "",
            timestamp = map["timestamp"] as? Timestamp ?: Timestamp.now(),
            read = map["read"] as? Boolean ?: false
        )
    }

    /** Whether this message was sent by the given user. */
    fun isFromUser(userId: String): Boolean = senderId == userId
}

// ═══════════════════════════════════════════════════════════════════════════════
//  9. Event — at events/{eventId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Event(
    /** Firestore document ID (often a named ID like "pushup_challenge_2025"). */
    val id: String = "",
    @PropertyName("title")
    val title: String = "",
    @PropertyName("description")
    val description: String = "",
    @PropertyName("startDate")
    @Serializable(with = TimestampSerializer::class)
        val startDate: Timestamp = Timestamp.now(),
    @PropertyName("endDate")
    @Serializable(with = TimestampSerializer::class)
        val endDate: Timestamp = Timestamp.now(),
    @PropertyName("active")
    val active: Boolean = false,
    @PropertyName("prizes")
    val prizes: Map<String, String> = emptyMap(),
    @PropertyName("dailyRequired")
    val dailyRequired: Int = 0,
    @PropertyName("eventType")
    val eventType: String = "default",
    @PropertyName("targetPushups")
    val targetPushups: Int = 0
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): Event {
            val prizesMap = mutableMapOf<String, String>()
            (map["prizes"] as? Map<*, *>)?.forEach { (key, value) ->
                if (key is String && value is String) {
                    prizesMap[key] = value
                }
            }
            return Event(
                id = id,
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                startDate = map["startDate"] as? Timestamp ?: Timestamp.now(),
                endDate = map["endDate"] as? Timestamp ?: Timestamp.now(),
                active = map["active"] as? Boolean ?: false,
                prizes = prizesMap,
                dailyRequired = (map["dailyRequired"] as? Long)?.toInt() ?: 0,
                eventType = map["eventType"] as? String ?: "default",
                targetPushups = (map["targetPushups"] as? Long)?.toInt() ?: 0
            )
        }
    }

    /** Check if the event is currently active (within date range and flagged active). */
    fun isCurrentlyActive(): Boolean {
        if (!active) return false
        val now = System.currentTimeMillis()
        val start = startDate.toDate().time
        val end = endDate.toDate().time
        return now in start..end
    }

    /** Check if event is a pushup challenge. */
    fun isPushupChallenge(): Boolean = eventType == "pushup"

    /** Calculate total days in the event. */
    fun totalDays(): Int {
        val diff = endDate.toDate().time - startDate.toDate().time
        return TimeUnit.MILLISECONDS.toDays(diff).toInt().coerceAtLeast(1)
    }

    /** Format prize list for display. */
    fun formattedPrizes(): String = prizes.entries.joinToString("\n") { (rank, prize) ->
        "$rank: $prize"
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  10. EventParticipant — at eventParticipants/{userId}_{eventId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class EventParticipant(
    /** Deterministic ID: {userId}_{eventId}. */
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("eventId")
    val eventId: String = "",
    @PropertyName("currentStreak")
    val currentStreak: Int = 0,
    @PropertyName("totalApprovedDays")
    val totalApprovedDays: Int = 0,
    @PropertyName("totalPushups")
    val totalPushups: Int = 0,
    @PropertyName("completed")
    val completed: Boolean = false,
    @PropertyName("completionTimestamp")
    @Serializable(with = TimestampSerializer::class)
        val completionTimestamp: Timestamp? = null,
    @PropertyName("joinedAt")
    @Serializable(with = TimestampSerializer::class)
        val joinedAt: Timestamp = Timestamp.now(),
    @PropertyName("rank")
    val rank: Int = 0
){
    companion object {
        /** Generate deterministic participant ID. */
        fun participantId(userId: String, eventId: String): String = "${userId}_$eventId"

        fun fromFirestoreMap(id: String, map: Map<String, Any?>): EventParticipant =
            EventParticipant(
                id = id,
                userId = map["userId"] as? String ?: "",
                eventId = map["eventId"] as? String ?: "",
                currentStreak = (map["currentStreak"] as? Long)?.toInt() ?: 0,
                totalApprovedDays = (map["totalApprovedDays"] as? Long)?.toInt() ?: 0,
                totalPushups = (map["totalPushups"] as? Long)?.toInt() ?: 0,
                completed = map["completed"] as? Boolean ?: false,
                completionTimestamp = map["completionTimestamp"] as? Timestamp,
                joinedAt = map["joinedAt"] as? Timestamp ?: Timestamp.now(),
                rank = (map["rank"] as? Long)?.toInt() ?: 0
            )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  11. EventCheckin — at eventCheckins/{checkinId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class EventCheckin(
    /** Firestore document ID — auto-generated. */
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("eventId")
    val eventId: String = "",
    @PropertyName("dayNumber")
    val dayNumber: Int = 1,
    @PropertyName("videoURL")
    val videoURL: String = "",
    @PropertyName("status")
    val status: CheckinStatus = CheckinStatus.PENDING,
    @PropertyName("submittedAt")
    @Serializable(with = TimestampSerializer::class)
        val submittedAt: Timestamp = Timestamp.now(),
    @PropertyName("reviewedAt")
    @Serializable(with = TimestampSerializer::class)
        val reviewedAt: Timestamp? = null,
    @PropertyName("reviewComment")
    val reviewComment: String? = null
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): EventCheckin =
            EventCheckin(
                id = id,
                userId = map["userId"] as? String ?: "",
                eventId = map["eventId"] as? String ?: "",
                dayNumber = (map["dayNumber"] as? Long)?.toInt() ?: 1,
                videoURL = map["videoURL"] as? String ?: "",
                status = CheckinStatus.fromValue(map["status"] as? String ?: "pending"),
                submittedAt = map["submittedAt"] as? Timestamp ?: Timestamp.now(),
                reviewedAt = map["reviewedAt"] as? Timestamp,
                reviewComment = map["reviewComment"] as? String
            )
    }

    fun isPending(): Boolean = status == CheckinStatus.PENDING
    fun isApproved(): Boolean = status == CheckinStatus.APPROVED
    fun isRejected(): Boolean = status == CheckinStatus.REJECTED
}

// ═══════════════════════════════════════════════════════════════════════════════
//  12. CravingLog — at craving_logs/{logId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class CravingLog(
    /** Firestore document ID — auto-generated. */
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("timestamp")
    @Serializable(with = TimestampSerializer::class)
        val timestamp: Timestamp = Timestamp.now(),
    @PropertyName("copingMethod")
    val copingMethod: CopingMethod = CopingMethod.BREATHING,
    @PropertyName("success")
    val success: Boolean = false
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): CravingLog = CravingLog(
            id = id,
            userId = map["userId"] as? String ?: "",
            timestamp = map["timestamp"] as? Timestamp ?: Timestamp.now(),
            copingMethod = CopingMethod.fromValue(map["copingMethod"] as? String ?: "breathing"),
            success = map["success"] as? Boolean ?: false
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  13. Subscription — at subscriptions/{userId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Subscription(
    @PropertyName("active")
    val active: Boolean = false,
    @PropertyName("plan")
    val plan: String = "",
    @PropertyName("expiresAt")
    @Serializable(with = TimestampSerializer::class)
        val expiresAt: Timestamp = Timestamp.now(),
    @PropertyName("purchaseToken")
    val purchaseToken: String = ""
){
    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>): Subscription = Subscription(
            active = map["active"] as? Boolean ?: false,
            plan = map["plan"] as? String ?: "",
            expiresAt = map["expiresAt"] as? Timestamp ?: Timestamp.now(),
            purchaseToken = map["purchaseToken"] as? String ?: ""
        )
    }

    /** Check if the subscription is currently active and not expired. */
    fun isActive(): Boolean {
        if (!active) return false
        return expiresAt.toDate().time > System.currentTimeMillis()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  14. CoachMessage — Subcollection at users/{userId}/coach_chats/{chatId}
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class CoachMessage(
    /** Firestore document ID — auto-generated. */
    val id: String = "",
    @PropertyName("role")
    val role: MessageRole = MessageRole.USER,
    @PropertyName("content")
    val content: String = "",
    @PropertyName("timestamp")
    @Serializable(with = TimestampSerializer::class)
        val timestamp: Timestamp = Timestamp.now()
){
    companion object {
        fun fromFirestoreMap(id: String, map: Map<String, Any?>): CoachMessage = CoachMessage(
            id = id,
            role = MessageRole.fromValue(map["role"] as? String ?: "user"),
            content = map["content"] as? String ?: "",
            timestamp = map["timestamp"] as? Timestamp ?: Timestamp.now()
        )
    }

    fun isFromUser(): Boolean = role == MessageRole.USER
    fun isFromAssistant(): Boolean = role == MessageRole.ASSISTANT
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Achievement — Client-side model for achievement definitions and unlock state
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class Achievement(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    val xpReward: Int = 0,
    val unlocked: Boolean = false
) {
    companion object {
        /** Predefined achievements as specified in the PRD. */
        val ALL_DEFINITIONS = listOf(
            Achievement("first_day", "First Breath", "Complete Day 1 smoke-free", "\uD83C\uDF2C\uFE0F", 100),
            Achievement("one_week", "One Week Strong", "Reach 7 days smoke-free", "\uD83D\uDCAA", 200),
            Achievement("one_month", "One Month Free", "Reach 30 days smoke-free", "\uD83C\uDFC6", 500),
            Achievement("one_year", "One Year Clean", "Reach 365 days smoke-free", "\uD83C\uDF1F", 5000),
            Achievement("money_saver", "Money Saver", "Save \$100 total", "\uD83D\uDCB0", 150),
            Achievement("big_saver", "Big Saver", "Save \$1,000 total", "\uD83C\uDFE6", 500),
            Achievement("craving_crusher", "Craving Crusher", "Successfully resist 50 cravings", "\uD83D\uDD25", 300),
            Achievement("breathing_master", "Breathing Master", "Complete 100 breathing exercises", "\uD83E\uDDD8", 300),
            Achievement("community_star", "Community Star", "Receive 50 total likes on stories", "\u2B50", 200),
            Achievement("storyteller", "Storyteller", "Post 10 community stories", "\uD83D\uDCDD", 200),
            Achievement("social_butterfly", "Social Butterfly", "Add 10 friends", "\uD83E\uDD8B", 200),
            Achievement("event_champion", "Event Champion", "Complete an event challenge", "\uD83C\uDFC5", 400),
            Achievement("streak_keeper", "Streak Keeper", "Claim daily reward 30 days in a row", "\uD83D\uDCC5", 300),
            Achievement("level_5", "Level 5", "Reach Level 5", "\uD83D\uDE80", 250),
            Achievement("level_10", "Level 10", "Reach Level 10", "\uD83D\uDC51", 500),
            Achievement("ai_explorer", "AI Explorer", "Have 50 AI Coach conversations", "\uD83E\uDD16", 300)
        )

        /** Create an Achievement with unlock state from the user's unlocked list. */
        fun withUnlockState(unlockedIds: List<String>): List<Achievement> =
            ALL_DEFINITIONS.map { definition ->
                definition.copy(unlocked = unlockedIds.contains(definition.id))
            }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  HealthMilestone — Client-side constant data for health recovery timeline
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class HealthMilestone(
    val minutesAfterQuit: Long,
    val title: String,
    val description: String,
    val icon: String
) {
    /** Whether this milestone has been achieved given the minutes since quit. */
    fun isAchieved(minutesSinceQuit: Long): Boolean = minutesSinceQuit >= minutesAfterQuit

    /** Remaining minutes until this milestone is reached. */
    fun minutesRemaining(minutesSinceQuit: Long): Long =
        (minutesAfterQuit - minutesSinceQuit).coerceAtLeast(0)

    /** Human-readable time label for the milestone. */
    fun timeLabel(): String = when {
        minutesAfterQuit < 60 -> "${minutesAfterQuit} minutes"
        minutesAfterQuit < 1440 -> "${minutesAfterQuit / 60} hours"
        minutesAfterQuit < 10080 -> "${minutesAfterQuit / 1440} days"
        minutesAfterQuit < 43200 -> "${minutesAfterQuit / 10080} weeks"
        minutesAfterQuit < 525600 -> "${minutesAfterQuit / 43200} months"
        else -> "${minutesAfterQuit / 525600} years"
    }

    companion object {
        /** Scientifically-backed health milestones as specified in the PRD. */
        val ALL = listOf(
            HealthMilestone(20L, "Heart Rate Normalizes", "Your heart rate and blood pressure begin to drop to normal levels, reducing cardiovascular stress immediately.", "\u2764\uFE0F"),
            HealthMilestone(480L, "Carbon Monoxide Levels Drop", "Carbon monoxide levels in your blood decrease by half, allowing oxygen levels to return to normal. Your cells begin receiving the oxygen they need.", "\uD83E\uDE81"),
            HealthMilestone(1440L, "Heart Attack Risk Decreases", "Your risk of heart attack begins to decrease as the effects of carbon monoxide and nicotine on your cardiovascular system begin to reverse.", "\uD83D\uDEE1\uFE0F"),
            HealthMilestone(2880L, "Taste and Smell Improve", "Nerve endings begin to regrow, and your ability to taste and smell is noticeably enhanced. Food begins to taste richer and more complex.", "\uD83D\uDC45"),
            HealthMilestone(4320L, "Breathing Becomes Easier", "Bronchial tubes relax and lung capacity increases, making breathing feel noticeably easier and deeper.", "\uD83D\uDCA8"),
            HealthMilestone(20160L, "Circulation Improves", "Blood circulation throughout your body improves significantly, making walking and exercise easier and more enjoyable.", "\uD83E\uDE78"),
            HealthMilestone(43200L, "Lung Function Improves", "Lung cilia regrow and lung function increases by up to 30%, significantly improving respiratory health and reducing coughing.", "\uD83E\uDE81"),
            HealthMilestone(129600L, "Coughing Decreases", "Cilia in the lungs fully regrow, reducing the frequency and severity of coughing, sinus congestion, and shortness of breath.", "\uD83C\uDF2C\uFE0F"),
            HealthMilestone(525600L, "Heart Disease Risk Halved", "Your risk of coronary heart disease is now half that of a smoker's, a dramatic reduction in cardiovascular danger.", "\u2764\uFE0F\u200D\uD83E\uDE79"),
            HealthMilestone(2628000L, "Stroke Risk Equal to Non-Smoker", "Your risk of having a stroke is now reduced to that of a non-smoker, a major milestone in vascular health recovery.", "\uD83E\uDDE0"),
            HealthMilestone(5256000L, "Lung Cancer Risk Halved", "Your risk of lung cancer falls to about half that of a smoker, and your risk of other cancers also decreases.", "\uD83D\uDEE1\uFE0F"),
            HealthMilestone(7884000L, "Heart Disease Risk Equal to Non-Smoker", "Your risk of coronary heart disease is now equivalent to that of someone who has never smoked \u2014 full cardiovascular recovery.", "\uD83C\uDFC6")
        )

        /** Find the most recently achieved milestone for a given minutes-since-quit value. */
        fun latestAchieved(minutesSinceQuit: Long): HealthMilestone? =
            ALL.lastOrNull { it.isAchieved(minutesSinceQuit) }

        /** Find the next upcoming milestone that hasn't been reached yet. */
        fun nextUpcoming(minutesSinceQuit: Long): HealthMilestone? =
            ALL.firstOrNull { !it.isAchieved(minutesSinceQuit) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  QuitStats — Computed stats for the home screen dashboard
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class QuitStats(
    val daysSmokeFree: Int = 0,
    val cigarettesAvoided: Int = 0,
    val moneySaved: Double = 0.0,
    val lifeRegainedMinutes: Long = 0L
) {
    companion object {
        /**
         * Calculate quit stats from user data.
         * Life regained is based on the estimate that each cigarette avoided
         * adds approximately 11 minutes of life (per CDC/WHO research).
         */
        fun fromUser(user: User): QuitStats {
            val days = user.daysSmokeFree
            val avoided = user.cigarettesAvoided()
            val saved = user.moneySaved()
            // Each cigarette avoided = 11 minutes of life regained
            val lifeRegained = avoided.toLong() * 11L
            return QuitStats(
                daysSmokeFree = days,
                cigarettesAvoided = avoided,
                moneySaved = saved,
                lifeRegainedMinutes = lifeRegained
            )
        }
    }

    /** Format life regained as a human-readable string. */
    fun formattedLifeRegained(): String {
        val hours = lifeRegainedMinutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${lifeRegainedMinutes % 60}m"
            else -> "${lifeRegainedMinutes}m"
        }
    }

    /** Format money saved with currency symbol. */
    fun formattedMoneySaved(currencySymbol: String = "$"): String =
        "$currencySymbol${"%,.2f".format(moneySaved)}"
}

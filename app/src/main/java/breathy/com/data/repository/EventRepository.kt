package breathy.com.data.repository

import android.net.Uri
import breathy.com.data.models.CheckinStatus
import breathy.com.data.models.Event
import breathy.com.data.models.EventCheckin
import breathy.com.data.models.EventParticipant
import breathy.com.data.models.PublicProfile
import breathy.com.utils.CloudinaryUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Event repository — Firestore CRUD for challenge events, participants,
 * and video check-ins.
 *
 * Features:
 * - Active event listing and real-time updates
 * - Join event with deterministic participant ID
 * - Video check-in submission with Firebase Storage upload
 * - Admin check-in review (approve/reject) with participant stats update
 * - Event leaderboard based on approved days
 * - Uses [CheckinStatus] enum instead of string literals
 * - 30-second timeout on all network operations
 * - Proper listener cleanup via Flow's awaitClose
 */
class EventRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val cloudinaryUploader: CloudinaryUploader,
    private val functions: com.google.firebase.functions.FirebaseFunctions? = null
) {

    companion object {
        private const val TAG = "EventRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val EVENTS_COLLECTION = "events"
        private const val EVENT_PARTICIPANTS_COLLECTION = "eventParticipants"
        private const val EVENT_CHECKINS_COLLECTION = "eventCheckins"
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        private const val LEADERBOARD_LIMIT = 50L
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // ═══════════════════════════════════════════════════════════════════════════
    //  Get events
    // ═══════════════════════════════════════════════════════════════════════════

    /** Fetch all currently active events. */
    suspend fun getActiveEvents(): Result<List<Event>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = try {
                firestore.collection(EVENTS_COLLECTION)
                    .whereEqualTo("active", true)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server fetch failed for events — trying cache")
                firestore.collection(EVENTS_COLLECTION)
                    .whereEqualTo("active", true)
                    .get(Source.CACHE)
                    .await()
            }
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Event.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get active events timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get active events")
    }

    /** Fetch a single event by ID. */
    suspend fun getEvent(eventId: String): Result<Event> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val document = try {
                firestore.collection(EVENTS_COLLECTION).document(eventId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server fetch failed for event %s — trying cache", eventId)
                firestore.collection(EVENTS_COLLECTION).document(eventId)
                    .get(Source.CACHE)
                    .await()
            }
            if (!document.exists()) {
                throw NoSuchElementException("Event not found: $eventId")
            }
            Event.fromFirestoreMap(document.id, document.data ?: emptyMap())
        } ?: throw IllegalStateException("Get event timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get event: %s", eventId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Join event
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Join an event. Uses a deterministic participant ID ({userId}_{eventId})
     * to prevent duplicate joins.
     */
    suspend fun joinEvent(eventId: String): Result<EventParticipant> = runCatching {
        val uid = currentUserId
        val participantId = EventParticipant.participantId(uid, eventId)

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val existingDoc = try {
                firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                    .document(participantId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server fetch failed for participant %s — trying cache", participantId)
                firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                    .document(participantId)
                    .get(Source.CACHE)
                    .await()
            }

            if (existingDoc.exists()) {
                throw IllegalStateException("Already joined this event")
            }

            val participantData = mapOf(
                "userId" to uid,
                "eventId" to eventId,
                "currentStreak" to 0,
                "totalApprovedDays" to 0,
                "completed" to false,
                "joinedAt" to FieldValue.serverTimestamp(),
                "rank" to 0
            )

            firestore.collection(EVENT_PARTICIPANTS_COLLECTION).document(participantId)
                .set(participantData)
                .await()
                Unit

            EventParticipant(
                id = participantId,
                userId = uid,
                eventId = eventId,
                joinedAt = com.google.firebase.Timestamp.now()
            )
        } ?: throw IllegalStateException("Join event timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to join event: %s", eventId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Participant
    // ═══════════════════════════════════════════════════════════════════════════

    /** Get a participant record, or null if the user hasn't joined the event. */
    suspend fun getParticipant(
        eventId: String,
        userId: String
    ): Result<EventParticipant?> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val participantId = EventParticipant.participantId(userId, eventId)
            val document = try {
                firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                    .document(participantId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server fetch failed for participant %s — trying cache", participantId)
                firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                    .document(participantId)
                    .get(Source.CACHE)
                    .await()
            }
            if (!document.exists()) null
            else EventParticipant.fromFirestoreMap(document.id, document.data ?: emptyMap())
        } ?: throw IllegalStateException("Get participant timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get participant for event: %s", eventId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Check-ins
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Submit a video check-in for an event.
     * Uploads the video to Cloudinary and creates a check-in document.
     */
    suspend fun submitCheckin(
        eventId: String,
        dayNumber: Int,
        videoUri: Uri
    ): Result<EventCheckin> = runCatching {
        val uid = currentUserId

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Upload video to Cloudinary
            val uploadResult = cloudinaryUploader.uploadEventVideo(
                videoUri = videoUri,
                userId = uid,
                eventId = eventId,
                dayNumber = dayNumber
            ) ?: throw IllegalStateException("Failed to upload check-in video to Cloudinary")

            val downloadUrl = uploadResult.secureUrl

            val checkinData = mapOf(
                "userId" to uid,
                "eventId" to eventId,
                "dayNumber" to dayNumber,
                "videoURL" to downloadUrl,
                "status" to CheckinStatus.PENDING.value,
                "submittedAt" to FieldValue.serverTimestamp()
            )

            val docRef = firestore.collection(EVENT_CHECKINS_COLLECTION)
                .add(checkinData)
                .await()
                Unit

            EventCheckin(
                id = docRef.id,
                userId = uid,
                eventId = eventId,
                dayNumber = dayNumber,
                videoURL = downloadUrl,
                status = CheckinStatus.PENDING,
                submittedAt = com.google.firebase.Timestamp.now()
            )
        } ?: throw IllegalStateException("Submit check-in timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to submit check-in for event: %s", eventId)
    }

    /** Get all check-ins for a user in a specific event. */
    suspend fun getCheckins(
        eventId: String,
        userId: String
    ): Result<List<EventCheckin>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = firestore.collection(EVENT_CHECKINS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("eventId", eventId)
                .orderBy("dayNumber", Query.Direction.ASCENDING)
                .get(Source.SERVER)
                .await()
                Unit
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { EventCheckin.fromFirestoreMap(doc.id, it) }
            }
        } ?: throw IllegalStateException("Get check-ins timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get check-ins for event: %s", eventId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Check-in review (admin)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Review a check-in: approve or reject it.
     * When approved, updates the participant's totalApprovedDays and currentStreak
     * using a Firestore transaction for consistency.
     */
    suspend fun updateCheckinStatus(
        checkinId: String,
        approved: Boolean,
        comment: String? = null
    ): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val newStatus = if (approved) CheckinStatus.APPROVED else CheckinStatus.REJECTED
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.value,
                "reviewedAt" to FieldValue.serverTimestamp()
            )
            if (comment != null) {
                updates["reviewComment"] = comment
            }

            // Update the check-in document
            firestore.collection(EVENT_CHECKINS_COLLECTION).document(checkinId)
                .update(updates)
                .await()
                Unit

            // If approved, update the participant's stats in a transaction
            if (approved) {
                val checkinDoc = firestore.collection(EVENT_CHECKINS_COLLECTION)
                    .document(checkinId)
                    .get(Source.SERVER)
                    .await()
                    Unit
                val checkinData = checkinDoc.data ?: return@withTimeoutOrNull
                val userId = checkinData["userId"] as? String ?: return@withTimeoutOrNull
                val eventId = checkinData["eventId"] as? String ?: return@withTimeoutOrNull
                val participantId = EventParticipant.participantId(userId, eventId)
                val participantRef = firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                    .document(participantId)

                firestore.runTransaction { transaction ->
                    val participantSnapshot = transaction.get(participantRef)
                    if (!participantSnapshot.exists()) return@runTransaction

                    val currentApproved = (participantSnapshot.getLong("totalApprovedDays") ?: 0L).toInt()
                    val currentStreak = (participantSnapshot.getLong("currentStreak") ?: 0L).toInt()

                    transaction.update(participantRef, mapOf(
                        "totalApprovedDays" to (currentApproved + 1),
                        "currentStreak" to (currentStreak + 1)
                    ))
                }.await()
                Unit
            }
        } ?: throw IllegalStateException("Review check-in timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to review check-in: %s", checkinId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Event leaderboard
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the leaderboard for an event.
     * Returns participants sorted by totalApprovedDays descending,
     * enriched with their public profiles.
     */
    suspend fun getEventLeaderboard(
        eventId: String,
        limit: Int = LEADERBOARD_LIMIT.toInt()
    ): Result<List<EventLeaderboardEntry>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .orderBy("totalApprovedDays", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get(Source.SERVER)
                .await()
                Unit

            val participants = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { EventParticipant.fromFirestoreMap(doc.id, it) }
            }

            // Fetch public profiles concurrently
            val entries = coroutineScope {
                participants.mapIndexed { index, participant ->
                    async {
                        try {
                            val profileDoc = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                                .document(participant.userId)
                                .get()
                                .await()
                                Unit
                            val profile = if (profileDoc.exists()) {
                                PublicProfile.fromFirestoreMap(participant.userId, profileDoc.data ?: emptyMap())
                            } else null

                            EventLeaderboardEntry(
                                participant = participant,
                                publicProfile = profile,
                                rank = index + 1
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to fetch profile for leaderboard: %s", participant.userId)
                            EventLeaderboardEntry(
                                participant = participant,
                                publicProfile = null,
                                rank = index + 1
                            )
                        }
                    }
                }.awaitAll()
            }
            entries
        } ?: throw IllegalStateException("Get event leaderboard timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get event leaderboard: %s", eventId)
    }

    /** Leaderboard entry combining participant data with public profile. */
    data class EventLeaderboardEntry(
        val participant: EventParticipant,
        val publicProfile: PublicProfile?,
        val rank: Int
    )

    // ═══════════════════════════════════════════════════════════════════════════
    //  Pushup Challenge
    // ═══════════════════════════════════════════════════════════════════════════

    /** Submit pushup count via Cloud Function. */
    suspend fun submitPushupCount(
        eventId: String,
        pushupCount: Int,
        sessionDurationSeconds: Int
    ): Result<Int> = runCatching {
        val uid = currentUserId

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Try Cloud Function first
            val result = try {
                functions
                    ?.getHttpsCallable("submitPushupCount")
                    ?.call(mapOf(
                        "eventId" to eventId,
                        "pushupCount" to pushupCount,
                        "sessionDurationSeconds" to sessionDurationSeconds
                    ))
                    ?.await()
            } catch (e: Exception) {
                Timber.w(e, "Cloud Function submitPushupCount failed, using local fallback")
                null
            }

            // If cloud function succeeded, return the count
            if (result != null) {
                return@withTimeoutOrNull pushupCount
            }

            // Fallback: write directly to Firestore
            val participantId = EventParticipant.participantId(uid, eventId)
            val participantRef = firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                .document(participantId)

            firestore.runTransaction { transaction ->
                val participantDoc = transaction.get(participantRef)
                if (!participantDoc.exists()) {
                    throw IllegalStateException("You must join the event first.")
                }

                val currentData = participantDoc.data ?: emptyMap<String, Any>()
                val currentTotalPushups = (currentData["totalPushups"] as? Long)?.toInt() ?: 0
                val currentTotalApprovedDays = (currentData["totalApprovedDays"] as? Long)?.toInt() ?: 0
                val currentStreak = (currentData["currentStreak"] as? Long)?.toInt() ?: 0

                transaction.update(participantRef, mapOf(
                    "totalPushups" to (currentTotalPushups + pushupCount),
                    "totalApprovedDays" to (currentTotalApprovedDays + 1),
                    "currentStreak" to (currentStreak + 1),
                    "lastCheckinAt" to FieldValue.serverTimestamp()
                ))
            }.await()
            Unit

            // Also create checkin doc
            val checkinData = mapOf(
                "userId" to uid,
                "eventId" to eventId,
                "pushupCount" to pushupCount,
                "sessionDurationSeconds" to sessionDurationSeconds,
                "status" to "approved",
                "submittedAt" to FieldValue.serverTimestamp(),
                "type" to "pushup"
            )
            firestore.collection(EVENT_CHECKINS_COLLECTION).add(checkinData).await()
            Unit

            pushupCount
        } ?: throw IllegalStateException("Submit pushup count timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to submit pushup count")
    }

    /** Create the pushup challenge event. Tries Cloud Function first, falls back to direct Firestore write. */
    suspend fun createPushupChallengeEvent(): Result<String> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            // Check if event already exists
            val existingEvent = firestore.collection(EVENTS_COLLECTION)
                .whereEqualTo("type", "pushup_challenge")
                .limit(1)
                .get()
                .await()

            if (!existingEvent.isEmpty) {
                val existingDoc = existingEvent.documents.first()
                return@withTimeoutOrNull existingDoc.id
            }

            // Try Cloud Function first
            val cloudResult = try {
                functions
                    ?.getHttpsCallable("createPushupChallenge")
                    ?.call(emptyMap<String, Any>())
                    ?.await()
            } catch (e: Exception) {
                Timber.w(e, "Cloud Function createPushupChallenge failed, using local fallback")
                null
            }

            if (cloudResult != null) {
                val data = cloudResult.getData() as? Map<*, *> ?: emptyMap<Any, Any?>()
                return@withTimeoutOrNull data["eventId"] as? String ?: "pushup_challenge_2025"
            }

            // Fallback: create the event directly in Firestore
            val eventId = "pushup_challenge_may2026"
            val startDate = com.google.firebase.Timestamp(
                java.util.Calendar.getInstance().apply {
                    set(2026, java.util.Calendar.MAY, 20, 0, 0, 0)
                }.timeInMillis / 1000, 0
            )
            val endDate = com.google.firebase.Timestamp(
                java.util.Calendar.getInstance().apply {
                    set(2026, java.util.Calendar.AUGUST, 20, 23, 59, 59)
                }.timeInMillis / 1000, 0
            )

            val eventData = mapOf(
                "title" to "100 Pushup Challenge",
                "description" to "Join our 3-month pushup challenge! Use AI pose detection to count your pushups, climb the leaderboard, and win cash prizes. Rank 1-5 win \$20, Rank 6-10 win \$10!",
                "type" to "pushup_challenge",
                "eventType" to "pushup_challenge",
                "active" to true,
                "startDate" to startDate,
                "endDate" to endDate,
                "dailyRequired" to 10,
                "targetPushups" to 100,
                "prizes" to mapOf(
                    "1st" to "\$20",
                    "2nd" to "\$20",
                    "3rd" to "\$20",
                    "4th" to "\$20",
                    "5th" to "\$20",
                    "6th" to "\$10",
                    "7th" to "\$10",
                    "8th" to "\$10",
                    "9th" to "\$10",
                    "10th" to "\$10"
                ),
                "createdAt" to FieldValue.serverTimestamp()
            )

            firestore.collection(EVENTS_COLLECTION).document(eventId)
                .set(eventData, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Unit

            eventId
        } ?: throw IllegalStateException("Create pushup challenge timed out")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to create pushup challenge event")
    }

    /** Get pushup-specific leaderboard sorted by totalPushups descending. */
    suspend fun getPushupLeaderboard(
        eventId: String,
        limit: Int = LEADERBOARD_LIMIT.toInt()
    ): Result<List<EventLeaderboardEntry>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val snapshot = firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .orderBy("totalPushups", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get(Source.SERVER)
                .await()
                Unit

            val participants = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { EventParticipant.fromFirestoreMap(doc.id, it) }
            }

            // Fetch public profiles concurrently
            val entries = coroutineScope {
                participants.mapIndexed { index, participant ->
                    async {
                        try {
                            val profileDoc = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                                .document(participant.userId)
                                .get()
                                .await()
                                Unit
                            val profile = if (profileDoc.exists()) {
                                PublicProfile.fromFirestoreMap(participant.userId, profileDoc.data ?: emptyMap())
                            } else null

                            EventLeaderboardEntry(
                                participant = participant,
                                publicProfile = profile,
                                rank = index + 1
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to fetch profile for pushup leaderboard: %s", participant.userId)
                            EventLeaderboardEntry(
                                participant = participant,
                                publicProfile = null,
                                rank = index + 1
                            )
                        }
                    }
                }.awaitAll()
            }
            entries
        } ?: throw IllegalStateException("Get pushup leaderboard timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get pushup leaderboard: %s", eventId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time observers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Observe active events in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveEvents(): Flow<List<Event>> = callbackFlow {
        val registration = firestore.collection(EVENTS_COLLECTION)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeActiveEvents error")
                    // Don't close the flow on transient errors — emit empty list
                    // as fallback so the UI can still render.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val events = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { Event.fromFirestoreMap(doc.id, it) }
                    }
                    trySend(events)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Observe a single event in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeEvent(eventId: String): Flow<Event> = callbackFlow {
        val registration = firestore.collection(EVENTS_COLLECTION).document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeEvent error for %s", eventId)
                    // Don't close the flow on transient errors — the listener stays
                    // active and will retry on the next Firestore sync.
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val event = Event.fromFirestoreMap(snapshot.id, snapshot.data ?: emptyMap())
                    trySend(event)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Observe participants for an event in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeEventParticipants(eventId: String): Flow<List<EventParticipant>> =
        callbackFlow {
            val registration = firestore.collection(EVENT_PARTICIPANTS_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .orderBy("totalApprovedDays", Query.Direction.DESCENDING)
                .limit(LEADERBOARD_LIMIT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "observeEventParticipants error for %s", eventId)
                        // Don't close the flow on transient errors — emit empty list
                        // as fallback so the UI can still render.
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val participants = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { EventParticipant.fromFirestoreMap(doc.id, it) }
                        }
                        trySend(participants)
                    }
                }
            awaitClose { registration.remove() }
        }
}

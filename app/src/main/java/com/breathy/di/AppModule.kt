package com.breathy.di

import android.content.Context
import com.breathy.data.repository.AuthRepository
import com.breathy.data.repository.ChatRepository
import com.breathy.data.repository.CoachRepository
import com.breathy.data.repository.EventRepository
import com.breathy.data.repository.FriendRepository
import com.breathy.data.repository.RewardRepository
import com.breathy.data.repository.StoryRepository
import com.breathy.data.repository.UserRepository
import com.breathy.utils.AdManager
import com.breathy.utils.NotificationHelper
import com.breathy.utils.CloudinaryUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import timber.log.Timber

/**
 * Manual dependency injection container for the Breathy application.
 *
 * Replaces Hilt/Dagger with simple lazy singletons backed by Kotlin `lazy`
 * delegates. All Firebase services and repositories are created once and
 * reused for the lifetime of the application process.
 *
 * Usage:
 * ```
 * val appModule = (application as BreathyApplication).appModule
 * val authRepo = appModule.authRepository
 * ```
 *
 * Thread safety: Kotlin `lazy` with the default [LazyThreadSafetyMode.SYNCHRONIZED]
 * ensures that concurrent access from multiple threads is safe — the first thread
 * to access a property computes the value, and all other threads see the same result.
 *
 * @param applicationContext The application context — used for constructing
 *                           utilities that require a Context (AdManager, NotificationHelper).
 */
class AppModule(
    private val applicationContext: Context
) {

    // ═══════════════════════════════════════════════════════════════════════════
    // Firebase Services — lazily initialized singletons
    // ═══════════════════════════════════════════════════════════════════════════

    /** Firebase Authentication instance. */
    val firebaseAuth: FirebaseAuth by lazy {
        Timber.d("Initializing FirebaseAuth")
        Firebase.auth
    }

    /** Cloud Firestore instance — uses the named database for this Firebase project. */
    val firestore: FirebaseFirestore by lazy {
        Timber.d("Initializing FirebaseFirestore with named database")
        FirebaseFirestore.getInstance(
            com.google.firebase.FirebaseApp.getInstance(),
            "ai-studio-breathy-34bd5ba5-3577-4eac-963b-2ac3634ce3d7"
        )
    }

    /** Cloudinary uploader instance — replaces Firebase Storage for file uploads. */
    val cloudinaryUploader: CloudinaryUploader by lazy {
        Timber.d("Initializing CloudinaryUploader")
        CloudinaryUploader(applicationContext)
    }

    /** Cloud Functions for Firebase instance. */
    val firebaseFunctions: FirebaseFunctions by lazy {
        Timber.d("Initializing FirebaseFunctions")
        Firebase.functions
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Repositories — lazily initialized singletons
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Authentication repository — handles sign-in, sign-up, Google auth,
     * password reset, sign-out, and account deletion.
     */
    val authRepository: AuthRepository by lazy {
        Timber.d("Initializing AuthRepository")
        AuthRepository(
            auth = firebaseAuth,
            firestore = firestore
        )
    }

    /**
     * User repository — manages user profiles, XP/coins, achievements,
     * craving logs, and health milestone calculations.
     */
    val userRepository: UserRepository by lazy {
        Timber.d("Initializing UserRepository")
        UserRepository(
            firestore = firestore,
            auth = firebaseAuth,
            cloudinaryUploader = cloudinaryUploader
        )
    }

    /**
     * Story repository — CRUD for community stories, likes, and replies.
     */
    val storyRepository: StoryRepository by lazy {
        Timber.d("Initializing StoryRepository")
        StoryRepository(
            firestore = firestore,
            auth = firebaseAuth
        )
    }

    /**
     * Friend repository — friend requests, friendships, and friend list
     * management.
     */
    val friendRepository: FriendRepository by lazy {
        Timber.d("Initializing FriendRepository")
        FriendRepository(
            firestore = firestore,
            auth = firebaseAuth
        )
    }

    /**
     * Chat repository — direct messaging, typing indicators, and unread
     * message tracking.
     */
    val chatRepository: ChatRepository by lazy {
        Timber.d("Initializing ChatRepository")
        ChatRepository(
            firestore = firestore,
            auth = firebaseAuth
        )
    }

    /**
     * Event repository — challenge events, participant tracking, video
     * check-ins, and admin review.
     */
    val eventRepository: EventRepository by lazy {
        Timber.d("Initializing EventRepository")
        EventRepository(
            firestore = firestore,
            auth = firebaseAuth,
            cloudinaryUploader = cloudinaryUploader
        )
    }

    /**
     * Reward repository — XP/level calculations, achievement definitions,
     * unlock checking, and currency awards.
     */
    val rewardRepository: RewardRepository by lazy {
        Timber.d("Initializing RewardRepository")
        RewardRepository(
            firestore = firestore,
            auth = firebaseAuth,
            userRepository = userRepository
        )
    }

    /**
     * AI Coach repository — conversational AI powered by Cloud Functions,
     * with local Firestore message persistence.
     */
    val coachRepository: CoachRepository by lazy {
        Timber.d("Initializing CoachRepository")
        CoachRepository(
            firestore = firestore,
            auth = firebaseAuth,
            functions = firebaseFunctions
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utility Singletons — lazily initialized
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * AdMob manager — handles app-open and interstitial ad loading/display.
     * Premium subscribers are automatically exempt.
     */
    val adManager: AdManager by lazy {
        Timber.d("Initializing AdManager")
        AdManager(applicationContext)
    }

    /**
     * Notification helper — creates notification channels and builds/posts
     * notifications with deep-link routing.
     */
    val notificationHelper: NotificationHelper by lazy {
        Timber.d("Initializing NotificationHelper")
        NotificationHelper(applicationContext)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory Methods — create fresh instances per call
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new [AuthRepository] instance.
     * Useful for testing or for scoped instances that should not share state
     * with the app-wide singleton.
     */
    fun createAuthRepository(): AuthRepository = AuthRepository(
        auth = firebaseAuth,
        firestore = firestore
    )

    /**
     * Create a new [UserRepository] instance.
     */
    fun createUserRepository(): UserRepository = UserRepository(
        firestore = firestore,
        auth = firebaseAuth,
        cloudinaryUploader = cloudinaryUploader
    )

    /**
     * Create a new [StoryRepository] instance.
     */
    fun createStoryRepository(): StoryRepository = StoryRepository(
        firestore = firestore,
        auth = firebaseAuth
    )

    /**
     * Create a new [FriendRepository] instance.
     */
    fun createFriendRepository(): FriendRepository = FriendRepository(
        firestore = firestore,
        auth = firebaseAuth
    )

    /**
     * Create a new [ChatRepository] instance.
     */
    fun createChatRepository(): ChatRepository = ChatRepository(
        firestore = firestore,
        auth = firebaseAuth
    )

    /**
     * Create a new [EventRepository] instance.
     */
    fun createEventRepository(): EventRepository = EventRepository(
        firestore = firestore,
        auth = firebaseAuth,
        cloudinaryUploader = cloudinaryUploader
    )

    /**
     * Create a new [RewardRepository] instance.
     */
    fun createRewardRepository(): RewardRepository = RewardRepository(
        firestore = firestore,
        auth = firebaseAuth,
        userRepository = userRepository
    )

    /**
     * Create a new [CoachRepository] instance.
     */
    fun createCoachRepository(): CoachRepository = CoachRepository(
        firestore = firestore,
        auth = firebaseAuth,
        functions = firebaseFunctions
    )
}

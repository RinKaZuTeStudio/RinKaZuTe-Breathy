package breathy.com.di

import android.content.Context
import breathy.com.data.repository.AuthRepository
import breathy.com.data.repository.ChatRepository
import breathy.com.data.repository.GoldRepository
import breathy.com.data.repository.EventRepository
import breathy.com.data.repository.FollowRepository
import breathy.com.data.repository.FriendRepository
import breathy.com.data.repository.PremiumRepository
import breathy.com.data.repository.RewardRepository
import breathy.com.data.repository.SafetyRepository
import breathy.com.data.repository.StoryRepository
import breathy.com.data.repository.UserRepository
import breathy.com.utils.AdManager
import breathy.com.utils.NotificationHelper
import breathy.com.utils.CloudinaryUploader
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
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
        FirebaseAuth.getInstance()
    }

    /** Cloud Firestore instance — uses the named database for this Firebase project. */
    @Suppress("DEPRECATION")
    val firestore: FirebaseFirestore by lazy {
        Timber.d("Initializing FirebaseFirestore with named database")
        try {
            FirebaseFirestore.getInstance(
                FirebaseApp.getInstance(),
                "ai-studio-breathy-34bd5ba5-3577-4eac-963b-2ac3634ce3d7"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Firestore with named database, falling back to default")
            FirebaseFirestore.getInstance()
        }
    }

    /** Cloudinary uploader instance — primary upload mechanism for file uploads. */
    val cloudinaryUploader: CloudinaryUploader by lazy {
        Timber.d("Initializing CloudinaryUploader")
        CloudinaryUploader(applicationContext)
    }

    /**
     * Disk-persistent onboarding state — the offline-proof completion flag
     * and the pending-profile retry queue (v1.0.8 onboarding fix).
     */
    val onboardingLocalStore: breathy.com.utils.OnboardingLocalStore by lazy {
        breathy.com.utils.OnboardingLocalStore(applicationContext)
    }

    /** Firebase Storage instance — used as fallback for profile image uploads. */
    val firebaseStorage: FirebaseStorage by lazy {
        Timber.d("Initializing FirebaseStorage")
        try {
            FirebaseStorage.getInstance("gs://breathy-healthy.firebasestorage.app")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Firebase Storage with explicit bucket, falling back to default")
            FirebaseStorage.getInstance()
        }
    }

    /** Cloud Functions for Firebase instance. */
    val firebaseFunctions: FirebaseFunctions by lazy {
        Timber.d("Initializing FirebaseFunctions")
        FirebaseFunctions.getInstance()
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
            cloudinaryUploader = cloudinaryUploader,
            firebaseStorage = firebaseStorage
        )
    }

    /**
     * Story repository — CRUD for community stories, likes, and replies.
     */
    val storyRepository: StoryRepository by lazy {
        Timber.d("Initializing StoryRepository")
        StoryRepository(
            firestore = firestore,
            auth = firebaseAuth,
            firebaseStorage = firebaseStorage
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
     * Follow repository — one-way FOLLOWERS / FOLLOWING social graph,
     * independent of the friends system. Real accounts only.
     */
    val followRepository: FollowRepository by lazy {
        Timber.d("Initializing FollowRepository")
        FollowRepository(
            firestore = firestore,
            auth = firebaseAuth
        )
    }

    /**
     * Gold repository — in-app currency ledger (balance, earn, spend,
     * transaction history, frame purchases). Atomic + duplicate-protected.
     */
    val goldRepository: GoldRepository by lazy {
        Timber.d("Initializing GoldRepository")
        GoldRepository(
            firestore = firestore,
            auth = firebaseAuth
        )
    }

    /**
     * Safety repository — report user/post/message and block user
     * (UGC safety tooling for social features).
     */
    val safetyRepository: SafetyRepository by lazy {
        Timber.d("Initializing SafetyRepository")
        SafetyRepository(
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
            cloudinaryUploader = cloudinaryUploader,
            functions = firebaseFunctions
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


    // ═══════════════════════════════════════════════════════════════════════════
    // Utility Singletons — lazily initialized
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * LevelPlay ad manager — the ONLY ad system in the app (AdMob fully
     * removed). Handles rewarded ("Gold Ads" → +200 Gold), native, and
     * frequency-capped interstitial ads. Automatically exempts users with a
     * verified Premium subscription (zero ads loaded or shown).
     */
    val adManager: AdManager by lazy {
        Timber.d("Initializing AdManager (Unity LevelPlay)")
        AdManager(applicationContext).also { adManager ->
            // Keep ad behavior in lock-step with the verified premium entitlement:
            // verified premium → zero ads (not loaded, not shown).
            adManager.attachPremiumState(premiumRepository.state)

            // ── Gold Ads rewarded security path ─────────────────────────────
            // Invoked ONLY from the LevelPlay completion callback
            // (onAdRewarded) — never on ad open/click. The show token powers
            // the Gold-ledger dedup key, so retries/duplicate callbacks can
            // never double-credit a single completed ad.
            val goldScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() +
                    kotlinx.coroutines.Dispatchers.Main.immediate
            )
            adManager.rewardGrantCallback = { token ->
                goldScope.launch {
                    val result = goldRepository.earn(
                        amount = AdManager.REWARDED_GOLD_AMOUNT,
                        source = "gold_ads",
                        description = "Gold Ads — rewarded ad completed",
                        dedupKey = "goldads_$token"
                    )
                    result.onSuccess { (balance, credited) ->
                        if (credited) {
                            Timber.i(
                                "Gold Ads: +%d Gold granted (balance=%d)",
                                AdManager.REWARDED_GOLD_AMOUNT, balance
                            )
                        } else {
                            Timber.w("Gold Ads: duplicate completion ignored (dedup)")
                        }
                    }.onFailure { e ->
                        Timber.e(e, "Gold Ads: failed to credit reward")
                    }
                }
            }
        }
    }

    /**
     * Premium entitlement manager — REAL Google Play subscription
     * (breathy_premium_monthly / monthly-premium / launch-offer).
     * Single source of truth for ads, events, and UI.
     */
    val premiumRepository: PremiumRepository by lazy {
        Timber.d("Initializing PremiumRepository")
        PremiumRepository(
            context = applicationContext,
            auth = firebaseAuth,
            firestore = firestore
        )
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
        cloudinaryUploader = cloudinaryUploader,
        firebaseStorage = firebaseStorage
    )

    /**
     * Create a new [StoryRepository] instance.
     */
    fun createStoryRepository(): StoryRepository = StoryRepository(
        firestore = firestore,
        auth = firebaseAuth,
        firebaseStorage = firebaseStorage
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
        cloudinaryUploader = cloudinaryUploader,
        functions = firebaseFunctions
    )

    /**
     * Create a new [RewardRepository] instance.
     */
    fun createRewardRepository(): RewardRepository = RewardRepository(
        firestore = firestore,
        auth = firebaseAuth,
        userRepository = userRepository
    )

}

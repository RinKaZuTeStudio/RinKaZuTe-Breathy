package breathy.com

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import breathy.com.di.AppModule
import breathy.com.ui.navigation.BreathyNavHost
import breathy.com.ui.theme.BreathyTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import timber.log.Timber

/**
 * Single-activity host for the Breathy application.
 *
 * Responsibilities:
 * - Initializes the Unity LevelPlay ad SDK (the only ad system)
 * - Re-verifies the Google Play Premium entitlement on start + foreground
 * - Handles Google Sign-In via [GoogleSignInClient]
 * - Requests POST_NOTIFICATIONS permission on Android 13+
 * - Handles deep links from push notifications and URI-based intents
 * - Provides the Compose navigation host wrapped in [BreathyTheme]
 */
class MainActivity : ComponentActivity() {

    private var deepLinkRoute by mutableStateOf<String?>(null)

    /** Google Sign-In ID token, shared with Compose navigation via state. */
    private var googleIdToken by mutableStateOf<String?>(null)

    /**
     * v1.0.17 — Google Sign-In failure message, surfaced to the Auth screen.
     * Fixes the production "taps Google Sign-In, picks an account, nothing
     * happens" report: every failure path now becomes a visible error.
     */
    private var googleSignInError by mutableStateOf<String?>(null)

    /** Lazy reference to the app-scoped [AppModule] for manual DI. */
    private val appModule: AppModule by lazy {
        (application as BreathyApplication).appModule
    }

    /** Google Sign-In client configured with the default web client ID from Firebase. */
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, gso)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.d("Notification permission granted")
        } else {
            Timber.w("Notification permission denied")
        }
    }

    /** Launcher for Google Sign-In intent. */
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        handleGoogleSignInResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── LevelPlay ads (the ONLY ad system — AdMob fully removed) ────────
        appModule.adManager.initialize()

        // ── Premium entitlement re-check ─────────────────────────────────────
        // Connect to Google Play Billing and re-verify the subscription on
        // EVERY app start: active purchase → premium (ads off, premium events
        // unlocked); expired/cancelled → free behavior resumes. This is the
        // single startup point that keeps entitlement verified, not cached.
        try {
            appModule.premiumRepository.connectAndRefresh()
        } catch (e: Exception) {
            Timber.e(e, "Premium entitlement check failed at startup")
        }

        // Request notification permission on Android 13+
        requestNotificationPermissionIfNeeded()

        // Handle deep link from intent extras (e.g., from push notifications)
        handleDeepLinkFromIntent(intent)

        setContent {
            BreathyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BreathyNavHost(
                        deepLinkRoute = deepLinkRoute,
                        onDeepLinkConsumed = { deepLinkRoute = null },
                        onGoogleSignInRequest = { launchGoogleSignIn() },
                        googleIdToken = googleIdToken,
                        onGoogleTokenConsumed = { googleIdToken = null },
                        googleSignInError = googleSignInError,
                        onGoogleErrorConsumed = { googleSignInError = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // ── Premium entitlement re-check on EVERY foreground return ────────
        // The source of truth is the verified Google Play purchase state.
        // When the user comes back from subscribing (or cancelling) in Google
        // Play, the app must reflect the REAL entitlement immediately — never
        // a cached flag. This closes the "subscribed in Play but the app still
        // shows Subscribe" synchronization bug.
        try {
            appModule.premiumRepository.recheckEntitlement()
        } catch (e: Exception) {
            Timber.e(e, "Premium entitlement re-check on resume failed")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkFromIntent(intent)
    }

    // ── Google Sign-In ─────────────────────────────────────────────────────

    /** Launch the Google Sign-In intent. */
    private fun launchGoogleSignIn() {
        try {
            // Sign out first to show account picker every time
            googleSignInClient.signOut()
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Google Sign-In")
        }
    }

    /** Handle the result from Google Sign-In. */
    private fun handleGoogleSignInResult(result: ActivityResult) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                Timber.d("Google Sign-In successful, got idToken")
                googleIdToken = idToken
            } else {
                // v1.0.17 — null token means Google rejected the token request
                // for this build (typically package/sha1 not registered in
                // Firebase). Never silent: tell the user.
                Timber.e("Google Sign-In returned null idToken")
                googleSignInError =
                    "Google sign-in could not be completed (missing ID token). " +
                        "Please try again — if it persists, this app build's signing " +
                        "certificate is not registered in Firebase."
            }
        } catch (e: ApiException) {
            Timber.e(e, "Google Sign-In failed with status code: ${e.statusCode}")
            if (e.statusCode != GoogleSignInStatusCodes.CANCELED) {
                googleSignInError = when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR ->
                        "Sign-in configuration error: this app build's signing " +
                            "certificate is not registered in Firebase."
                    12500 ->
                        "Sign-in configuration error: the Google account could not " +
                            "be verified for this app build."
                    7 -> "Network error. Please check your connection and try again."
                    else -> "Google sign-in failed. Please try again."
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Google Sign-In failed unexpectedly")
            googleSignInError = "Google sign-in failed. Please try again."
        }
    }

    // ── Deep Link Handling ──────────────────────────────────────────────────

    /**
     * Extracts the navigation route from intent extras or data URI.
     *
     * Push notifications include a "route" extra that maps directly to a
     * navigation destination (e.g., "chat/uid123_uid456", "events").
     * Fallback chain: route → storyId → chatId → eventId → userId → URI path
     */
    private fun handleDeepLinkFromIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Direct route string from FCM data payload
        val routeExtra = intent.getStringExtra("route")
        if (!routeExtra.isNullOrBlank()) {
            deepLinkRoute = routeExtra
            Timber.d("Deep link route from extras: $routeExtra")
            return
        }

        // 2. Story ID (story like/reply notifications)
        val storyId = intent.getStringExtra("storyId")
        if (!storyId.isNullOrBlank()) {
            deepLinkRoute = "storyDetail/$storyId"
            Timber.d("Deep link route from storyId: storyDetail/$storyId")
            return
        }

        // 3. Chat ID (chat message notifications)
        val chatId = intent.getStringExtra("chatId")
        if (!chatId.isNullOrBlank()) {
            deepLinkRoute = "chat/$chatId"
            Timber.d("Deep link route from chatId: chat/$chatId")
            return
        }

        // 4. Event ID (event notifications)
        val eventId = intent.getStringExtra("eventId")
        if (!eventId.isNullOrBlank()) {
            deepLinkRoute = "eventChallenge/$eventId"
            Timber.d("Deep link route from eventId: eventChallenge/$eventId")
            return
        }

        // 5. User ID (friend request / profile notifications)
        val userId = intent.getStringExtra("userId")
        if (!userId.isNullOrBlank()) {
            deepLinkRoute = "publicProfile/$userId"
            Timber.d("Deep link route from userId: publicProfile/$userId")
            return
        }

        // 6. URI-based deep links (e.g., breathy://app/storyDetail/abc123)
        val uri = intent.data
        if (uri != null) {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                deepLinkRoute = path.trimStart('/')
                Timber.d("Deep link route from URI: $deepLinkRoute")
            }
        }
    }

    // ── Permission Handling ─────────────────────────────────────────────────

    /**
     * Requests the POST_NOTIFICATIONS permission on Android 13 (API 33)+.
     * Required for the app to show push notifications on those versions.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                Timber.e(e, "Failed to request notification permission")
            }
        }
    }
}

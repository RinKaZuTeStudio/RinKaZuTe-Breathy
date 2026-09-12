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
import breathy.com.utils.AdManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var deepLinkRoute by mutableStateOf<String?>(null)
    private var googleIdToken by mutableStateOf<String?>(null)
    private var googleSignInError by mutableStateOf<String?>(null)

    private val appModule: AppModule by lazy {
        (application as BreathyApplication).appModule
    }

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

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        handleGoogleSignInResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── AdMob ads (v1.0.30 — AdManager is the SINGLE owner) ──────────
        // ✅ FIX v1.0.30 — The AdManager now initializes the SDK once and
        // tracks its own ready state. We hook the AdEventListener here so
        // we can log every load/show/fail event to Logcat. This is the
        // only place that needs to call adManager.initialize().
        try {
            appModule.adManager.eventListener = object : AdManager.AdEventListener {
                override fun onAdLoaded(adType: AdManager.AdType) {
                    Timber.i("AdMob ✅ loaded: %s", adType)
                }
                override fun onAdLoadFailed(adType: AdManager.AdType, error: String) {
                    Timber.w("AdMob ❌ load failed: %s — %s", adType, error)
                }
                override fun onAdShown(adType: AdManager.AdType) {
                    Timber.i("AdMob ▶️ shown: %s", adType)
                }
                override fun onAdDismissed(adType: AdManager.AdType) {
                    Timber.i("AdMob ⏹️ dismissed: %s", adType)
                }
                override fun onAdShowFailed(adType: AdManager.AdType, error: String) {
                    Timber.w("AdMob ❌ show failed: %s — %s", adType, error)
                }
            }
            appModule.adManager.initialize()
            Timber.i("AdManager.initialize() called from MainActivity")
        } catch (e: Exception) {
            Timber.e(e, "AdManager initialization failed in MainActivity")
        }

        // ── Premium entitlement re-check ─────────────────────────────────────
        try {
            appModule.premiumRepository.connectAndRefresh()
        } catch (e: Exception) {
            Timber.e(e, "Premium entitlement check failed at startup")
        }

        requestNotificationPermissionIfNeeded()
        handleDeepLinkFromIntent(intent)

        setContent {
            BreathyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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

        try {
            appModule.adManager.maybeShowAppOpenAd(this)
        } catch (e: Exception) {
            Timber.e(e, "App Open ad show failed")
        }

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

    private fun launchGoogleSignIn() {
        try {
            googleSignInClient.signOut()
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Google Sign-In")
        }
    }

    private fun handleGoogleSignInResult(result: ActivityResult) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                Timber.d("Google Sign-In successful, got idToken")
                googleIdToken = idToken
            } else {
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

    private fun handleDeepLinkFromIntent(intent: Intent?) {
        if (intent == null) return

        val routeExtra = intent.getStringExtra("route")
        if (!routeExtra.isNullOrBlank()) {
            deepLinkRoute = routeExtra
            Timber.d("Deep link route from extras: $routeExtra")
            return
        }

        val storyId = intent.getStringExtra("storyId")
        if (!storyId.isNullOrBlank()) {
            deepLinkRoute = "storyDetail/$storyId"
            return
        }

        val chatId = intent.getStringExtra("chatId")
        if (!chatId.isNullOrBlank()) {
            deepLinkRoute = "chat/$chatId"
            return
        }

        val eventId = intent.getStringExtra("eventId")
        if (!eventId.isNullOrBlank()) {
            deepLinkRoute = "eventChallenge/$eventId"
            return
        }

        val userId = intent.getStringExtra("userId")
        if (!userId.isNullOrBlank()) {
            deepLinkRoute = "publicProfile/$userId"
            return
        }

        val uri = intent.data
        if (uri != null) {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                deepLinkRoute = path.trimStart('/')
            }
        }
    }

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

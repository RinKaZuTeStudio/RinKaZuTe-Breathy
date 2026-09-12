package breathy.com

import android.app.Application
import breathy.com.di.AppModule
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber

/**
 * Application class for Breathy.
 *
 * v1.0.30 FIX — REMOVED the direct MobileAds.initialize() call here.
 * AdManager is now the SINGLE owner of AdMob initialization (see
 * MainActivity.onCreate → appModule.adManager.initialize()). This
 * eliminates the double-initialization that was confusing the SDK state
 * and preventing ads from loading reliably on cold start.
 */
class BreathyApplication : Application() {

    val appModule: AppModule by lazy {
        AppModule(this)
    }

    val globalExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Uncaught coroutine exception")
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (_: Exception) { /* Crashlytics not available */ }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ── In-app language (English / العربية) ──────────────────────────────
        breathy.com.utils.AppLanguage.init(this)

        // ── Global Crash Safety Net ────────────────────────────────────────────
        installUncaughtExceptionHandler()

        // ── Firebase Initialization ──────────────────────────────────────────
        try {
            FirebaseApp.initializeApp(this)
            Timber.d("Firebase initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Firebase initialization failed")
        }

        // ── Firestore Configuration ──────────────────────────────────────────
        configureFirestore()

        // ── Crashlytics ─────────────────────────────────────────────────────
        configureCrashlytics()

        // ── Timber Logging ───────────────────────────────────────────────────
        plantTimberTrees()

        // ── Google Mobile Ads ────────────────────────────────────────────────
        // ✅ FIX v1.0.30 — REMOVED the direct MobileAds.initialize() call.
        // AdManager owns AdMob initialization now and tracks the ready state
        // internally so ads can actually load. See MainActivity.onCreate().

        // ── Notification Channels ────────────────────────────────────────────
        try {
            appModule.notificationHelper
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize notification helper")
        }
    }

    private fun configureFirestore() {
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FIRESTORE_CACHE_SIZE_BYTES)
                .build()

            FirebaseFirestore.getInstance(
                FirebaseApp.getInstance(),
                "ai-studio-breathy-34bd5ba5-3577-4eac-963b-2ac3634ce3d7"
            ).firestoreSettings = settings

            FirebaseFirestore.getInstance().firestoreSettings = settings

            Timber.d(
                "Firestore configured: persistence=true, named production DB + default, cacheSize=%dMB",
                FIRESTORE_CACHE_SIZE_BYTES / (1024 * 1024)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to configure Firestore settings")
        }
    }

    private fun configureCrashlytics() {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(
                !BuildConfig.DEBUG
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to configure Crashlytics")
        }
    }

    private fun plantTimberTrees() {
        if (BuildConfig.DEBUG) {
            Timber.plant(BreathyDebugTree())
        } else {
            Timber.plant(ReleaseCrashlyticsTree())
        }
    }

    private class BreathyDebugTree : Timber.DebugTree() {
        override fun createStackElementTag(element: StackTraceElement): String {
            return "Breathy: ${super.createStackElementTag(element)}.${element.methodName}:${element.lineNumber}"
        }
    }

    private class ReleaseCrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= android.util.Log.ERROR) {
                if (t != null) {
                    FirebaseCrashlytics.getInstance().recordException(t)
                } else {
                    FirebaseCrashlytics.getInstance().log("$tag: $message")
                }
            }
        }
    }

    companion object {
        lateinit var instance: BreathyApplication
            private set

        private const val FIRESTORE_CACHE_SIZE_BYTES = 100L * 1024L * 1024L
    }

    private fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Uncaught exception on thread: %s", thread.name)
            try {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (_: Exception) { }

            val message = throwable.message ?: ""
            val causeMessage = throwable.cause?.message ?: ""
            val fullMessage = "$message $causeMessage"
            val isRecoverableError =
                fullMessage.contains("PERMISSION_DENIED", ignoreCase = true) ||
                fullMessage.contains("Missing or insufficient permissions", ignoreCase = true) ||
                fullMessage.contains("UNAVAILABLE", ignoreCase = true) ||
                fullMessage.contains("DEADLINE_EXCEEDED", ignoreCase = true) ||
                fullMessage.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                fullMessage.contains("ABORTED", ignoreCase = true) ||
                fullMessage.contains("INTERNAL", ignoreCase = true) && fullMessage.contains("firestore", ignoreCase = true) ||
                fullMessage.contains("FirebaseFirestoreException", ignoreCase = true) ||
                fullMessage.contains("Could not reach Cloud Firestore backend", ignoreCase = true)

            val isCriticalError = throwable is OutOfMemoryError ||
                    throwable is StackOverflowError ||
                    throwable is ThreadDeath

            if (isCriticalError || !isRecoverableError) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}

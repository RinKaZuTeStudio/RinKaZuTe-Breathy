package com.breathy

import android.app.Application
import com.breathy.di.AppModule
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber

/**
 * Application class for Breathy.
 *
 * Responsibilities:
 * - Initializes Firebase ([FirebaseApp.initializeApp])
 * - Configures Firestore offline persistence and cache size
 * - Enables/disables Crashlytics based on build type
 * - Plants Timber logging trees (debug tree or Crashlytics-forwarding tree)
 * - Creates the manual dependency injection [AppModule]
 * - Installs a global uncaught-exception handler to prevent hard crashes
 */
class BreathyApplication : Application() {

    /**
     * App-scoped dependency container.
     * Lazily created on first access so that Firebase is fully initialized
     * before any Firebase service instances are obtained.
     */
    val appModule: AppModule by lazy {
        AppModule(this)
    }

    /**
     * Global coroutine exception handler that logs errors instead of crashing.
     * This prevents uncaught coroutine exceptions from killing the app process.
     */
    val globalExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Uncaught coroutine exception")
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (_: Exception) { /* Crashlytics not available */ }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ── Global Crash Safety Net ────────────────────────────────────────────
        // Catch any uncaught exception on the main thread so the app doesn't
        // hard-crash. This is a last resort — all Firestore / auth operations
        // should already have their own try-catch blocks.
        installUncaughtExceptionHandler()

        // ── Firebase Initialization ──────────────────────────────────────────
        try {
            FirebaseApp.initializeApp(this)
            Timber.d("Firebase initialized successfully")
        } catch (e: Exception) {
            // In rare cases (e.g., missing google-services.json in debug),
            // initialization may fail — log but don't crash.
            Timber.e(e, "Firebase initialization failed")
        }

        // ── Firestore Configuration ──────────────────────────────────────────
        configureFirestore()

        // ── Crashlytics ─────────────────────────────────────────────────────
        configureCrashlytics()

        // ── Timber Logging ───────────────────────────────────────────────────
        plantTimberTrees()

        // ── Notification Channels ────────────────────────────────────────────
        // Created eagerly so channels exist before any notification is posted.
        try {
            appModule.notificationHelper // triggers lazy init of AppModule → NotificationHelper
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize notification helper")
        }
    }

    /**
     * Configures Firestore with offline persistence enabled and a 100 MB cache.
     *
     * Persistence allows the app to read previously fetched data when offline,
     * which is critical for a quit-smoking tracker that users may consult in
     * areas with poor connectivity.
     */
    private fun configureFirestore() {
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FIRESTORE_CACHE_SIZE_BYTES)
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
            Timber.d("Firestore configured: persistence=true, cacheSize=%dMB",
                FIRESTORE_CACHE_SIZE_BYTES / (1024 * 1024))
        } catch (e: Exception) {
            Timber.e(e, "Failed to configure Firestore settings")
        }
    }

    /**
     * Enables Crashlytics collection only in release builds.
     * Debug builds avoid polluting the Crashlytics dashboard with stack traces
     * from development iterations.
     */
    private fun configureCrashlytics() {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(
                !BuildConfig.DEBUG
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to configure Crashlytics")
        }
    }

    /**
     * Plants the appropriate Timber tree based on the build type:
     * - **Debug**: [Timber.DebugTree] prints to Logcat with PrettyStackTree
     * - **Release**: [ReleaseCrashlyticsTree] forwards errors to Crashlytics
     */
    private fun plantTimberTrees() {
        if (BuildConfig.DEBUG) {
            Timber.plant(BreathyDebugTree())
        } else {
            Timber.plant(ReleaseCrashlyticsTree())
        }
    }

    // ── Custom Timber Trees ─────────────────────────────────────────────────

    /**
     * Debug tree that tags logs with the calling class name for easy
     * Logcat filtering.
     */
    private class BreathyDebugTree : Timber.DebugTree() {
        override fun createStackElementTag(element: StackTraceElement): String {
            // Format: "Breathy: ClassName.methodName:lineNumber"
            return "Breathy: ${super.createStackElementTag(element)}.${element.methodName}:${element.lineNumber}"
        }
    }

    /**
     * Release tree that forwards logged errors to Firebase Crashlytics.
     * Only errors and assertions are forwarded to avoid PII and noise.
     * Warnings and below are silently dropped in release builds.
     */
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
        /** Global reference to the application context for utility access. */
        lateinit var instance: BreathyApplication
            private set

        /** 100 MB cache size for Firestore offline persistence. */
        private const val FIRESTORE_CACHE_SIZE_BYTES = 100L * 1024L * 1024L
    }

    /**
     * Installs a global uncaught-exception handler that logs the error
     * instead of letting it crash the app. This is a safety net for any
     * exceptions that escape the per-feature try-catch blocks.
     *
     * For Firestore errors (PERMISSION_DENIED, UNAVAILABLE, etc.) and other
     * recoverable errors, the app survives instead of crashing. Critical
     * errors (OutOfMemoryError, StackOverflowError) still crash.
     */
    private fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Uncaught exception on thread: %s", thread.name)
            // Also report to Crashlytics if available
            try {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (_: Exception) { /* Crashlytics not available */ }

            // Check if this is a recoverable error — survive those
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
                // Also survive Firestore callback errors on main thread
                fullMessage.contains("Could not reach Cloud Firestore backend", ignoreCase = true)

            val isCriticalError = throwable is OutOfMemoryError ||
                    throwable is StackOverflowError ||
                    throwable is ThreadDeath

            if (isCriticalError || !isRecoverableError) {
                // Critical or unknown error — let the default handler crash the app
                defaultHandler?.uncaughtException(thread, throwable)
            }
            // For recoverable errors, just log — don't crash.
            // The app can still function with local/cached data.
        }
    }
}

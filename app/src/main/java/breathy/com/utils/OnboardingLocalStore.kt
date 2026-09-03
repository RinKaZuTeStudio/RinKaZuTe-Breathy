package breathy.com.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import breathy.com.data.models.AvatarFrame
import breathy.com.data.models.PublicProfile
import breathy.com.data.models.QuitType
import breathy.com.data.models.User
import org.json.JSONObject
import timber.log.Timber

/**
 * Local, disk-persistent store for onboarding completion state and pending
 * profile writes.
 *
 * WHY THIS EXISTS (v1.0.8 bug): onboarding saved the profile to Firestore
 * "best-effort" and navigated on. If the write failed (slow network, rules
 * propagation) the background retry lived in the ViewModel scope — closing
 * the app killed it, the profile stayed sparse in Firestore, and the next
 * launch routed the SAME account back into onboarding ("asks me to put the
 * create account information again"). Persistence now works in two layers:
 *
 *  1. [markCompleted] — a per-uid flag written the moment onboarding
 *     finishes. AuthViewModel treats it as the fast, offline-proof routing
 *     signal: flag present → straight to Home, never re-onboard.
 *  2. [savePendingProfile] — the FULL onboarding payload (user + public
 *     profile fields) persisted locally whenever the remote write fails.
 *     Every subsequent launch retries the upload in the background until it
 *     lands, then clears the pending copy.
 *
 * Data is stored as primitive JSON in SharedPreferences — no timestamps or
 * Firebase types, so it survives process death and restarts safely.
 */
class OnboardingLocalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("breathy_onboarding", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COMPLETED_PREFIX = "onboarding_done_"
        private const val KEY_PENDING_PREFIX = "pending_profile_"
    }

    // ── Completion flag ───────────────────────────────────────────────────

    fun markCompleted(uid: String) {
        prefs.edit().putBoolean(KEY_COMPLETED_PREFIX + uid, true).apply()
    }

    fun isCompleted(uid: String): Boolean =
        uid.isNotBlank() && prefs.getBoolean(KEY_COMPLETED_PREFIX + uid, false)

    // ── Pending profile (remote write retry queue) ────────────────────────

    /**
     * The full onboarding payload, kept locally until Firestore accepts it.
     * Only primitive types — JSON-safe by construction.
     */
    data class PendingProfile(
        val email: String,
        val nickname: String,
        val age: Int,
        val quitDateMillis: Long,
        val quitType: String,
        val cigarettesPerDay: Int,
        val pricePerPack: Double,
        val cigarettesPerPack: Int,
        val photoURL: String?
    )

    fun savePendingProfile(uid: String, profile: PendingProfile) {
        val json = JSONObject().apply {
            put("email", profile.email)
            put("nickname", profile.nickname)
            put("age", profile.age)
            put("quitDateMillis", profile.quitDateMillis)
            put("quitType", profile.quitType)
            put("cigarettesPerDay", profile.cigarettesPerDay)
            put("pricePerPack", profile.pricePerPack.toDouble())
            put("cigarettesPerPack", profile.cigarettesPerPack)
            put("photoURL", profile.photoURL ?: "")
        }
        prefs.edit().putString(KEY_PENDING_PREFIX + uid, json.toString()).apply()
        Timber.i("OnboardingLocalStore: pending profile saved for uid=%s", uid)
    }

    fun readPendingProfile(uid: String): PendingProfile? {
        val raw = prefs.getString(KEY_PENDING_PREFIX + uid, null) ?: return null
        return try {
            val json = JSONObject(raw)
            PendingProfile(
                email = json.optString("email"),
                nickname = json.optString("nickname"),
                age = json.optInt("age"),
                quitDateMillis = json.optLong("quitDateMillis"),
                quitType = json.optString("quitType", QuitType.INSTANT.value),
                cigarettesPerDay = json.optInt("cigarettesPerDay", 10),
                pricePerPack = json.optDouble("pricePerPack", 5.0),
                cigarettesPerPack = json.optInt("cigarettesPerPack", 20),
                photoURL = json.optString("photoURL").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            Timber.w(e, "OnboardingLocalStore: corrupt pending profile for uid=%s — dropped", uid)
            clearPendingProfile(uid)
            null
        }
    }

    fun clearPendingProfile(uid: String) {
        prefs.edit().remove(KEY_PENDING_PREFIX + uid).apply()
    }

    // ── Shared Firestore payload builders ─────────────────────────────────

    /**
     * Build the Firestore maps for the onboarding write from a pending
     * profile. Shared by OnboardingViewModel (initial write) and
     * AuthViewModel (later retries) so the payload can never drift.
     *
     * New accounts start with the CLASSIC frame (v1.0.8: Nature unlocks on
     * Day 7 — granting it at creation bypassed the progression rules).
     */
    fun buildFirestoreMaps(profile: PendingProfile): Pair<Map<String, Any?>, Map<String, Any?>> {
        val quitTimestamp = Timestamp(java.util.Date(profile.quitDateMillis))
        val user = User(
            email = profile.email,
            nickname = profile.nickname,
            age = profile.age,
            quitDate = quitTimestamp,
            quitType = QuitType.fromValue(profile.quitType),
            cigarettesPerDay = profile.cigarettesPerDay,
            pricePerPack = profile.pricePerPack,
            cigarettesPerPack = profile.cigarettesPerPack,
            photoURL = profile.photoURL,
            createdAt = quitTimestamp
        )
        val userMap = user.toFirestoreMap()
        val publicMap = mapOf<String, Any?>(
            "nickname" to profile.nickname,
            "photoURL" to profile.photoURL,
            "daysSmokeFree" to 0,
            "xp" to 0,
            "quitDate" to quitTimestamp,
            "avatarFrame" to AvatarFrame.NONE.id,
            "premium" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return userMap to publicMap
    }
}

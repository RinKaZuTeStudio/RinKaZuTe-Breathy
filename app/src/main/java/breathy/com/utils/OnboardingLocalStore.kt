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
 * v1.0.30 FIX — buildFirestoreMaps() no longer uses quitTimestamp as
 * createdAt. createdAt is now Timestamp.now() (the actual account creation
 * time is enforced by Firestore's existing value via merge, but if the doc
 * doesn't exist the field still represents "now", not the quit date).
 */
class OnboardingLocalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("breathy_onboarding", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COMPLETED_PREFIX = "onboarding_done_"
        private const val KEY_PENDING_PREFIX = "pending_profile_"
    }

    fun markCompleted(uid: String) {
        prefs.edit().putBoolean(KEY_COMPLETED_PREFIX + uid, true).apply()
    }

    fun isCompleted(uid: String): Boolean =
        uid.isNotBlank() && prefs.getBoolean(KEY_COMPLETED_PREFIX + uid, false)

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

    /**
     * ✅ FIX v1.0.30 — buildFirestoreMaps
     *
     * قبل الإصلاح: createdAt = quitTimestamp (خطأ دلالي — createdAt يجب أن
     * يمثل وقت إنشاء الحساب، وليس وقت الإقلاع). هذا كان يسبب مشاكل عند
     * محاولة الكتابة لحساب جديد (المستند غير موجود).
     *
     * بعد الإصلاح: createdAt = Timestamp.now() — وقت حقيقي لإنشاء الحساب.
     * ملاحظة: عند الكتابة إلى مستند موجود، الـ merge في AuthViewModel و
     * OnboardingViewModel يحذف createdAt من الـ map تلقائيًا، فالقيمة هنا
     * تُستخدم فقط عند إنشاء مستند جديد من الصفر.
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
            // ✅ FIX v1.0.30 — createdAt = now(), not quitDate
            createdAt = Timestamp.now()
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

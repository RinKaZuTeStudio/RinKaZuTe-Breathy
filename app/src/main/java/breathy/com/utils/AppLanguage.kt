package breathy.com.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * v1.0.9 — In-app ARABIC / ENGLISH language layer.
 *
 * The app's UI historically uses hardcoded English literals (strings.xml is
 * unreferenced), so system-locale switching alone would show nothing. This
 * helper adds a lightweight in-app switch (Profile → Settings → Language)
 * persisted in SharedPreferences, plus the [breathyString] accessor used by
 * the localized screens:
 *
 *     S.weekly   // "Weekly" / "أسبوعي"
 *
 * Layout direction: Compose keeps the existing LTR layout; Arabic text renders
 * correctly inside it. Full RTL mirroring is planned separately.
 */
object AppLanguage {

    enum class Lang(val code: String, val displayName: String) {
        ENGLISH("en", "English"),
        ARABIC("ar", "العربية")
    }

    private const val PREFS = "breathy_prefs"
    private const val KEY = "app_language"

    @Volatile
    var current: Lang = Lang.ENGLISH
        private set

    /** Load the persisted choice once at app start (called from BreathyApplication). */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = if (prefs.getString(KEY, Lang.ENGLISH.code) == Lang.ARABIC.code)
            Lang.ARABIC else Lang.ENGLISH
    }

    /** Switch the app language and persist it. Caller should recreate the UI. */
    fun set(context: Context, lang: Lang) {
        current = lang
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, lang.code).apply()
    }

    fun isArabic(): Boolean = current == Lang.ARABIC
}

/** Resolve the English/Arabic pair against the selected app language. */
fun s(en: String, ar: String): String =
    if (AppLanguage.isArabic()) ar else en

/** CompositionLocal so composables can read the live language (for reactive recomposition). */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Lang.ENGLISH }

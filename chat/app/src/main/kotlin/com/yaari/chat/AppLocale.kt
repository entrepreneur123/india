package com.yaari.chat

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language choice, for every Android version.
 * Android 13+: the system per-app language (LocaleManager), so the choice also shows in the phone's settings.
 * Android 7–12 (most low-end phones in our markets): saved in the app and applied to the activity's context.
 */
object AppLocale {
    /** BCP-47 tag to the language's own name; "" follows the phone. Same list as res/values-* and localeFilters. */
    val languages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "hi" to "हिन्दी",
        "bn" to "বাংলা",
        "pt-BR" to "Português (Brasil)",
        "vi" to "Tiếng Việt",
    )

    private const val PREFS = "yaari_settings"
    private const val KEY = "language"

    /** The chosen tag, or "" when the app follows the phone's language. */
    fun current(context: Context, sdk: Int = Build.VERSION.SDK_INT): String =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            @Suppress("NewApi") // only reached on Android 13+; [sdk] is a parameter for tests
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }

    /** Applies [tag] ("" = follow the phone). The screen is recreated in the new language. */
    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    /** For Activity.attachBaseContext on Android 12 and older: the base context in the saved language. */
    fun wrap(base: Context, sdk: Int = Build.VERSION.SDK_INT): Context {
        if (sdk >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** The display name for [tag], or null for "follow the phone". */
    fun nameOf(tag: String): String? = languages.firstOrNull { (t, _) -> t.equals(tag, ignoreCase = true) || tag.startsWith("$t-") }?.second
}

package com.yaari.chat

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language choice, for every Android version.
 * Android 13+: the system per-app language (LocaleManager), so the choice also shows in the phone's settings.
 * Android 7–12 (many low-end phones): saved in the app and applied to the activity's context.
 */
object AppLocale {
    /**
     * BCP-47 tag to the language's own name; "" follows the phone. The same languages as res/xml/locales_config.xml and
     * androidResources.localeFilters. A language without its res/values-* strings yet shows in English (Android's
     * fallback), so the list can be ahead of the translations.
     */
    val languages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "hi" to "हिन्दी",
        "bn" to "বাংলা",
        "ur" to "اردو",
        "pa" to "ਪੰਜਾਬੀ",
        "mr" to "मराठी",
        "gu" to "ગુજરાતી",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം",
        "ne" to "नेपाली",
        "pt-BR" to "Português (Brasil)",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "ro" to "Română",
        "tr" to "Türkçe",
        "ru" to "Русский",
        "uk" to "Українська",
        "ar" to "العربية",
        "fa" to "فارسی",
        "he" to "עברית",
        "sw" to "Kiswahili",
        "vi" to "Tiếng Việt",
        "th" to "ไทย",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "fil" to "Filipino",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh-Hans" to "简体中文",
        "zh-Hant" to "繁體中文",
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

    /**
     * The language matching prefers (server find_match2): the app's language, else the phone's when it is one of
     * ours, else English. One of en, hi, bn, pt-br, vi.
     */
    fun matchLanguage(context: Context): String =
        matchLanguageOf(current(context), Resources.getSystem().configuration.locales.takeIf { !it.isEmpty }?.get(0))

    internal fun matchLanguageOf(chosen: String, phone: Locale?): String {
        val tag = chosen.substringBefore(',').ifEmpty { phone?.toLanguageTag().orEmpty() }
        return when (val language = tag.substringBefore('-').substringBefore('_').lowercase()) {
            "hi", "bn", "vi" -> language
            "pt" -> "pt-br"
            else -> "en"
        }
    }

    /** The display name for [tag], or null for "follow the phone" (and languages the app doesn't offer). */
    fun nameOf(tag: String): String? = offered(tag)?.let { t -> languages.first { it.first == t }.second }

    /**
     * The offered tag [tag] stands for: old codes (iw, in, tl) and regions are folded in ("en-US" is "en", "zh-TW"
     * "zh-Hant", "pt" "pt-BR"); null when it is none of ours. Only the first tag of a list counts.
     */
    fun offered(tag: String): String? {
        val first = tag.substringBefore(',').trim().replace('_', '-')
        if (first.isEmpty()) return null
        val locale = Locale.forLanguageTag(first)
        val code = when (val lang = locale.language.lowercase()) {
            "iw" -> "he"
            "in" -> "id"
            "tl" -> "fil"
            "pt" -> "pt-BR"
            "zh" -> if (locale.script == "Hant" || (locale.script.isEmpty() && locale.country.uppercase() in setOf("TW", "HK", "MO"))) "zh-Hant" else "zh-Hans"
            else -> lang
        }
        return languages.firstOrNull { it.first.equals(code, ignoreCase = true) }?.first
    }
}

package com.yaari.chat.data

import android.content.Context
import androidx.core.content.edit

/** The chat translation settings on this phone: translate automatically (on by default) and the target language. */
interface TranslatePrefs {
    var auto: Boolean
    /** A target code (TranslateTargets), or null to follow the app's language. */
    var target: String?

    /** In memory (tests and previews). */
    class Memory(override var auto: Boolean = true, override var target: String? = null) : TranslatePrefs

    /** Kept with the app's other settings (the language on Android 12 and older). */
    class Prefs(context: Context) : TranslatePrefs {
        private val prefs = context.applicationContext.getSharedPreferences("yaari_settings", Context.MODE_PRIVATE)

        override var auto: Boolean
            get() = runCatching { prefs.getBoolean(AUTO, true) }.getOrDefault(true)
            set(value) = prefs.edit { putBoolean(AUTO, value) }

        override var target: String?
            get() = runCatching { prefs.getString(TARGET, null) }.getOrNull()?.ifEmpty { null }
            set(value) = prefs.edit { if (value == null) remove(TARGET) else putString(TARGET, value) }

        private companion object {
            const val AUTO = "translate_auto"
            const val TARGET = "translate_to"
        }
    }
}

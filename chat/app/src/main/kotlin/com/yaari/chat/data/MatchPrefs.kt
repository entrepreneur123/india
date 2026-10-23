package com.yaari.chat.data

import android.content.Context
import androidx.core.content.edit
import com.yaari.chat.domain.Countries

/** The random chat's countries to meet on this phone (043, free; empty = anywhere). */
interface MatchPrefs {
    var countries: List<String>

    /** In memory (tests and previews). */
    class Memory(override var countries: List<String> = emptyList()) : MatchPrefs

    /** Kept with the app's other settings (yaari_settings, like the translation ones). */
    class Prefs(context: Context) : MatchPrefs {
        private val prefs = context.applicationContext.getSharedPreferences("yaari_settings", Context.MODE_PRIVATE)

        override var countries: List<String>
            get() = runCatching { prefs.getString(KEY, null) }.getOrNull().orEmpty().split(',').filter { Countries.byIso(it) != null }.take(MAX)
            set(value) = prefs.edit { putString(KEY, value.joinToString(",")) }

        private companion object {
            const val KEY = "meet_countries"
        }
    }

    companion object {
        /** find_match3 takes at most this many. */
        const val MAX = 10
    }
}

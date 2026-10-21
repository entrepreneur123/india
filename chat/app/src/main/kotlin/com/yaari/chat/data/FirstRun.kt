package com.yaari.chat.data

import android.content.Context
import androidx.core.content.edit

/**
 * First-run steps, each shown once on this phone: the interests question, the three-card tour, the notifications
 * permission (after the first friend). Kept in the app's settings, like the language on Android 12 and older.
 */
interface FirstRun {
    fun done(step: Step): Boolean
    fun mark(step: Step)

    enum class Step(val key: String) { INTERESTS("interests_asked"), TOUR("tour_seen"), NOTIFICATIONS("notifications_asked") }

    /** Everything already seen: nothing first-run shows (tests and previews). */
    object Seen : FirstRun {
        override fun done(step: Step) = true
        override fun mark(step: Step) = Unit
    }

    /** In memory (tests of the first run). */
    class Memory(vararg seen: Step) : FirstRun {
        private val steps = seen.toMutableSet()
        override fun done(step: Step) = step in steps
        override fun mark(step: Step) {
            steps += step
        }
    }

    class Prefs(context: Context) : FirstRun {
        private val prefs = context.applicationContext.getSharedPreferences("yaari_first_run", Context.MODE_PRIVATE)
        override fun done(step: Step) = runCatching { prefs.getBoolean(step.key, false) }.getOrDefault(true) // unreadable: never nag
        override fun mark(step: Step) = prefs.edit { putBoolean(step.key, true) }
    }
}

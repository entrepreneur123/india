package com.yaari.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The in-app language choice on Android 7–12 (sdk = 30 below), where the system has no per-app language setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class AppLocaleTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val defaultLocale = java.util.Locale.getDefault()

    @After fun restore() {
        java.util.Locale.setDefault(defaultLocale) // wrap() sets the process default, as the app does
        choose("")
    }

    private fun choose(tag: String) =
        app.getSharedPreferences("yaari_settings", Context.MODE_PRIVATE).edit().putString("language", tag).commit()

    @Test fun savedLanguageIsAppliedToTheActivityContext() {
        choose("vi")
        assertEquals("vi", AppLocale.current(app, sdk = 30))
        assertEquals("Cài đặt", AppLocale.wrap(app, sdk = 30).getString(R.string.settings))
        choose("pt-BR")
        assertEquals("Configurações", AppLocale.wrap(app, sdk = 30).getString(R.string.settings))
        choose("bn")
        assertEquals("সেটিংস", AppLocale.wrap(app, sdk = 30).getString(R.string.settings))
    }

    @Test fun followingThePhoneLeavesTheContextAlone() {
        choose("")
        assertEquals("Settings", AppLocale.wrap(app, sdk = 30).getString(R.string.settings))
        assertEquals(null, AppLocale.nameOf(""))
        assertEquals("Português (Brasil)", AppLocale.nameOf("pt-BR"))
        assertEquals("English", AppLocale.nameOf("en-US"))
    }

    /** Every shipped language can be picked in the app. */
    @Test fun everyShippedLanguageIsOffered() {
        assertEquals(setOf("en", "hi", "bn", "pt-BR", "vi"), AppLocale.languages.map { it.first }.toSet())
    }
}

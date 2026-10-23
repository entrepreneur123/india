package com.yaari.chat

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yaari.chat.data.AppForeground
import com.yaari.chat.ui.AppViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Language while in a room: the room (and everything else on) stays. */
@RunWith(RobolectricTestRunner::class)
class LanguageChangeTest {
    /** Closing the activity marks the app as in the background (a global): the next tests expect it on screen. */
    @After fun onScreenAgain() = AppForeground.set(true)

    /** Android 13+: the activity takes the change itself, so nothing on screen (a video call's connection) is rebuilt. */
    @Config(sdk = [35])
    @Test fun theActivityHandlesALanguageChangeItself() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        val both = ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION
        assertEquals(both, info.configChanges and both)
    }

    /**
     * Android 12 and older: Settings recreates the activity (AppLocale.set). The view model, which holds the room, the
     * random chat and the call state (VoiceController, AppViewModel), lives on.
     */
    @Config(sdk = [35])
    @Test fun aRecreatedActivityKeepsTheViewModel() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var vm: AppViewModel
            sc.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
            sc.recreate()
            sc.onActivity { assertSame(vm, ViewModelProvider(it)[AppViewModel::class.java]) }
        }
    }
}

package com.yaari.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.yaari.chat.data.RoomSounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** Which sound the chime plays as, by the phone's mode when it plays (RoomSounds.Device). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomSoundsDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val sounds = RoomSounds.Device(context)
    private val usage get() = sounds.playing?.audioAttributes?.usage

    @Test fun normalModeIsAnAppSoundAndCallModeIsTheVoicesSound() {
        audio.mode = AudioManager.MODE_NORMAL
        sounds.play(join = true)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, usage)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        sounds.play(join = true) // someone came up while the voice stays connected
        assertEquals(AudioAttributes.USAGE_VOICE_COMMUNICATION, usage)
    }

    @Test fun whileTheVoiceClosesTheChimeWaitsForCallModeToEnd() {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        sounds.play(join = false, settling = true)
        assertNull("nothing yet: a call sound now would end up on the earpiece", sounds.playing)
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
        assertNull(sounds.playing)
        audio.mode = AudioManager.MODE_NORMAL
        ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, usage)
    }

    @Test fun callModeThatStaysPlaysAfterTheWaitAsTheVoices() {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        sounds.play(join = false, settling = true)
        ShadowLooper.idleMainLooper(1_600, TimeUnit.MILLISECONDS)
        assertEquals(AudioAttributes.USAGE_VOICE_COMMUNICATION, usage)
    }

    @Test fun aNewerChimeReplacesOneStillWaiting() {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        sounds.play(join = false, settling = true) // stepped off: waits for the voice to close
        audio.mode = AudioManager.MODE_NORMAL
        sounds.play(join = true) // straight back on: plays now
        val joined = sounds.playing!!
        ShadowLooper.idleMainLooper(2_000, TimeUnit.MILLISECONDS)
        assertEquals("the stale leave chime never plays after the newer one", joined, sounds.playing)
    }
}

package com.yaari.chat

import com.yaari.chat.data.BackendException
import com.yaari.chat.data.NewProfile
import com.yaari.chat.live.SupabaseBackend
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceRole
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Voice rooms against the real Supabase server (017_voice_rooms.sql) with two QA users: create, list, join, chat,
 * raise a hand, leave, end. No audio here (no WebRTC on the JVM). Runs only when YAARI_QA_PASSWORD is set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveVoiceTest {
    private val password = System.getenv("YAARI_QA_PASSWORD")

    private suspend fun user(email: String, name: String, gender: Gender): SupabaseBackend {
        val b = SupabaseBackend(RuntimeEnvironment.getApplication(), persistSession = false)
        b.signInWithEmailForTest(email, password)
        if (b.myProfile() == null) b.createProfile(NewProfile(name, gender, LocalDate.of(1999, 1, 1), "Mumbai", "QA account", null))
        return b
    }

    private fun code(block: suspend () -> Unit): String? = runBlocking {
        try { block(); null } catch (e: BackendException) { e.code }
    }

    @Test fun createJoinChatLeaveAndEnd() = runBlocking {
        assumeTrue("set YAARI_QA_PASSWORD to run against the live server", !password.isNullOrBlank())
        val host = user("qa1@yaari.test", "QA Arjun", Gender.MALE)
        val guest = user("qa2@yaari.test", "QA Priya", Gender.FEMALE)

        assertEquals("room_title", code { host.createVoiceRoom(NewVoiceRoom("x", RoomCategory.MUSIC, SpeakMode.APPROVAL)) })
        val id = host.createVoiceRoom(NewVoiceRoom("QA voice test", RoomCategory.MUSIC, SpeakMode.APPROVAL))
        val mine = host.voiceRooms().first { it.id == id }
        assertTrue(mine.mine)

        // The host enters on seat 0; the guest as a listener.
        val asHost = withTimeout(15_000) { host.voiceSession(id).filterIsInstance<VoiceEvent.RoomChanged>().first() }.room
        assertEquals(0, asHost.me!!.seat)
        assertEquals(VoiceRole.HOST, asHost.me!!.role)
        val asGuest = withTimeout(15_000) { guest.voiceSession(id).filterIsInstance<VoiceEvent.RoomChanged>().first() }.room
        assertNull(asGuest.me!!.seat)
        assertEquals(VoiceRole.LISTENER, asGuest.me!!.role)
        assertTrue(asGuest.track == null && asGuest.seats.size == 9)

        // Chat, and the system line for the guest's arrival.
        val line = guest.sendVoice(id, "hello from the live voice test")
        assertTrue((line as VoiceLine.Text).fromMe)
        val lines = host.voiceLines(id)
        assertTrue(lines.any { it is VoiceLine.Text && it.text == "hello from the live voice test" && !it.fromMe })

        // Rules: a listener can't mute or end; a hand goes up.
        assertEquals("not_allowed", code { guest.endVoice(id) })
        guest.voiceAction(id, VoiceAction.Hand(true))

        guest.leaveVoice(id)
        host.endVoice(id)
        assertEquals("room_closed", code { guest.voiceSession(id).first() })
        assertTrue(host.voiceRooms().none { it.id == id })
    }
}

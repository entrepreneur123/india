package com.yaari.chat

import com.yaari.chat.data.BackendException
import com.yaari.chat.data.NewProfile
import com.yaari.chat.live.SupabaseBackend
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomRules
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
 * Rooms against the real Supabase server (017_voice_rooms.sql, 019_unified_rooms.sql) with two QA users: create (private, with a code), list, join by code, chat,
 * icebreakers, song requests, the stage, settings, leave, end. No audio here (no WebRTC on the JVM). Runs only when YAARI_QA_PASSWORD is set.
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

        assertEquals("room_title", code { host.createVoiceRoom(NewVoiceRoom("x", RoomCategory.TALK)) })
        assertEquals("room_size", code { host.createVoiceRoom(NewVoiceRoom("QA size", RoomCategory.TALK, RoomRules(maxMembers = 201))) })
        val made = host.createVoiceRoom(NewVoiceRoom("QA rooms test", RoomCategory.MUSIC, RoomRules(isPrivate = true), "QA announcement"))
        val id = made.id
        val mine = host.voiceRooms().first { it.id == id }
        assertTrue(mine.mine)
        assertEquals(made.code, mine.code)
        assertTrue("private: not listed for others", guest.voiceRooms().none { it.id == id })

        // The host is not on the stage when the room opens; the guest comes in with the code.
        val asHost = withTimeout(15_000) { host.voiceSession(id).filterIsInstance<VoiceEvent.RoomChanged>().first() }.room
        assertNull(asHost.me!!.seat)
        assertEquals(VoiceRole.HOST, asHost.me!!.role)
        assertEquals("QA announcement" to made.code, asHost.announcement to asHost.code)
        assertEquals("not_found", code { guest.joinVoiceCode("ZZZZZZ") })
        assertEquals(id, guest.joinVoiceCode(made.code.lowercase()))
        val asGuest = withTimeout(15_000) { guest.voiceSession(id).filterIsInstance<VoiceEvent.RoomChanged>().first() }.room
        assertNull(asGuest.me!!.seat)
        assertNull("only the host sees the code", asGuest.code)

        // Chat, an icebreaker (one every 20 s), song requests by vote.
        val line = guest.sendVoice(id, "hello from the live rooms test")
        assertTrue((line as VoiceLine.Text).fromMe)
        assertTrue(guest.voiceIcebreaker(id) is VoiceLine.Icebreaker)
        assertEquals("slow_down", code { host.voiceIcebreaker(id) })
        assertTrue(host.voiceLines(id).any { it is VoiceLine.Text && it.text == "hello from the live rooms test" && !it.fromMe })
        guest.voiceAction(id, VoiceAction.AddTrack("BddP6PYo2gs", guest.videoTitle("BddP6PYo2gs")))
        assertTrue(host.voiceLeaders(id).isEmpty())

        // Rules: a listener can't end; the host joins the voice on seat 0 and leaves it again.
        assertEquals("not_allowed", code { guest.endVoice(id) })
        host.voiceAction(id, VoiceAction.TakeSeat(0))
        host.voiceAction(id, VoiceAction.Unseat)
        host.updateVoiceRoom(id, "QA rooms test 2", "")

        guest.leaveVoice(id)
        host.endVoice(id)
        assertEquals("room_closed", code { guest.voiceSession(id).first() })
        assertTrue(host.voiceRooms().none { it.id == id })
    }
}

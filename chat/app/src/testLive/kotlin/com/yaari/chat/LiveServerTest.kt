package com.yaari.chat

import com.yaari.chat.data.BackendException
import com.yaari.chat.data.MatchEvent
import com.yaari.chat.data.NewProfile
import com.yaari.chat.live.SupabaseBackend
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Target
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * Two QA users against the real Supabase server: match, realtime chat, exit lock, Premium checks,
 * profile visibility, partner-left event. Runs only when YAARI_QA_PASSWORD is set (never in CI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveServerTest {
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

    @Test fun deleteAccountRemovesTheUser() = runBlocking {
        assumeTrue(!password.isNullOrBlank() && System.getenv("YAARI_QA_DELETE") == "1")
        val c = SupabaseBackend(RuntimeEnvironment.getApplication(), persistSession = false)
        c.signInWithEmailForTest("qa3@yaari.test", password)
        assertNotNull("qa3 has a profile", c.myProfile())
        c.deleteAccount()
        val again = SupabaseBackend(RuntimeEnvironment.getApplication(), persistSession = false)
        val signIn = runCatching { again.signInWithEmailForTest("qa3@yaari.test", password) }
        assertTrue("deleted user must not sign in", signIn.isFailure)
    }

    @Test fun twoUsersMatchChatAndLeave() = runBlocking {
        assumeTrue("set YAARI_QA_PASSWORD to run against the live server", !password.isNullOrBlank())
        val a = user("qa1@yaari.test", "QA Arjun", Gender.MALE)
        val b = user("qa2@yaari.test", "QA Priya", Gender.FEMALE)
        assertNotNull(a.myProfile())

        // Strangers can't see each other yet; free users can't filter by gender.
        assertEquals("premium_required", code { a.findMatch(Target.FEMALE) })
        assertEquals(false, a.entitlements().premium)

        // A waits, B arrives, both end up in the same match.
        assertNull("A should queue", a.findMatch(Target.ANY))
        val mb = withTimeout(10_000) { b.findMatch(Target.ANY) }
        assertNotNull("B should match A", mb)
        val ma = withTimeout(10_000) { a.findMatch(Target.ANY) }
        assertEquals(mb!!.id, ma!!.id)
        assertEquals("QA Arjun", mb.partner.name)
        assertTrue(mb.partner.age >= 18)

        // Within 5 s of matching the server refuses to let a free user leave; video needs Premium.
        assertEquals("exit_locked", code { a.leave(ma.id) })
        assertEquals("premium_required", code { a.requestVideo(ma.id) })

        // Realtime: A receives B's message.
        val incoming = async { withTimeout(15_000) { a.events(ma.id).filterIsInstance<MatchEvent.NewMessage>().first { !it.message.fromMe } } }
        delay(2_000) // let A's realtime channel subscribe
        val sent = b.send(mb.id, "hello from live test", null)
        assertTrue(sent.fromMe)
        assertEquals("hello from live test", incoming.await().message.text)

        // Matched people can see each other's profile.
        assertEquals("QA Priya", a.profile(ma.partner.id)?.name)

        // After 5 s A leaves; B is told through realtime.
        val left = async { withTimeout(20_000) { b.events(mb.id).filterIsInstance<MatchEvent.PartnerLeft>().first() } }
        delay(5_500)
        a.leave(ma.id)
        left.await()
        assertTrue(a.recents().any { it.matchId == ma.id })
        assertEquals(listOf("hello from live test"), b.history(mb.id).map { it.text })
    }
}

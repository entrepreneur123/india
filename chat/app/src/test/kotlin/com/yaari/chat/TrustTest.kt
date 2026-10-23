package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.FirstRun
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.KycResult
import com.yaari.chat.model.MANNERS
import com.yaari.chat.model.MannerLevel
import com.yaari.chat.model.MannerTag
import com.yaari.chat.model.Target
import com.yaari.chat.model.Trust
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ChatState
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.VerifyOrigin
import com.yaari.chat.ui.VerifyPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** 0.12.0: ID verification (035, functions/kyc), badges for everyone on screen, manners after a random chat. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrustTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000_000L
    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(ControlledRandom(), clock = { now }, today = { today })

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(server: Backend = backend, firstRun: FirstRun = FirstRun.Seen): AppViewModel {
        val vm = AppViewModel(server, DemoBilling(), clock = { now }, today = { today }, firstRun = firstRun)
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "1998", "Pune", agreed = true)); advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        return vm
    }

    /**
     * A random chat where both wrote, then left normally. Manners are off (043): nothing asks for a rating; the chat as
     * it ended is handed back so the rating rules can be checked on [com.yaari.chat.ui.TrustController.chatEnded].
     */
    private fun TestScope.chatAndLeave(vm: AppViewModel, partnerWrites: Boolean = true): ChatState {
        vm.startMatching()
        advanceTimeBy(5_000); runCurrent()
        val c = vm.state.value.chat ?: error("not matched")
        vm.sendText("Hi!")
        advanceUntilIdle()
        if (partnerWrites) backend.partnerSays(c.match.id, "Hello from here")
        // The history merge picks up what the partner wrote (as a re-sync does).
        now += 10_000
        advanceUntilIdle()
        if (partnerWrites && vm.state.value.chat!!.messages.none { !it.fromMe }) {
            // Scripted partners answer through events; make sure the screen knows about the line.
            vm.sendText("Still there?")
            advanceUntilIdle()
        }
        val ended = vm.state.value.chat!!
        vm.leave(next = false)
        advanceUntilIdle()
        assertNull(vm.state.value.chat)
        assertNull("manners are off: no rating sheet", vm.trust.state.value.rating)
        return ended
    }

    // ---- verification ----

    @Test fun verifyFlowOpensTheProvidersPageAndReadsTheResultOnReturn() = runTest(dispatcher) {
        val vm = signedIn()
        assertFalse(vm.trust.state.value.mine!!.verified)
        vm.trust.open()
        assertEquals(VerifyPhase.START, vm.trust.state.value.verify!!.phase)
        vm.trust.start()
        advanceUntilIdle()
        assertEquals(VerifyPhase.WAITING, vm.trust.state.value.verify!!.phase)
        assertEquals(FakeBackend.KYC_URL, vm.trust.state.value.launch)
        vm.trust.opened()
        vm.resumed() // back from the browser
        advanceUntilIdle()
        assertEquals(VerifyPhase.VERIFIED, vm.trust.state.value.verify!!.phase)
        assertTrue(vm.trust.state.value.mine!!.verified)
        assertTrue("my profile reloaded with the badge", vm.state.value.me!!.verified)
        assertTrue(vm.trust.people.value[vm.state.value.me!!.id]!!.verified)
        // Opening it again goes straight to "You're verified"; starting again is refused by the server.
        vm.trust.close()
        vm.trust.open()
        assertEquals(VerifyPhase.VERIFIED, vm.trust.state.value.verify!!.phase)
        try {
            backend.kycStart(); fail()
        } catch (e: BackendException) {
            assertEquals("already_verified", e.code)
        }
    }

    @Test fun eachOutcomeHasItsOwnState() = runTest(dispatcher) {
        val vm = signedIn()
        val outcomes = mapOf(
            KycResult.PENDING to VerifyPhase.PENDING, KycResult.DECLINED to VerifyPhase.DECLINED, KycResult.UNREADABLE to VerifyPhase.UNREADABLE,
            KycResult.DUPLICATE to VerifyPhase.DUPLICATE, KycResult.MINOR to VerifyPhase.MINOR,
        )
        vm.trust.open()
        for ((result, phase) in outcomes) {
            backend.kycOutcome = result
            vm.trust.start(); advanceUntilIdle()
            vm.trust.opened()
            vm.trust.check(); advanceUntilIdle()
            assertEquals(result.name, phase, vm.trust.state.value.verify!!.phase)
            if (result == KycResult.MINOR) break
        }
        assertFalse(vm.trust.state.value.mine!!.verified)
        // The server allows 5 attempts a day; the sixth says "too many tries".
        vm.trust.start(); advanceUntilIdle()
        assertEquals(VerifyPhase.LIMIT, vm.trust.state.value.verify!!.phase)
        assertTrue("closable", vm.trust.canClose(vm.trust.state.value.verify!!))
    }

    @Test fun noProviderYetSaysComingSoonAndStopsNagging() = runTest(dispatcher) {
        backend.kycAvailable = false
        val firstRun = FirstRun.Memory()
        val vm = signedIn(firstRun = firstRun)
        // After sign-up the screen is offered once.
        assertEquals(VerifyOrigin.ONBOARDING, vm.trust.state.value.verify!!.origin)
        assertTrue(firstRun.done(FirstRun.Step.VERIFY))
        vm.trust.start(); advanceUntilIdle()
        assertEquals(VerifyPhase.UNAVAILABLE, vm.trust.state.value.verify!!.phase)
        assertTrue(vm.trust.state.value.comingSoon)
        assertTrue(firstRun.done(FirstRun.Step.KYC_SOON))
        vm.trust.close()
        assertNull(vm.state.value.notice)
    }

    @Test fun signUpOffersTheCheckOnceAndIsSkippableUnlessRequired() = runTest(dispatcher) {
        val vm = signedIn(firstRun = FirstRun.Memory())
        val v = vm.trust.state.value.verify!!
        assertEquals(VerifyOrigin.ONBOARDING, v.origin)
        assertTrue(vm.trust.canClose(v))
        vm.trust.close()

        backend.signOut()
        backend.requireVerification = true
        val other = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today }, firstRun = FirstRun.Seen)
        advanceUntilIdle()
        other.requestOtp(Countries.byIso("IN")!!, "9811111111"); advanceUntilIdle()
        other.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        other.createProfile(ProfileDraft("Ravi", Gender.MALE, "1", "1", "1995", agreed = true)); advanceUntilIdle()
        val req = other.trust.state.value.verify!!
        assertTrue(req.required)
        assertFalse("required right after sign-up: no skipping", other.trust.canClose(req))
    }

    @Test fun verifyRequiredOpensTheScreenNotAToast() = runTest(dispatcher) {
        val vm = signedIn()
        backend.requireVerification = true
        vm.startMatching()
        advanceTimeBy(5_000); runCurrent()
        advanceUntilIdle()
        assertFalse("the search stops", vm.state.value.searching)
        assertEquals(VerifyOrigin.REQUIRED, vm.trust.state.value.verify!!.origin)
        assertNull(vm.state.value.notice)
        vm.trust.close()
        // Posting, commenting and friend requests too.
        vm.addPost("Hello", null); advanceUntilIdle()
        assertNotNull(vm.trust.state.value.verify)
        assertNull(vm.state.value.notice)
        vm.trust.close()
        vm.addFriend(FakeBackend.POOL.first { it.id == "p1" }); advanceUntilIdle()
        assertNotNull(vm.trust.state.value.verify)
        // Once verified, it all works again.
        vm.trust.start(); advanceUntilIdle(); vm.trust.opened(); vm.trust.check(); advanceUntilIdle()
        vm.trust.close()
        vm.addPost("Hello", null); advanceUntilIdle()
        assertEquals("Hello", vm.state.value.me!!.posts.first().text)
    }

    // ---- manners ----

    @Test fun mannersAreOff() = assertFalse("043: turn back on in model/Trust.kt", MANNERS)

    @Test fun aLowMannerScoreNoLongerStopsRandomChat() = runTest(dispatcher) {
        val vm = signedIn()
        backend.rateMe(good = false, n = 8)
        assertEquals(19, FakeBackend.manner(0, 8)) // below the old floor (20)
        vm.startMatching()
        advanceTimeBy(5_000); runCurrent()
        assertNotNull("matched as usual", vm.state.value.chat)
        assertFalse(vm.trust.state.value.mannerLow)
        assertNull(vm.state.value.notice)
    }

    @Test fun ratingAskedOnceWhenBothWroteForWhenMannersAreBack() = runTest(dispatcher) {
        val vm = signedIn()
        val c = chatAndLeave(vm)
        val match = c.match.id
        vm.trust.chatEnded(c) // what AppViewModel.leave does while MANNERS is on
        val r = vm.trust.state.value.rating ?: error("no rating sheet")
        assertEquals(match, r.matchId)
        val partner = r.partner.id
        val before = runTestTrust(partner)
        vm.trust.rateGood(true)
        MannerTag.of(true).forEach(vm.trust::toggleTag) // only three stick
        assertEquals(MannerTag.MAX, vm.trust.state.value.rating!!.tags.size)
        vm.trust.toggleTag(MannerTag.RUDE) // a bad tag can't go on a good rating
        assertTrue(vm.trust.state.value.rating!!.tags.all { it.good })
        vm.trust.rateGood(false) // switching clears the tags
        assertTrue(vm.trust.state.value.rating!!.tags.isEmpty())
        vm.trust.rateGood(true)
        vm.trust.toggleTag(MannerTag.KIND)
        vm.trust.submitRating(); advanceUntilIdle()
        assertNull(vm.trust.state.value.rating)
        assertEquals(UiTextOf(R.string.rate_thanks), vm.state.value.notice)
        assertTrue(match in vm.trust.state.value.mine!!.rated)
        val after = runTestTrust(partner)
        assertEquals(before.ratings + 1, after.ratings)
        vm.trust.chatEnded(c)
        assertNull("asked once", vm.trust.state.value.rating)
        // The server refuses a second rating of the same chat.
        try {
            backend.rateChat(match, true, emptyList()); fail()
        } catch (e: BackendException) {
            assertEquals("already_rated", e.code)
        }
    }

    @Test fun noRatingWhenOnlyIWroteAndSkipIsRemembered() = runTest(dispatcher) {
        val vm = signedIn()
        val quiet = FakeBackend(ControlledRandom(roll = 10), clock = { now }, today = { today }) // partners never answer (roll < 15)
        val vm2 = signedIn(server = quiet)
        vm2.startMatching(); advanceTimeBy(5_000); runCurrent()
        vm2.sendText("Hello?"); advanceUntilIdle()
        now += 10_000
        val c = vm2.state.value.chat!!
        vm2.leave(next = false); advanceUntilIdle()
        vm2.trust.chatEnded(c)
        assertNull("the partner never wrote", vm2.trust.state.value.rating)
        try {
            quiet.rateChat(c.match.id, true, emptyList()); fail()
        } catch (e: BackendException) {
            assertEquals("not_allowed", e.code)
        }
        // Skipping closes the sheet, and it isn't asked again.
        val c2 = chatAndLeave(vm)
        vm.trust.chatEnded(c2)
        assertNotNull(vm.trust.state.value.rating)
        vm.trust.skipRating()
        assertNull(vm.trust.state.value.rating)
        vm.trust.chatEnded(c2)
        assertNull(vm.trust.state.value.rating)
    }

    @Test fun ratingRulesAsTheServer() = runTest(dispatcher) {
        val vm = signedIn()
        val match = chatAndLeave(vm).match.id
        fun refused(code: String, block: suspend () -> Unit) = kotlinx.coroutines.runBlocking {
            try {
                block(); fail("expected $code")
            } catch (e: BackendException) {
                assertEquals(code, e.code)
            }
        }
        refused("bad_request") { backend.rateChat(match, true, listOf(MannerTag.KIND, MannerTag.RUDE)) }
        refused("bad_request") { backend.rateChat(match, false, listOf(MannerTag.RUDE, MannerTag.SPAM, MannerTag.FAKE, MannerTag.CREEPY)) }
        refused("not_found") { backend.rateChat("nope", true, emptyList()) }
        now += 2 * 86_400_000L
        refused("too_late") { backend.rateChat(match, true, emptyList()) }
    }

    @Test fun badgesOfEveryoneOnScreenComeInOneBatch() = runTest(dispatcher) {
        var calls = 0
        var asked = emptyList<String>()
        val counting = object : Backend by backend {
            override suspend fun trust(userIds: List<String>): List<Trust> {
                calls++
                asked = userIds
                return backend.trust(userIds)
            }
        }
        val vm = signedIn(server = counting)
        vm.trust.want(listOf("p1", "p2"))
        vm.trust.want(listOf("p3"))
        vm.trust.want(listOf("p1"))
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf("p1", "p2", "p3"), asked)
        val people = vm.trust.people.value
        assertTrue(people.getValue("p1").verified)
        assertFalse(people.getValue("p2").verified)
        assertEquals(MannerLevel.GREAT, people.getValue("p1").level)
        assertEquals(listOf(MannerTag.KIND, MannerTag.FUNNY, MannerTag.GOOD_LISTENER), people.getValue("p1").goodTags.map { it.tag })
        assertFalse("one rating: New here", backend.trust(listOf("p7")).single().rated)
        // Known ones aren't asked again.
        vm.trust.want(listOf("p1", "p3"))
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun levelsAndScore() {
        assertEquals(MannerLevel.NEEDS_WORK, MannerLevel.of(19))
        assertEquals(MannerLevel.LOW, MannerLevel.of(20))
        assertEquals(MannerLevel.GOOD, MannerLevel.of(40))
        assertEquals(MannerLevel.GOOD, MannerLevel.of(69))
        assertEquals(MannerLevel.GREAT, MannerLevel.of(70))
        assertEquals(MannerLevel.EXCELLENT, MannerLevel.of(90))
        assertEquals(50, FakeBackend.manner(0, 0))
        assertEquals(81, FakeBackend.manner(24, 1))
        assertEquals(90, FakeBackend.manner(40, 0))
    }

    @Test fun postAuthorsCarryTheBadge() = runTest(dispatcher) {
        signedIn()
        assertTrue(backend.post("p1-0")!!.author!!.verified)
        assertFalse(backend.post("p2-0")!!.author!!.verified)
    }

    private suspend fun runTestTrust(id: String) = backend.trust(listOf(id)).single()

    @Suppress("FunctionName")
    private fun UiTextOf(id: Int) = com.yaari.chat.ui.UiText(id)
}

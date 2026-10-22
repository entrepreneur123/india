package com.yaari.chat

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.KycResult
import com.yaari.chat.model.MannerTag
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.VerifyPhase
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 0.12.0 screens: verifying an identity, the seal next to names, manners on profiles, rating a chat, manner_low. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class TrustScreensTest {
    @get:Rule val compose = createComposeRule()

    private var now = System.currentTimeMillis()
    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, instant = true)

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/trust_$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun signIn(): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today })
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "1998", "Pune", "Chai and long walks", agreed = true)) }
        compose.waitUntil(5_000) { vm.state.value.me != null && vm.trust.state.value.mine != null }
        return vm
    }

    @Test fun verifyMyIdentity() {
        val vm = signIn()
        compose.onNodeWithText("Me").performClick()
        waitFor("Get your verified badge")
        shot("01_me_unverified")
        compose.onNodeWithText("Get your verified badge").performClick()
        waitFor("An ID and a quick selfie")
        shot("02_verify_start")
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Why it matters"))
        shot("03_verify_what_we_keep")
        compose.onNodeWithText("Start verification").performClick()
        waitFor("You’re verified")
        shot("04_verified")
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(5_000) { vm.trust.state.value.verify == null }
        waitFor("ID verified · Female · 28")
        shot("05_me_verified")
        assertTrue(vm.state.value.me!!.verified)
    }

    @Test fun otherStates() {
        val vm = signIn()
        backend.kycOutcome = KycResult.PENDING
        compose.runOnUiThread { vm.trust.open(); vm.trust.start() }
        waitFor("We’re checking your ID")
        shot("06_pending")
        backend.kycOutcome = KycResult.DUPLICATE
        compose.onNodeWithText("Check again").performClick()
        waitFor("This ID is already in use")
        shot("07_duplicate")
        compose.onNodeWithText("Done").performClick()
        backend.kycOutcome = KycResult.DECLINED
        compose.runOnUiThread { vm.trust.open(); vm.trust.start() }
        waitFor("That didn’t go through")
        shot("08_declined")
        backend.kycAvailable = false
        compose.onNodeWithText("Try again").performClick()
        waitFor("Coming soon")
        shot("09_coming_soon")
        assertEquals(VerifyPhase.UNAVAILABLE, vm.trust.state.value.verify!!.phase)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Me").performClick()
        waitFor("ID verification is coming soon")
        shot("10_me_coming_soon")
    }

    @Test fun badgesAndMannersOnProfilesAndPosts() {
        val vm = signIn()
        val post = kotlinx.coroutines.runBlocking { backend.post("p1-0")!! }
        compose.runOnUiThread { vm.feed.openDetail(post) }
        waitFor("Marine Drive evenings")
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("ID verified")).fetchSemanticsNodes().isNotEmpty() }
        shot("11_post_badges")
        compose.runOnUiThread { vm.feed.closeDetail() }
        compose.runOnUiThread { vm.openProfile(FakeBackend.POOL.first { it.id == "p1" }) }
        waitFor("ID verified · Female · 22 · India")
        waitFor("Great")
        shot("12_their_profile")
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Kind · 12"))
        shot("13_their_manners")
        compose.runOnUiThread { vm.closeProfile(); vm.openProfile(FakeBackend.POOL.first { it.id == "p2" }) }
        waitFor("Lucas")
        // Unverified and rated by few: the badge comes from the batched lookup (once the profile is on screen).
        compose.waitUntil(5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) // the batch's short pause
            vm.trust.people.value.containsKey("p2")
        }
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Manners"))
        shot("14_unverified_profile")
    }

    @Test fun randomChatRevealHeaderAndRating() {
        val vm = signIn()
        // Badges load in one batch (here ahead of the match, so the reveal can be caught mid-way with the clock held).
        compose.runOnUiThread { vm.trust.want(FakeBackend.POOL.map { it.id }) }
        compose.waitUntil(5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) // the batch's short pause
            vm.trust.people.value.containsKey("p1")
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { vm.startMatching() }
        compose.waitUntil(5_000) { vm.state.value.chat != null }
        // The reveal: the seal and "Great manners" (Priya, the first scripted partner).
        compose.mainClock.advanceTimeBy(500)
        shot("15_match_reveal")
        compose.mainClock.autoAdvance = true
        compose.runOnUiThread { vm.sendText("Hi! How is Mumbai today?") }
        compose.waitUntil(5_000) { vm.state.value.chat!!.messages.any { !it.fromMe } }
        shot("16_chat_header")
        now += 6_000
        compose.runOnUiThread { vm.leave(next = false) }
        waitFor("How was your chat with")
        shot("17_rate_sheet")
        compose.onNodeWithText("Good").performClick()
        waitFor("What stood out?")
        compose.onNodeWithText("Kind").performClick()
        compose.onNodeWithText("Funny").performClick()
        shot("18_rate_good_tags")
        compose.onNodeWithText("Not good").performClick()
        waitFor("What went wrong?")
        compose.onNodeWithText("Rude").performClick()
        shot("19_rate_bad_tags")
        compose.onNodeWithText("Good").performClick()
        compose.onNodeWithText("Kind").performClick()
        compose.onNodeWithText("Submit").performClick()
        compose.waitUntil(5_000) { vm.trust.state.value.rating == null }
        assertTrue(vm.trust.state.value.mine!!.rated.isNotEmpty())
    }

    @Test fun mannerLowSheet() {
        val vm = signIn()
        backend.rateMe(good = false, n = 10)
        backend.rateMe(good = true, n = 1, tags = listOf(MannerTag.FUNNY))
        compose.runOnUiThread { vm.startMatching() }
        waitFor("Random chat is paused")
        shot("20_manner_low")
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Me").performClick()
        waitFor("Needs work")
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Needs work"))
        shot("21_me_needs_work")
    }
}

package com.yaari.chat

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.Backend
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Interest
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.ProfilePrompt
import com.yaari.chat.model.ProfileStyle
import com.yaari.chat.model.PromptQ
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceReaction
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Free voice and video calls (random chat, a friend's thread), my profile as others see it, room links, reactions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CallsAndProfileScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    /** Partners always accept a call (the roll of 50 is under the demo's 70). */
    private val fake = FakeBackend(ControlledRandom(), today = { today }, instant = true)
    /** p1, ID verified, with a status. */
    private val backend = object : Backend by fake {
        override suspend fun profile(userId: String) = fake.profile(userId)?.let { if (it.id == "p1") it.copy(verified = true) else it }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("/tmp/claude-0/-home-user-india/ddb0ac4a-dcc2-550f-b595-fb27e8220261/scratchpad/shots/p2-android-$name.png")
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = compose.waitUntil(5_000) { has(text) }
    private fun click(text: String) = compose.onAllNodes(hasText(text, substring = true))[0].performClick()
    private fun tap(description: String) = compose.onAllNodes(hasContentDescription(description))[0].performClick()

    private fun signIn(): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today })
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
        return vm
    }

    private fun matched(vm: AppViewModel) {
        compose.runOnUiThread { vm.startMatching() }
        compose.waitUntil(5_000) { vm.state.value.chat != null }
        waitFor("View profile")
    }

    @Test fun randomChatOffersVoiceAndVideoCallsWithoutPlus() {
        val vm = signIn()
        matched(vm)
        tap("Call")
        waitFor("Voice call")
        waitFor("Video call")
        shot("call-menu")
        click("Voice call")
        compose.waitUntil(5_000) { vm.state.value.call != null }
        assertFalse("a voice call starts with the camera off", vm.state.value.call!!.video)
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Camera on")).fetchSemanticsNodes().isNotEmpty() }
        shot("voice-call")
        assertEquals(null, vm.state.value.paywall)
    }

    @Test fun anIncomingVoiceCallSaysSo() {
        val vm = signIn()
        matched(vm)
        compose.runOnUiThread { vm.sendText("can you call me?") }
        waitFor("wants a voice call")
        shot("incoming-voice-call")
        click("Accept")
        compose.waitUntil(5_000) { vm.state.value.call != null }
    }

    @Test fun aFriendsThreadHasTheCallButton() {
        val vm = signIn()
        val p3 = FakeBackend.POOL.first { it.id == "p3" }
        compose.runOnUiThread { vm.answerFriend(p3, true) }
        compose.waitUntil(5_000) { vm.state.value.friends.any { it.profile.id == "p3" && it.state == com.yaari.chat.model.FriendState.FRIENDS } }
        compose.runOnUiThread { vm.openDm("p3") }
        compose.waitUntil(5_000) { vm.state.value.dm != null }
        tap("Call")
        click("Video call")
        compose.waitUntil(5_000) { vm.state.value.call?.partner?.id == "p3" }
    }

    @Test fun aFriendsCallRingsOverAnyScreenAndShowsInNotifications() {
        val vm = signIn()
        val p3 = FakeBackend.POOL.first { it.id == "p3" }
        compose.runOnUiThread { vm.answerFriend(p3, true) }
        compose.waitUntil(5_000) { vm.state.value.friendState("p3") == com.yaari.chat.model.FriendState.FRIENDS }
        kotlinx.coroutines.runBlocking { fake.friendCalls("p3", video = false) }
        waitFor("${p3.name} wants a voice call")
        shot("ring-over-home")
        click("Decline")
        compose.waitUntil(5_000) { vm.social.state.value.ring == null }
        compose.runOnUiThread { vm.social.openInbox() }
        waitFor("${p3.name} called you")
        shot("inbox-call-row")
    }

    @Test fun myProfileLooksLikeOthersAndEditingIsInEditProfile() {
        val vm = signIn()
        compose.runOnUiThread {
            vm.social.saveStyle(ProfileStyle(status = "Chai and long walks", prompts = listOf(ProfilePrompt(PromptQ.WEEKEND, "Marine Drive at sunset"))))
            vm.social.saveInterests(Interest.entries.take(3).toSet())
            vm.selectTab(Tab.ME)
        }
        waitFor("“Chai and long walks”")
        waitFor("Edit profile")
        assertFalse("no inline editing on my profile", has("Edit interests") || has("Add photo"))
        shot("my-profile")
        click("Edit profile")
        waitFor("Change photo")
        shot("edit-profile")
        compose.onNode(hasText("Edit interests")).performScrollTo()
        shot("edit-profile-interests-photos")
    }

    @Test fun anotherProfileShowsTheStatusApartFromTheVerifiedChip() {
        val vm = signIn()
        compose.runOnUiThread { vm.openProfile(FakeBackend.POOL.first { it.id == "p1" }) }
        waitFor("“Chai first, then everything else”")
        waitFor("ID verified")
        shot("other-profile")
    }

    @Test fun aPageTheRoomCantFrameOpensOutsideAndAFrameableOneCanBeOpenedTogether() {
        val vm = signIn()
        compose.runOnUiThread { vm.voice.create(NewVoiceRoom("Cricket night", RoomCategory.TALK)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.isHost == true && vm.voice.state.value?.lines != null }
        compose.runOnUiThread {
            vm.voice.send("Score: https://www.cricbuzz.com/live-cricket-scores")
            vm.voice.send("About chai https://en.wikipedia.org/wiki/Masala_chai")
        }
        waitFor("Cricbuzz")
        waitFor("Wikipedia")
        shot("room-link-cards")
        tap("Open Cricbuzz")
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.cricbuzz.com/live-cricket-scores", started?.dataString)
        assertFalse("not the in-app viewer", has("Open together"))
        tap("Open Wikipedia")
        waitFor("Open together")
        shot("room-link-open-together")
    }

    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    @Test fun reactionsFloatOverTheRoomOnATablet() {
        val vm = signIn()
        compose.runOnUiThread { vm.voice.create(NewVoiceRoom("Cricket night", RoomCategory.TALK)) }
        compose.waitUntil(5_000) { vm.voice.state.value?.isHost == true }
        val id = vm.voice.state.value!!.roomId
        compose.runOnUiThread { repeat(4) { fake.voice.react(id, vm.voice.state.value!!.room!!.meId, VoiceReaction.entries[it]) } }
        compose.waitUntil(5_000) { vm.voice.state.value!!.reactions.isNotEmpty() }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        shot("room-reactions-tablet")
        compose.mainClock.autoAdvance = true
    }
}

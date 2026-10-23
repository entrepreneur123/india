package com.yaari.chat

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomRules
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Shared steps of the rooms screenshots (0.7.0: one kind of room, as the web). */
private class RoomsShots(private val compose: androidx.compose.ui.test.junit4.ComposeContentTestRule, private val suffix: String = "") {
    val today: LocalDate = LocalDate.of(2026, 10, 18)
    val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    val vm = AppViewModel(backend, DemoBilling(), today = { today })

    fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/rooms_$name$suffix.png")
    }

    fun screen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/rooms_$name$suffix.png")
    }

    fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    fun signIn() {
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
        compose.runOnUiThread { vm.selectTab(Tab.ROOMS) }
        waitFor("Late night Bollywood songs")
    }

    fun open(roomId: String) {
        compose.runOnUiThread { vm.voice.open(roomId) }
        compose.waitUntil(5_000) { vm.voice.state.value?.room != null && vm.voice.state.value?.lines != null }
    }

    fun close() {
        compose.runOnUiThread { vm.voice.close() }
        compose.waitUntil(5_000) { vm.voice.state.value == null }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class RoomsScreensTest {
    @get:Rule val compose = createComposeRule()
    private val r = RoomsShots(compose)

    @Test fun listAndFilter() {
        r.signIn()
        r.shot("01_list")
        compose.onAllNodes(hasText("Music"))[0].performClick()
        compose.waitUntil(5_000) { r.vm.voice.list.value.filter == RoomCategory.MUSIC }
        r.shot("02_filter_music")
    }

    @Test fun talkRoomWithIcebreaker() {
        r.signIn()
        r.open("v-chai")
        compose.runOnUiThread { r.vm.voice.icebreaker() }
        compose.waitUntil(5_000) { r.vm.voice.state.value!!.lines!!.any { it is VoiceLine.Icebreaker && it.fromMe } }
        r.waitFor("Another question")
        r.shot("03_talk_icebreaker")
    }

    @Test fun photoBlurredThenRevealed() {
        r.signIn()
        r.open("v-chai")
        // The photo is a few lines up the chat (the talk room's top block takes about 30 % of the height).
        compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().indices.firstNotNullOf { i ->
            runCatching { compose.onAllNodes(hasScrollAction())[i].performScrollToNode(hasText("Tap to view")) }.getOrNull()
        }
        r.waitFor("Tap to view")
        compose.onAllNodes(hasText("Tap to view"))[0].performScrollTo()
        r.shot("04_photo_blurred")
        compose.onAllNodes(hasText("Tap to view"))[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Tap to view")).fetchSemanticsNodes().isEmpty() }
        r.shot("05_photo_revealed")
    }

    @Test fun musicHeroWithVotes() {
        r.signIn()
        r.open("v-bolly")
        r.waitFor("Up next")
        val third = r.vm.voice.state.value!!.room!!.queue.minByOrNull { it.votes }!!
        compose.runOnUiThread { r.vm.voice.vote(third.id, true) }
        compose.waitUntil(5_000) { r.vm.voice.state.value!!.room!!.queue.any { it.voted } }
        compose.onAllNodes(hasText(com.yaari.chat.model.YouTube.songOf(third.title)))[0].performScrollTo()
        r.shot("06_music_hero_votes")
    }

    @Test fun privateRoomShowsItsCode() {
        r.signIn()
        compose.runOnUiThread { r.vm.voice.create(NewVoiceRoom("Sunday cricket chat", RoomCategory.TALK, RoomRules(isPrivate = true), "Be kind. Hindi or English, both welcome.")) }
        compose.waitUntil(5_000) { r.vm.voice.list.value.created != null && r.vm.voice.state.value?.room != null }
        r.waitFor("Your room is open")
        r.screen("08_private_code")
        assertTrue(r.vm.voice.state.value!!.room!!.code == r.vm.voice.list.value.created!!.code)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-night-xxhdpi")
class RoomsScreensNightTest {
    @get:Rule val compose = createComposeRule()
    private val r = RoomsShots(compose, "_dark")

    @Test fun listAndCreateAtNight() {
        r.signIn()
        r.shot("01_list")
        compose.onAllNodes(hasText("Create room"))[0].performClick()
        r.waitFor("You’re the host")
        r.screen("09_create")
    }
}

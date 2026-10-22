package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.Links
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.YouTube
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
import com.yaari.chat.ui.VoiceController
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
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** 036_room_powers.sql: what the host and co-hosts may do, the room's typing, the shared page and links in chats. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPowersTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(backend: Backend = fake): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today }, wallClock = { now })
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)); advanceUntilIdle()
        vm.selectTab(Tab.ROOMS); advanceUntilIdle()
        return vm
    }

    private val AppViewModel.room get() = voice.state.value?.room
    private fun AppViewModel.notice(): UiText? = state.value.notice.also { consumeNotice() }

    @Test fun aCohostRunsTheMusicAndRemovesOnlyListenersAndSpeakers() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-bolly"); advanceUntilIdle()
        assertFalse(vm.room!!.canDj) // a listener: read-only music
        fake.voice.setRole("v-bolly", vm.room!!.meId, VoiceRole.COHOST); runCurrent()
        val room = vm.room!!
        assertTrue(room.canDj && room.canShare)

        vm.voice.act(VoiceAction.Pause); advanceUntilIdle()
        assertNotNull(vm.room!!.track!!.pausedAt)
        // Seeking a paused song: it stays paused at the new place.
        vm.voice.act(VoiceAction.Seek(42_000)); advanceUntilIdle()
        now += 10_000
        assertEquals(42f, vm.room!!.track!!.position(now), 0.001f)
        vm.voice.act(VoiceAction.Resume); advanceUntilIdle()
        now += 3_000
        assertEquals(45f, vm.room!!.track!!.position(now), 0.001f)

        val other = room.members.first { it.role == VoiceRole.COHOST && it.profile.id != room.meId }
        val host = room.member(room.hostId)!!
        val listener = room.audience.first { it.profile.id != room.meId }
        val speaker = room.members.first { it.role == VoiceRole.SPEAKER }
        // What the sheet offers: nothing against the host or another co-host, no co-host making.
        assertFalse(room.canKick(other) || room.canMoveToAudience(other) || room.canKick(host) || room.canSetCohost(listener))
        assertTrue(room.canKick(listener) && room.canMoveToAudience(speaker) && room.canInvite(listener))
        assertFalse(room.canMoveToAudience(listener)) // not on the stage

        // And the server's rules behind it.
        vm.voice.act(VoiceAction.Kick(other.profile.id)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
        vm.voice.act(VoiceAction.ToAudience(other.profile.id)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
        vm.voice.act(VoiceAction.Cohost(listener.profile.id, true)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
        vm.voice.act(VoiceAction.ToAudience(speaker.profile.id)); advanceUntilIdle()
        assertNull(vm.room!!.member(speaker.profile.id)!!.seat)
        vm.voice.act(VoiceAction.Kick(listener.profile.id)); advanceUntilIdle()
        assertNull(vm.room!!.member(listener.profile.id))
    }

    @Test fun theHostMakesCohostsAndRemovesThem() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Chai", RoomCategory.TALK)); advanceUntilIdle()
        val id = vm.voice.state.value!!.roomId
        fake.voice.raiseHands(id, 1); runCurrent()
        val p = vm.room!!.audience.first { it.hand }.profile.id
        vm.voice.act(VoiceAction.Cohost(p, true)); advanceUntilIdle()
        val co = vm.room!!.member(p)!!
        assertEquals(VoiceRole.COHOST, co.role)
        assertTrue(vm.room!!.canKick(co) && vm.room!!.canSetCohost(co))
        assertFalse(vm.room!!.canKick(vm.room!!.me!!)) // never myself
        vm.voice.act(VoiceAction.Kick(p)); advanceUntilIdle()
        assertNull(vm.room!!.member(p))
    }

    @Test fun listenersControlOnlyTheirOwnPhone() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-bolly"); advanceUntilIdle()
        vm.voice.act(VoiceAction.Seek(10_000)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
        vm.voice.act(VoiceAction.Pause); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
        vm.voice.shareLink("https://en.wikipedia.org/wiki/Chai", null); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
        assertNull(vm.room!!.stage)
    }

    @Test fun aSharedPageShowsForEveryoneAndClosesOnlyForMe() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Quiz", RoomCategory.TALK)); advanceUntilIdle()
        vm.voice.shareLink("http://example.com", null); advanceUntilIdle()
        assertEquals(UiText(R.string.link_not_shareable), vm.notice())

        vm.voice.shareLink("https://en.wikipedia.org/wiki/Chai", " Chai "); advanceUntilIdle()
        assertEquals(UiText(R.string.link_shared), vm.notice())
        val stage = vm.room!!.stage!!
        assertEquals(listOf("https://en.wikipedia.org/wiki/Chai", "Chai", "Dev"), listOf(stage.url, stage.title, stage.by))
        assertEquals(stage, vm.voice.state.value!!.shownStage)

        vm.voice.hideStage()
        assertNull(vm.voice.state.value!!.shownStage)
        assertNotNull(vm.room!!.stage) // still shared for everyone else
        now += 1_000
        vm.voice.shareLink("https://en.wikipedia.org/wiki/Chai", null); advanceUntilIdle()
        assertNotNull("shared again: it shows again", vm.voice.state.value!!.shownStage)

        vm.voice.stopSharing(); advanceUntilIdle()
        assertNull(vm.room!!.stage)
    }

    @Test fun someoneElsesSharedPageArrivesWithTheRoom() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-chai"); advanceUntilIdle()
        fake.voice.shareFromHost("v-chai", "https://www.cricbuzz.com/live", "Live score"); runCurrent()
        assertEquals("Meera", vm.voice.state.value!!.shownStage!!.by)
        assertFalse(vm.room!!.canShare)
    }

    @Test fun roomTypingShowsOthersForFourSecondsNeverMe() = runTest(dispatcher) {
        var sent = 0
        val counting = object : Backend by fake {
            override suspend fun sendVoiceTyping(roomId: String, name: String) {
                sent++
                assertEquals("Dev", name)
            }
        }
        val vm = signedIn(counting)
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val room = vm.room!!
        val (a, b) = room.members.filter { it.profile.id != room.meId }.take(2)

        fake.voice.typing("v-cricket", a.profile.id); runCurrent()
        assertEquals(listOf(a.profile.name), vm.voice.state.value!!.typers)
        advanceTimeBy(2_000); runCurrent()
        fake.voice.typing("v-cricket", b.profile.id); runCurrent()
        fake.voice.typing("v-cricket", room.meId); runCurrent() // my own signal (another tab) is never shown
        fake.voice.typing("v-cricket", "stranger", "Not here"); runCurrent() // not in the room
        assertEquals(listOf(a.profile.name, b.profile.name), vm.voice.state.value!!.typers)
        advanceTimeBy(2_100); runCurrent()
        assertEquals(listOf(b.profile.name), vm.voice.state.value!!.typers) // a's 4 s are up
        // b's line arrives: b stopped typing.
        fake.voice.say("v-cricket", b.profile.id, "hello"); runCurrent()
        assertTrue(vm.voice.state.value!!.typing.isEmpty())

        // Mine goes out at most every 2.5 s.
        vm.voice.typing(); vm.voice.typing(); advanceUntilIdle()
        assertEquals(1, sent)
        now += 2_500
        vm.voice.typing(); advanceUntilIdle()
        assertEquals(2, sent)
    }

    @Test fun linksInMessagesAreOnlyWebAddresses() {
        val text = "see https://en.wikipedia.org/wiki/Foo_(bar), and http://x.in/a?b=1. Not javascript:alert(1) or ftp://f.com"
        assertEquals(listOf("https://en.wikipedia.org/wiki/Foo_(bar)", "http://x.in/a?b=1"), Links.find(text).map { it.url })
        val first = Links.find(text).first()
        assertEquals(text.indexOf("https"), first.start)
        assertEquals("(https://example.com)", "(" + Links.find("(https://example.com)").single().url + ")")
        assertFalse(Links.isWeb("javascript:alert(1)"))
        assertFalse(Links.isWeb("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(Links.isWeb("https://user:pw@example.com"))
        assertTrue(Links.shareable("https://example.com/a"))
        assertFalse(Links.shareable("http://example.com/a"))
        assertFalse(Links.shareable("https://localhost/a"))
        assertFalse(Links.shareable("https://example.com/" + "a".repeat(500)))
        // YouTube videos keep their own card; the first other page gets a preview.
        assertEquals("https://example.com", Links.firstPreviewable("https://youtu.be/BddP6PYo2gs https://example.com"))
        assertNotNull(YouTube.videoId(Links.find("listen https://youtu.be/BddP6PYo2gs!").single().url))
    }

    @Test fun typingConstantsMatchRandomChat() {
        assertEquals(4_000L, VoiceController.TYPING_SHOWN_MS)
        assertEquals(2_500L, VoiceController.TYPING_EVERY_MS)
    }
}

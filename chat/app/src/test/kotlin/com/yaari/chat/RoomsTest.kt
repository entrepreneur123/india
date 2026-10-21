package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.Nsfw
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.AudioStatus
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomRules
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceAudioState
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceMember
import com.yaari.chat.model.VoiceQueueItem
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.VoiceSeat
import com.yaari.chat.model.VoiceTrack
import com.yaari.chat.model.YouTube
import com.yaari.chat.model.byVotes
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
import com.yaari.chat.ui.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate

/**
 * One kind of room (019_unified_rooms.sql) against the FakeBackend, which applies the server's rules: categories and
 * the filter, the create sheet's rules, codes, the host off the stage, icebreakers (one per room every 20 s), song
 * requests by vote, photos checked on the phone, and audio only while someone is on the stage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomsTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(
        backend: Backend = fake, photos: PhotoCheck = PhotoCheck.None, gender: Gender = Gender.MALE,
    ): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today }, photos = photos)
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = "Dev", gender = gender, day = "1", month = "1", year = "2000", agreed = true)); advanceUntilIdle()
        vm.selectTab(Tab.ROOMS); advanceUntilIdle()
        return vm
    }

    private val AppViewModel.s get() = voice.state.value!!
    private val AppViewModel.room get() = voice.state.value!!.room!!
    private fun AppViewModel.notice(): UiText? = state.value.notice.also { consumeNotice() }

    @Test fun theListHasTheWebsDemoRoomsAndFiltersByCategory() = runTest(dispatcher) {
        val vm = signedIn()
        val list = vm.voice.list.value
        assertEquals(setOf("v-bolly", "v-chai", "v-cricket", "v-adda", "v-lofi", "v-gym"), list.rooms!!.map { it.id }.toSet())
        assertEquals(RoomCategory.TALK, list.rooms!!.first { it.id == "v-chai" }.category)
        assertTrue("nobody on the chai stage: a chat room", list.rooms!!.first { it.id == "v-chai" }.speakers.isEmpty())
        assertEquals(RoomRules(30, GenderRule.MALE, 18, 30), list.rooms!!.first { it.id == "v-gym" }.rules)
        vm.voice.filter(RoomCategory.MUSIC)
        assertEquals(setOf("v-bolly", "v-lofi"), vm.voice.list.value.shown!!.map { it.id }.toSet())
        vm.voice.filter(null)
        assertEquals(6, vm.voice.list.value.shown!!.size)
        // Rooms are talk or music: games (029), categories from before 019 and anything unknown are talk.
        assertEquals(listOf(RoomCategory.TALK, RoomCategory.MUSIC), RoomCategory.entries.toList())
        assertEquals(RoomCategory.TALK, RoomCategory.of("games"))
        assertEquals(RoomCategory.TALK, RoomCategory.of("gaming"))
        assertEquals(RoomCategory.TALK, RoomCategory.of("night"))
        assertEquals(RoomCategory.TALK, RoomCategory.of(null))
        assertEquals(RoomCategory.MUSIC, RoomCategory.of("music"))
    }

    @Test fun aRoomThatRefusesMeSaysWhyInsteadOfOpening() = runTest(dispatcher) {
        val vm = signedIn(gender = Gender.FEMALE)
        val gym = vm.voice.list.value.rooms!!.first { it.id == "v-gym" }
        assertEquals("room_gender", gym.refusal)
        vm.voice.enter(gym); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_men), vm.notice())
        assertNull(vm.voice.state.value)
    }

    @Test fun createChecksTheRulesAndTheHostStartsOffTheStage() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Cricket", RoomCategory.TALK, RoomRules(maxMembers = 1))); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_size), vm.notice())
        vm.voice.create(NewVoiceRoom("Cricket", RoomCategory.TALK, RoomRules(maxMembers = 201))); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_size), vm.notice())
        vm.voice.create(NewVoiceRoom("Cricket", RoomCategory.TALK, RoomRules(minAge = 40, maxAge = 30))); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_settings), vm.notice())
        vm.voice.create(NewVoiceRoom("Cricket", RoomCategory.TALK, announcement = "x".repeat(201))); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_settings), vm.notice())
        assertNull(vm.voice.state.value)

        vm.voice.create(NewVoiceRoom(" Late chai ", RoomCategory.TALK, RoomRules(maxMembers = 12, isPrivate = true), "  Be kind  ")); advanceUntilIdle()
        val created = vm.voice.list.value.created!!
        assertEquals("Late chai", created.title)
        assertTrue(Regex("[A-HJ-NP-Z2-9]{6}").matches(created.code))
        assertEquals(created.code, vm.room.code) // the host sees the code
        assertEquals("Be kind", vm.room.announcement)
        assertEquals(12, vm.room.rules.maxMembers)
        assertEquals(VoiceRole.HOST, vm.s.me!!.role)
        assertNull("the host is not on the stage when the room opens", vm.s.me!!.seat)
        assertFalse(vm.room.stageLive)
        vm.voice.dismissCreated()
        assertNull(vm.voice.list.value.created)

        // "Join voice" takes seat 0; the host can leave the stage again (the room goes on as a chat room).
        vm.voice.joinVoice(); advanceUntilIdle()
        assertEquals(0, vm.s.me!!.seat)
        assertTrue(vm.room.stageLive)
        vm.voice.act(VoiceAction.Unseat); advanceUntilIdle()
        assertNull(vm.s.me!!.seat)
        assertFalse(vm.room.stageLive)

        // Settings: the host edits the title and the pinned announcement (empty removes it).
        vm.voice.saveSettings("Late chai club", " "); advanceUntilIdle()
        assertEquals(UiText(R.string.room_saved), vm.notice())
        assertEquals("Late chai club" to null, vm.room.title to vm.room.announcement)
        vm.voice.saveSettings("x", ""); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_title), vm.notice())
    }

    @Test fun codesFindRoomsAndWrongOnesSaySo() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.joinCode("ab1"); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_code), vm.notice())
        vm.voice.joinCode("ZZZZZZ"); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_code_unknown), vm.notice())
        vm.voice.create(NewVoiceRoom("Secret", RoomCategory.MUSIC, RoomRules(isPrivate = true))); advanceUntilIdle()
        val code = vm.voice.list.value.created!!.code
        vm.voice.close(); advanceUntilIdle()
        var done = false
        vm.voice.joinCode(code.lowercase()) { done = true }; advanceUntilIdle()
        assertTrue(done)
        assertEquals("Secret", vm.room.title)
    }

    @Test fun listenersJoinVoiceByRaisingAHandUnlessTheRoomIsOpen() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-chai"); advanceUntilIdle() // approval
        assertFalse(VoiceController.joinsDirectly(vm.room))
        vm.voice.joinVoice(); advanceTimeBy(1_000); runCurrent() // before the demo host brings me up
        assertTrue(vm.s.me!!.hand)
        assertEquals(UiText(R.string.voice_hand_sent), vm.notice())
        vm.voice.close(); advanceUntilIdle()
        vm.voice.open("v-lofi"); advanceUntilIdle() // open seats
        assertTrue(VoiceController.joinsDirectly(vm.room))
        val free = vm.room.firstFreeSeat()
        vm.voice.joinVoice(); advanceUntilIdle()
        assertEquals(free, vm.s.me!!.seat)
    }

    @Test fun icebreakersAreOnePerRoomEveryTwentySeconds() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-chai"); advanceUntilIdle()
        vm.voice.icebreaker(); advanceUntilIdle()
        val card = vm.s.lines!!.filterIsInstance<VoiceLine.Icebreaker>().last()
        assertTrue(card.fromMe && card.prompt in 1..VoiceRoom.PROMPTS)
        assertNull(vm.notice())
        now += 5_000
        vm.voice.icebreaker(); advanceUntilIdle()
        assertEquals("the rate limit is said kindly", UiText(R.string.ib_wait), vm.notice())
        assertEquals(1, vm.s.lines!!.count { it is VoiceLine.Icebreaker && it.fromMe })
        now += 15_000
        vm.voice.icebreaker(); advanceUntilIdle()
        assertEquals(2, vm.s.lines!!.count { it is VoiceLine.Icebreaker && it.fromMe })
        // Every prompt number has its text; an unknown one (a newer server) shows the first.
        assertEquals(R.string.ib_40, VoiceController.prompt(40))
        assertEquals(R.string.ib_1, VoiceController.prompt(99))
    }

    @Test fun songRequestsPlayByVotes() = runTest(dispatcher) {
        // Most voted first, ties in the order they were added.
        val q = listOf(VoiceQueueItem("a", "v", "", "", 2), VoiceQueueItem("b", "v", "", "", 5), VoiceQueueItem("c", "v", "", "", 2))
        assertEquals(listOf("b", "a", "c"), q.byVotes().map { it.id })
        assertEquals("Kesariya – Brahmāstra" to "Arijit Singh", YouTube.songOf("Kesariya – Brahmāstra · Arijit Singh") to YouTube.artistOf("Kesariya – Brahmāstra · Arijit Singh"))
        assertEquals("Song" to "", YouTube.songOf("Song") to YouTube.artistOf("Song"))

        val vm = signedIn()
        vm.voice.open("v-bolly"); advanceUntilIdle()
        assertEquals(listOf(7, 4, 2), vm.room.queue.byVotes().map { it.votes })
        val last = vm.room.queue.byVotes().last()
        vm.voice.vote(last.id, true); advanceUntilIdle()
        assertEquals(3 to true, vm.room.queue.first { it.id == last.id }.let { it.votes to it.voted })
        vm.voice.vote(last.id, true); advanceUntilIdle() // one vote per person
        assertEquals(3, vm.room.queue.first { it.id == last.id }.votes)
        vm.voice.vote(last.id, false); advanceUntilIdle()
        assertEquals(2 to false, vm.room.queue.first { it.id == last.id }.let { it.votes to it.voted })

        // A listener's request goes straight into the queue with its real title; 3 waiting per person.
        vm.voice.addSong("g0eO74UmRBs"); advanceUntilIdle()
        assertEquals(UiText(R.string.voice_suggested), vm.notice())
        assertEquals("Kal Ho Naa Ho – Title Track · Sonu Nigam", vm.room.queue.first { it.videoId == "g0eO74UmRBs" }.title)
        assertNull(vm.s.adding)
        vm.voice.addSong("Umqb9KENgmk"); advanceUntilIdle()
        vm.voice.addSong("284Ov7ysmfA"); advanceUntilIdle()
        vm.notice()
        var added: Boolean? = null
        vm.voice.addSong("VAdGW7QDJiU") { added = it }; advanceUntilIdle()
        assertEquals(false, added)
        assertEquals(UiText(R.string.err_slow_down), vm.notice())
        vm.voice.act(VoiceAction.Skip()); advanceUntilIdle()
        assertEquals("only the host plays next", UiText(R.string.err_not_allowed), vm.notice())
    }

    @Test fun theHostsNextPlaysTheMostVoted() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("DJ night", RoomCategory.MUSIC)); advanceUntilIdle()
        vm.voice.addSong("BddP6PYo2gs"); advanceUntilIdle() // plays at once
        vm.voice.addSong("Umqb9KENgmk"); advanceUntilIdle()
        vm.voice.addSong("284Ov7ysmfA"); advanceUntilIdle()
        val later = vm.room.queue.first { it.videoId == "284Ov7ysmfA" }
        vm.voice.vote(later.id, true); advanceUntilIdle()
        assertEquals("284Ov7ysmfA", vm.room.queue.byVotes().first().videoId)
        vm.voice.act(VoiceAction.Skip(vm.room.track!!.id)); advanceUntilIdle()
        assertEquals("284Ov7ysmfA", vm.room.track!!.videoId)
        assertEquals(listOf("Umqb9KENgmk"), vm.room.queue.map { it.videoId })
    }

    private class FakePhotos(var verdict: () -> Boolean) : PhotoCheck {
        val shrunk = mutableListOf<String>()
        override suspend fun shrink(uri: String) = "$uri.small.jpg".also { shrunk += it }
        override suspend fun nude(jpegUri: String) = verdict()
    }

    @Test fun photosAreCheckedOnThePhoneAndANudeOneIsNotSent() = runTest(dispatcher) {
        val photos = FakePhotos { true }
        var sent = 0
        val counting = object : Backend by fake {
            override suspend fun sendVoicePhoto(roomId: String, jpegUri: String): VoiceLine { sent++; return fake.sendVoicePhoto(roomId, jpegUri) }
        }
        val vm = signedIn(counting, photos)
        vm.voice.open("v-chai"); advanceUntilIdle()

        vm.voice.sendPhoto("content://pick/1"); advanceUntilIdle()
        assertEquals(UiText(R.string.err_nude_photo), vm.notice())
        assertEquals("nothing was uploaded", 0, sent)
        assertFalse(vm.s.photoBusy)

        photos.verdict = { throw IllegalStateException("no model") }
        vm.voice.sendPhoto("content://pick/2"); advanceUntilIdle()
        assertEquals("a photo that can't be checked isn't sent", UiText(R.string.err_network), vm.notice())
        assertEquals(0, sent)

        photos.verdict = { false }
        vm.voice.sendPhoto("content://pick/3"); advanceUntilIdle()
        assertNull(vm.notice())
        assertEquals(1, sent)
        val photo = vm.s.lines!!.filterIsInstance<VoiceLine.Photo>().last()
        assertTrue(photo.fromMe)
        assertEquals("the shrunk JPEG is what's sent", "content://pick/3.small.jpg", photo.url)
        assertFalse(vm.s.photoBusy)
    }

    @Test fun photoVerdictLooksAtTheWholePictureThenItsCentre() = runTest(dispatcher) {
        fun scores(porn: Float, hentai: Float) = FloatArray(Nsfw.CLASSES).also { it[Nsfw.PORN] = porn; it[Nsfw.HENTAI] = hentai }
        var centreLooked = false
        assertTrue(Nsfw.isNudePhoto({ scores(0.5f, 0.2f) }, { centreLooked = true; scores(0f, 0f) }))
        assertFalse("clean whole picture: the centre is looked at too", centreLooked)
        assertTrue(Nsfw.isNudePhoto({ scores(0.1f, 0f) }, { scores(0.7f, 0f) }))
        assertFalse(Nsfw.isNudePhoto({ scores(0.3f, 0.2f) }, { scores(0.4f, 0.1f) }))
        // ARGB pixels become RGB in [0, 1].
        val out = ByteBuffer.allocateDirect(2 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        Nsfw.argbToInput(intArrayOf(0xFFFF8000.toInt(), 0xFF0000FF.toInt()), out)
        val got = FloatArray(6).also { out.get(it) }
        assertEquals(listOf(1f, 128 / 255f, 0f, 0f, 0f, 1f), got.toList())
    }

    @Test fun audioOnlyWhileSomeoneIsOnTheStage() = runTest(dispatcher) {
        // The room as the server sends it: an empty stage, then someone on it, then empty again.
        val me = Profile("me", "Dev", Gender.MALE, 26, "", "")
        val other = Profile("o", "Asha", Gender.FEMALE, 24, "", "")
        fun room(seat: Int?, track: VoiceTrack? = null) = VoiceRoom(
            "r", "Chai", RoomCategory.TALK, SpeakMode.APPROVAL, "o", "me",
            listOf(VoiceMember(me, VoiceRole.LISTENER, null, true, false), VoiceMember(other, VoiceRole.HOST, seat, true, false)),
            List(VoiceRoom.SEATS) { VoiceSeat(it, false) }, track, emptyList(),
        )
        assertFalse(room(null).stageLive)
        assertTrue(room(0).stageLive)

        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val scripted = object : Backend by fake {
            override fun voiceSession(roomId: String): Flow<VoiceEvent> = events.receiveAsFlow()
            override suspend fun voiceLines(roomId: String) = emptyList<VoiceLine>()
        }
        val vm = signedIn(scripted)
        vm.voice.open("r")
        events.send(VoiceEvent.RoomChanged(room(null))); advanceUntilIdle()
        assertNull(vm.s.audio)
        assertFalse("a chat room keeps nothing alive", vm.s.keepAlive)
        events.send(VoiceEvent.RoomChanged(room(0)))
        events.send(VoiceEvent.Audio(VoiceAudioState(AudioStatus.CONNECTED))); advanceUntilIdle()
        assertTrue("connected audio keeps the service up", vm.s.keepAlive)
        events.send(VoiceEvent.RoomChanged(room(null)))
        events.send(VoiceEvent.Audio(null)); advanceUntilIdle()
        assertNull(vm.s.audio)
        assertFalse(vm.s.keepAlive)
        events.send(VoiceEvent.RoomChanged(room(null, VoiceTrack("t", "BddP6PYo2gs", "", "Asha", now)))); advanceUntilIdle()
        assertTrue("music playing keeps it up", vm.s.keepAlive)
        events.send(VoiceEvent.RoomChanged(room(null, VoiceTrack("t", "BddP6PYo2gs", "", "Asha", now, pausedAt = now)))); advanceUntilIdle()
        assertFalse("paused music doesn't", vm.s.keepAlive)
        vm.voice.close(); advanceUntilIdle()
    }

    @Test fun theCreateSheetsSizeSteps() {
        assertEquals(3, VoiceController.sizeUp(2))
        assertEquals(15, VoiceController.sizeUp(10))
        assertEquals(60, VoiceController.sizeUp(50))
        assertEquals(200, VoiceController.sizeUp(200))
        assertEquals(2, VoiceController.sizeDown(2))
        assertEquals(9, VoiceController.sizeDown(10))
        assertEquals(45, VoiceController.sizeDown(50))
        assertEquals(50, VoiceController.sizeDown(60))
    }
}

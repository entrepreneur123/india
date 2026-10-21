package com.yaari.chat

import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.ServerClock
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceReaction
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceSystemEvent
import com.yaari.chat.model.VoiceTrack
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

/** Voice rooms against the FakeBackend, which applies the server's rules (017_voice_rooms.sql). */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val today = LocalDate.of(2026, 10, 18)
    // No ambient simulation: the tests drive the room through the FakeVoice hooks.
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(name: String = "Dev"): AppViewModel {
        val vm = AppViewModel(fake, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = name, gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)); advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        vm.selectTab(Tab.ROOMS); advanceUntilIdle()
        return vm
    }

    private val AppViewModel.room get() = voice.state.value?.room
    private val AppViewModel.me get() = voice.state.value?.me
    private fun AppViewModel.notice(): UiText? = state.value.notice.also { consumeNotice() }

    @Test fun listShowsLiveRoomsAndCreatingOpensMineAsHost() = runTest(dispatcher) {
        val vm = signedIn()
        val list = vm.voice.list.value.rooms!!
        assertEquals(setOf("v-bolly", "v-chai", "v-cricket", "v-lofi", "v-ludo", "v-gym"), list.map { it.id }.toSet())
        assertEquals(list.sortedByDescending { it.members }, list) // the busiest first
        assertEquals("Kesariya – Brahmāstra · Arijit Singh", list.first { it.id == "v-bolly" }.nowPlaying)
        assertTrue(list.all { it.speakers.size <= 4 && (it.speakers.isEmpty() || it.speakers.first().id == it.host.id) })

        vm.voice.create(NewVoiceRoom(" x ", RoomCategory.MUSIC)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_room_title), vm.notice())
        assertNull(vm.voice.state.value)

        var closed = false
        vm.voice.create(NewVoiceRoom("  Sunday chai and gossip ", RoomCategory.MUSIC)) { closed = true }; advanceUntilIdle()
        assertTrue(closed)
        val s = vm.voice.state.value!!
        assertEquals("Sunday chai and gossip", s.room!!.title)
        assertTrue(s.expanded && s.isHost)
        assertNull("the host starts off the stage", s.me!!.seat)
        assertEquals(s.roomId, vm.voice.list.value.rooms!!.first().id) // mine first
        assertTrue(vm.voice.list.value.rooms!!.first().mine)
    }

    @Test fun listenerJoinsSeesChatAndLeaves() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val me = vm.me!!
        assertEquals(VoiceRole.LISTENER, me.role)
        assertNull(me.seat)
        assertTrue(me.muted)
        val lines = vm.voice.state.value!!.lines!!
        assertTrue(lines.any { it is VoiceLine.Text })
        // My own "joined" line is there, written for everyone in their language.
        val joined = lines.filterIsInstance<VoiceLine.System>().last()
        assertEquals(VoiceSystemEvent.JOINED to "Dev", joined.event to joined.name)
        val listening = vm.voice.list.value.rooms!!.first { it.id == "v-cricket" }.listeners

        vm.voice.minimise()
        assertFalse(vm.voice.state.value!!.expanded) // still in the room: the pill
        vm.voice.expand()
        vm.voice.leave(); advanceUntilIdle()
        assertNull(vm.voice.state.value)
        assertEquals(listening, vm.voice.list.value.rooms!!.first { it.id == "v-cricket" }.listeners)
    }

    @Test fun raisedHandIsBroughtUpMutedThenTalksAndStepsDown() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        vm.voice.hand(true); runCurrent(); advanceTimeBy(200); runCurrent()
        assertEquals(UiText(R.string.voice_hand_sent), vm.notice())
        assertTrue(vm.me!!.hand)

        // The demo host brings me up a few seconds later: on a seat, muted, told so.
        advanceTimeBy(6_500); runCurrent()
        val me = vm.me!!
        assertNotNull(me.seat)
        assertEquals(VoiceRole.SPEAKER, me.role)
        assertTrue(me.muted)
        assertFalse(me.hand)
        assertEquals(UiText(R.string.voice_on_stage), vm.notice())

        vm.voice.act(VoiceAction.Mic(true)); advanceUntilIdle()
        assertFalse(vm.me!!.muted)
        // On the stage, a hand can't be raised (the server's rule).
        vm.voice.act(VoiceAction.Hand(true)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())

        vm.voice.act(VoiceAction.Unseat); advanceUntilIdle()
        assertNull(vm.me!!.seat)
        assertEquals(VoiceRole.LISTENER, vm.me!!.role)
        assertEquals(VoiceSystemEvent.AUDIENCE, (vm.voice.state.value!!.lines!!.last() as VoiceLine.System).event)
    }

    @Test fun tappingSeatsFollowsTheRoomsMode() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        // Seat 8 is locked in the hand-raise rooms; an empty seat raises my hand there.
        vm.voice.tapSeat(8) { error("listeners don't manage seats") }; runCurrent()
        assertEquals(UiText(R.string.voice_seat_locked, 8), vm.notice())
        vm.voice.tapSeat(7) { error("listeners don't manage seats") }; advanceTimeBy(300); runCurrent()
        assertTrue(vm.me!!.hand)
        vm.voice.close(); advanceUntilIdle()

        // An open room: the free seat is mine at once.
        vm.voice.open("v-lofi"); advanceUntilIdle()
        vm.voice.tapSeat(5) { error("listeners don't manage seats") }; advanceUntilIdle()
        assertEquals(5, vm.me!!.seat)
    }

    @Test fun hostAnswersRequestsAndManagesThePeopleOnStage() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Chai", RoomCategory.TALK)); advanceUntilIdle()
        val id = vm.voice.state.value!!.roomId
        fake.voice.raiseHands(id, 2); runCurrent()
        assertEquals(2, vm.voice.state.value!!.hands)
        val (first, second) = vm.room!!.audience.filter { it.hand }

        vm.voice.act(VoiceAction.Answer(first.profile.id, true)); advanceTimeBy(300); runCurrent()
        assertEquals(1, vm.room!!.member(first.profile.id)!!.seat)
        assertEquals(VoiceSystemEvent.SEAT to 1, (vm.voice.state.value!!.lines!!.last() as VoiceLine.System).let { it.event to it.seat })
        vm.voice.act(VoiceAction.Answer(second.profile.id, false)); advanceUntilIdle()
        assertFalse(vm.room!!.member(second.profile.id)!!.hand)
        assertEquals(0, vm.voice.state.value!!.hands) // none left

        // A host's tools: co-host, mute, back to the audience, lock a seat, remove.
        val p = first.profile.id
        vm.voice.act(VoiceAction.Cohost(p, true)); advanceUntilIdle()
        assertEquals(VoiceRole.COHOST, vm.room!!.member(p)!!.role)
        vm.voice.act(VoiceAction.Mute(p)); advanceUntilIdle()
        assertTrue(vm.room!!.member(p)!!.muted)
        vm.voice.act(VoiceAction.ToAudience(p)); advanceUntilIdle()
        assertNull(vm.room!!.member(p)!!.seat)
        vm.voice.act(VoiceAction.Lock(3, true)); advanceUntilIdle()
        assertTrue(vm.room!!.locked(3))
        assertEquals(7, vm.room!!.freeSeats)
        vm.voice.act(VoiceAction.Kick(p)); advanceUntilIdle()
        assertNull(vm.room!!.member(p))
        assertEquals(VoiceSystemEvent.REMOVED, (vm.voice.state.value!!.lines!!.last() as VoiceLine.System).event)
        // The host can't be moved off the host seat, and can't remove themselves.
        vm.voice.act(VoiceAction.Kick(vm.room!!.meId)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
    }

    @Test fun aFullStageRefusesAnotherSpeaker() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Full", RoomCategory.TALK)); advanceUntilIdle()
        val id = vm.voice.state.value!!.roomId
        for (seat in 1..7) vm.voice.act(VoiceAction.Lock(seat, true))
        advanceUntilIdle()
        fake.voice.raiseHands(id, 2); runCurrent()
        val (a, b) = vm.room!!.audience.filter { it.hand }
        vm.voice.act(VoiceAction.Answer(a.profile.id, true)); advanceUntilIdle()
        assertEquals(0, vm.room!!.freeSeats)
        vm.voice.act(VoiceAction.Answer(b.profile.id, true)); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_allowed), vm.notice())
        assertNull(vm.room!!.member(b.profile.id)!!.seat)
    }

    @Test fun listenersCantUseHostTools() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val speaker = vm.room!!.members.first { it.seat == 2 }.profile.id
        listOf(VoiceAction.Mute(speaker), VoiceAction.Kick(speaker), VoiceAction.Cohost(speaker, true), VoiceAction.Lock(7, true), VoiceAction.Pause).forEach {
            vm.voice.act(it); advanceUntilIdle()
            assertEquals(it.toString(), UiText(R.string.err_not_allowed), vm.notice())
        }
    }

    @Test fun theHostsLeaveAsksToEndTheRoomForEveryone() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Night owls", RoomCategory.TALK)); advanceUntilIdle()
        vm.voice.minimise()
        vm.voice.leave()
        val s = vm.voice.state.value!!
        assertTrue(s.confirmEnd && s.expanded) // the confirm shows over the room
        vm.voice.dismissEnd()
        assertFalse(vm.voice.state.value!!.confirmEnd)
        vm.voice.leave(); vm.voice.end(); advanceUntilIdle()
        assertNull(vm.voice.state.value)
        assertEquals(UiText(R.string.voice_ended_by_you), vm.notice())
        assertTrue(vm.voice.list.value.rooms!!.none { it.title == "Night owls" })
    }

    @Test fun endedOrRemovedRoomsCloseWithAWord() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-lofi"); advanceUntilIdle()
        fake.voice.hostEnds("v-lofi"); advanceUntilIdle()
        assertNull(vm.voice.state.value)
        assertEquals(UiText(R.string.voice_ended), vm.notice())
        vm.voice.open("v-lofi"); advanceUntilIdle()
        assertNull(vm.voice.state.value) // can't enter an ended room
        assertEquals(UiText(R.string.err_room_closed), vm.notice())

        vm.voice.open("v-cricket"); advanceUntilIdle()
        fake.voice.kickMe("v-cricket"); advanceUntilIdle()
        assertNull(vm.voice.state.value)
        assertEquals(UiText(R.string.voice_removed), vm.notice())
    }

    @Test fun reactionsFloatForAMomentAndStayFew() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val host = vm.room!!.hostId
        fake.voice.react("v-cricket", host, VoiceReaction.FIRE); runCurrent()
        val r = vm.voice.state.value!!.reactions.single()
        assertEquals(host to VoiceReaction.FIRE, r.userId to r.kind)
        assertTrue(r.x in 0.12f..0.88f)
        repeat(20) { fake.voice.react("v-cricket", host, VoiceReaction.HEART) }
        runCurrent()
        assertEquals(VoiceController.MAX_REACTIONS, vm.voice.state.value!!.reactions.size)
        advanceTimeBy(VoiceController.REACTION_MS + 10); runCurrent()
        assertTrue(vm.voice.state.value!!.reactions.isEmpty())
        // Mine come back through the room like everyone else's.
        vm.voice.act(VoiceAction.React(VoiceReaction.CLAP)); runCurrent()
        assertEquals(vm.room!!.meId, vm.voice.state.value!!.reactions.single().userId)
    }

    @Test fun levelsLeadThePill() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val (a, b) = vm.room!!.members.filter { it.seat != null }.take(2).map { it.profile.id }
        fake.voice.speak("v-cricket", mapOf(a to 0.3f, b to 0.8f)); runCurrent()
        assertEquals(b, VoiceController.loudest(vm.voice.levels.value))
        fake.voice.speak("v-cricket", mapOf(a to 0.05f)); runCurrent()
        assertNull("a whisper is not speaking", VoiceController.loudest(vm.voice.levels.value))
        vm.voice.close(); advanceUntilIdle()
        assertTrue(vm.voice.levels.value.isEmpty())
    }

    @Test fun chatSendsMergesAndForgetsReportedPeople() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        vm.voice.send("  hello room  "); advanceUntilIdle()
        val mine = vm.voice.state.value!!.lines!!.filterIsInstance<VoiceLine.Text>().last()
        assertTrue(mine.fromMe)
        assertEquals("hello room", mine.text)
        // The same line arriving again (send reply + realtime) shows once.
        assertEquals(1, vm.voice.state.value!!.lines!!.count { it.id == mine.id })

        val other = vm.room!!.members.first { it.seat == 1 }.profile
        fake.voice.say("v-cricket", other.id, "kya baat hai"); runCurrent()
        assertTrue(vm.voice.state.value!!.lines!!.any { it is VoiceLine.Text && it.sender.id == other.id })
        vm.report(other.id, com.yaari.chat.model.ReportReason.HARASSMENT); advanceUntilIdle()
        assertTrue(vm.voice.state.value!!.lines!!.none { it is VoiceLine.Text && it.sender.id == other.id })

        // Five lines in five seconds, then slow down (the server's rate limit).
        repeat(5) { vm.voice.send("x$it"); advanceUntilIdle() }
        assertEquals(UiText(R.string.err_slow_down), vm.notice())
    }

    @Test fun linesKeepTheNewest300InOrder() {
        val p = Profile("p", "P", Gender.FEMALE, 20, "", "")
        val old = (1L..300L).map { VoiceLine.Text(it, p, false, "$it", it) }
        val merged = VoiceController.merge(old, listOf(VoiceLine.System(302, VoiceSystemEvent.JOINED, "A", 302), VoiceLine.Text(301, p, false, "301", 301), old[5]))
        assertEquals(VoiceController.MAX_LINES, merged.size)
        assertEquals((3L..302L).toList(), merged.map { it.id })
        assertTrue(VoiceController.merge(old, listOf(old[0])) === old)
    }

    @Test fun musicFollowsTheHost() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("DJ night", RoomCategory.MUSIC)); advanceUntilIdle()
        vm.voice.act(VoiceAction.AddTrack("BddP6PYo2gs")); advanceUntilIdle()
        val playing = vm.room!!.track!!
        assertEquals("Kesariya – Brahmāstra · Arijit Singh" to now, playing.title to playing.startedAt)
        assertEquals(VoiceSystemEvent.TRACK, (vm.voice.state.value!!.lines!!.last() as VoiceLine.System).event)
        vm.voice.act(VoiceAction.AddTrack("Umqb9KENgmk")); advanceUntilIdle()
        assertEquals(listOf("Umqb9KENgmk"), vm.room!!.queue.map { it.videoId })

        now += 30_000
        vm.voice.act(VoiceAction.Pause); advanceUntilIdle()
        now += 60_000 // paused: the position holds
        assertEquals(30f, vm.room!!.track!!.position(now), 0.001f)
        vm.voice.act(VoiceAction.Resume); advanceUntilIdle()
        now += 5_000
        assertEquals(35f, vm.room!!.track!!.position(now), 0.001f)

        // The track that ended moves on once; a second "ended" for it does nothing.
        val ended = vm.room!!.track!!.id
        vm.voice.act(VoiceAction.Skip(ended)); advanceUntilIdle()
        assertEquals("Umqb9KENgmk", vm.room!!.track!!.videoId)
        vm.voice.act(VoiceAction.Skip(ended)); advanceUntilIdle()
        assertEquals("Umqb9KENgmk", vm.room!!.track!!.videoId)
    }

    @Test fun positionAndDriftMath() {
        val t = VoiceTrack("t", "BddP6PYo2gs", "", "Priya", startedAt = 10_000L)
        assertEquals(0f, t.position(5_000L), 0f) // a clock behind the start never plays backwards
        assertEquals(74f, t.position(84_000L), 0.001f)
        assertEquals(20f, t.copy(pausedAt = 30_000L).position(999_999L), 0.001f)
        // In step within 2.5 s; further off, jump to the room's position.
        assertNull(VoiceController.seekTarget(72f, t, 84_000L))
        assertNull(VoiceController.seekTarget(76.4f, t, 84_000L))
        assertEquals(74f, VoiceController.seekTarget(70f, t, 84_000L)!!, 0.001f)
        assertEquals(74f, VoiceController.seekTarget(80f, t, 84_000L)!!, 0.001f)
    }

    @Test fun serverClockOffsetUsesTheQuickestReply() {
        val c = ServerClock()
        // Phone 5 s behind the server; 200 ms round trip.
        c.note(serverNow = 105_100L, sent = 100_000L, received = 100_200L)
        assertEquals(5_000L, c.offset)
        // A slow reply (2 s) is not trusted over the quick one.
        c.note(serverNow = 200_000L, sent = 150_000L, received = 152_000L)
        assertEquals(5_000L, c.offset)
        // A quicker one wins.
        c.note(serverNow = 305_020L, sent = 300_000L, received = 300_040L)
        assertEquals(5_000L, c.offset)
        assertEquals(95_000L, c.toLocal(100_000L))
    }

    @Test fun systemLinesAreWrittenFromTheirPayload() {
        fun line(e: VoiceSystemEvent, seat: Int? = null, title: String? = null) = VoiceLine.System(1, e, "Asha", 0, seat, title)
        assertEquals(UiText(R.string.voice_sys_joined, "Asha"), VoiceController.systemText(line(VoiceSystemEvent.JOINED)))
        assertEquals(UiText(R.string.voice_sys_left, "Asha"), VoiceController.systemText(line(VoiceSystemEvent.LEFT)))
        assertEquals(UiText(R.string.voice_sys_seat, "Asha", 3), VoiceController.systemText(line(VoiceSystemEvent.SEAT, seat = 3)))
        assertEquals(UiText(R.string.voice_sys_audience, "Asha"), VoiceController.systemText(line(VoiceSystemEvent.AUDIENCE)))
        assertEquals(UiText(R.string.voice_sys_cohost, "Asha"), VoiceController.systemText(line(VoiceSystemEvent.COHOST)))
        assertEquals(UiText(R.string.voice_sys_host, "Asha"), VoiceController.systemText(line(VoiceSystemEvent.HOST)))
        assertEquals(UiText(R.string.voice_sys_removed, "Asha"), VoiceController.systemText(line(VoiceSystemEvent.REMOVED)))
        assertEquals(UiText(R.string.voice_sys_track, "Asha", "Tum Hi Ho"), VoiceController.systemText(line(VoiceSystemEvent.TRACK, title = "Tum Hi Ho")))
        // A pasted link has no title: "played YouTube video".
        assertEquals(UiText(R.string.voice_sys_track_video, "Asha"), VoiceController.systemText(line(VoiceSystemEvent.TRACK, title = "")))
    }

    @Test fun youTubeLinksGiveTheirVideoId() {
        val id = "BddP6PYo2gs"
        listOf(
            "https://www.youtube.com/watch?v=$id", "youtube.com/watch?feature=share&v=$id", "https://youtu.be/$id?si=abc",
            "https://m.youtube.com/shorts/$id", "https://music.youtube.com/watch?v=$id&list=RD", "https://www.youtube-nocookie.com/embed/$id",
            "  https://youtube.com/live/$id  ",
        ).forEach { assertEquals(it, id, YouTube.videoId(it)) }
        listOf("", "hello", "https://vimeo.com/123", "https://youtu.be/short", "https://evil.com/watch?v=$id", "https://youtube.com/watch?v=$id x")
            .forEach { assertNull(it, YouTube.videoId(it)) }
    }
}

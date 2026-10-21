package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.domain.ExitPolicy
import com.yaari.chat.model.Gender
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceEvent
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoicePoll
import com.yaari.chat.model.VoiceReply
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
import com.yaari.chat.ui.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
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

/**
 * 0.10.0: one live session at a time (a room, or a random chat and its search: entering one leaves the other), the
 * room and the random chat minimised when the tabs are used, the music playing on when its panel collapses, replies, @mentions and polls (029_room_replies_polls.sql).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(backend: Backend = fake): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = "Dev", gender = Gender.MALE, day = "1", month = "1", year = "2000", agreed = true)); advanceUntilIdle()
        vm.selectTab(Tab.ROOMS); advanceUntilIdle()
        return vm
    }

    private fun AppViewModel.notice(): UiText? = state.value.notice.also { consumeNotice() }
    private val AppViewModel.room get() = voice.state.value!!

    /** A random chat (the scripted partner answers within seconds). */
    private fun TestScope.matched(vm: AppViewModel) {
        vm.startMatching()
        advanceTimeBy(5_000)
        runCurrent()
        assertNotNull("should be matched", vm.state.value.chat)
    }

    // ---- one live session ----

    @Test fun aRandomChatFromARoomLeavesTheRoomAndSaysSo() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        vm.startMatching(); runCurrent()
        assertNull("left the room, as its Leave does", vm.voice.state.value)
        assertTrue("and searching", vm.state.value.searching)
        assertEquals(UiText(R.string.live_left_room, "Cricket talk: IND vs AUS"), vm.notice())
        advanceTimeBy(500); runCurrent()
        assertFalse("the server let me go", fake.voice.isIn("v-cricket", vm.state.value.me!!.id))
        vm.cancelMatching(); advanceUntilIdle()
    }

    @Test fun theHostsRoomEndsFirst() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Late night", RoomCategory.TALK)); advanceUntilIdle()
        val id = vm.room.roomId
        assertTrue(vm.room.isHost)
        vm.startMatching(); runCurrent()
        assertEquals(UiText(R.string.live_left_room, "Late night"), vm.notice())
        advanceTimeBy(1_000); runCurrent()
        assertNull(vm.voice.state.value)
        assertFalse("ended for everyone", fake.voice.isOpen(id))
        assertTrue("the search starts once the room has ended", vm.state.value.searching)
        assertNull("no other word", vm.notice())
        vm.cancelMatching(); advanceUntilIdle()
    }

    @Test fun aRoomDuringARandomChatEndsTheChatAndSaysSo() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        val partner = vm.state.value.chat!!.match.partner
        // Inside the exit lock too: the server ends the chat when I enter a room (031).
        vm.voice.open("v-chai"); runCurrent()
        assertNull("the chat ended", vm.state.value.chat)
        assertEquals(UiText(R.string.live_ended_chat, partner.name), vm.notice())
        advanceTimeBy(2_000); runCurrent()
        assertEquals("v-chai", vm.voice.state.value?.roomId)
        vm.voice.close(); advanceUntilIdle()
    }

    @Test fun aSearchStopsQuietlyForARoom() = runTest(dispatcher) {
        val vm = signedIn()
        vm.startMatching(); runCurrent()
        assertTrue(vm.state.value.searching)
        vm.voice.open("v-cricket"); advanceTimeBy(1_000); runCurrent()
        assertFalse(vm.state.value.searching)
        assertNull("nothing to say for a search", vm.notice())
        assertEquals("v-cricket", vm.voice.state.value?.roomId)
        vm.voice.close(); advanceUntilIdle()
    }

    @Test fun creatingARoomAlsoEndsTheChat() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        vm.voice.create(NewVoiceRoom("Late night", RoomCategory.TALK)); advanceTimeBy(2_000); runCurrent()
        assertNull(vm.state.value.chat)
        assertTrue(vm.room.isHost)
        vm.voice.close(); advanceUntilIdle()
    }

    @Test fun theServerTakingMeOutForARandomChatIsQuiet() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        vm.notice()
        fake.voice.refuseMe("v-cricket", "in_random_chat"); advanceUntilIdle()
        assertNull(vm.voice.state.value)
        assertNull("no error", vm.notice())
    }

    @Test fun aTabMinimisesTheRandomChatWhichStaysOn() = runTest(dispatcher) {
        val vm = signedIn()
        matched(vm)
        val c = vm.state.value.chat!!
        vm.selectTab(Tab.ME); runCurrent()
        assertTrue(vm.state.value.chatMinimised)
        assertEquals("still on", c.match.id, vm.state.value.chat?.match?.id)
        // Start chatting with a chat on: it comes back.
        vm.startMatching(); runCurrent()
        assertFalse(vm.state.value.chatMinimised)
        vm.minimiseChat()
        assertTrue(vm.state.value.chatMinimised)
        // The bar's end button keeps the exit lock (the clock here never moves on its own).
        vm.notice()
        vm.endChatFromBar(); runCurrent()
        assertEquals(UiText(R.string.err_exit_locked), vm.notice())
        now += ExitPolicy.FREE_LOCK_MS + 1
        vm.endChatFromBar(); advanceTimeBy(1_000); runCurrent()
        assertNull(vm.state.value.chat)
        assertFalse(vm.state.value.chatMinimised)
    }

    @Test fun aTabTakesTheRoomDownToItsBarAndKeepsMeIn() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-cricket"); advanceUntilIdle()
        assertTrue(vm.room.expanded)
        vm.selectTab(Tab.ME); advanceUntilIdle()
        assertEquals(Tab.ME, vm.state.value.tab)
        assertFalse(vm.room.expanded)
        assertTrue(fake.voice.isIn("v-cricket", vm.state.value.me!!.id))
        vm.voice.expand()
        assertTrue(vm.room.expanded)
        vm.voice.close(); advanceUntilIdle()
    }

    // ---- music ----

    @Test fun collapsingTheMusicKeepsItPlaying() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Chai and songs", RoomCategory.TALK)); advanceUntilIdle()
        vm.voice.act(VoiceAction.AddTrack("BddP6PYo2gs", "Kesariya")); advanceUntilIdle()
        assertNotNull(vm.room.room!!.track)
        assertFalse("a talk room's music waits for Listen", vm.room.playing)
        vm.voice.listen(true)
        assertTrue(vm.room.playing && vm.room.musicOpen)
        vm.voice.musicPanel(false)
        assertTrue("collapsed: still playing", vm.room.playing)
        assertFalse(vm.room.musicOpen)
        vm.voice.minimise()
        assertTrue("minimised: still playing", vm.room.playing)
        assertTrue("the service keeps it alive", vm.room.keepAlive)
        vm.voice.listen(false)
        assertFalse(vm.room.playing)
        vm.voice.close(); advanceUntilIdle()
        // A music room always plays.
        vm.voice.open("v-bolly"); advanceUntilIdle()
        assertTrue(vm.room.playing)
        vm.voice.close(); advanceUntilIdle()
    }

    // ---- replies and mentions ----

    @Test fun aReplyQuotesTheLineAndTheQuoteComesBackOnFailure() = runTest(dispatcher) {
        var down = false
        val flaky = object : Backend by fake {
            override suspend fun sendVoice(roomId: String, text: String, replyTo: Long?): VoiceLine =
                if (down) throw com.yaari.chat.data.BackendException("network") else fake.sendVoice(roomId, text, replyTo)
        }
        val vm = signedIn(flaky)
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val other = vm.room.room!!.members.first { it.seat == null && it.profile.id != vm.room.room!!.meId }.profile
        fake.voice.say("v-cricket", other.id, "who is opening today?"); runCurrent()
        val line = vm.room.lines!!.last() as VoiceLine.Text
        vm.voice.reply(line)
        assertEquals(VoiceReply(line.id, other.id, other.name, VoiceReply.Kind.TEXT, "who is opening today?"), vm.room.replyTo)

        down = true
        var failed = false
        vm.voice.send("Rohit and Gill") { failed = true }; advanceUntilIdle()
        assertTrue(failed)
        assertEquals("the quote is back with the text", line.id, vm.room.replyTo?.id)
        vm.notice()

        down = false
        vm.voice.send("Rohit and Gill"); advanceUntilIdle()
        val mine = vm.room.lines!!.last() as VoiceLine.Text
        assertTrue(mine.fromMe)
        assertEquals(line.id, mine.reply?.id)
        assertEquals("who is opening today?", mine.reply?.text)
        assertNull(vm.room.replyTo)

        // System lines can't be answered; a photo is quoted without text; long lines are cut to 120.
        vm.voice.reply(VoiceLine.System(1, com.yaari.chat.model.VoiceSystemEvent.JOINED, "x", 0))
        assertNull(vm.room.replyTo)
        val long = VoiceLine.Text(2, other, false, "a".repeat(300), 0)
        assertEquals(VoiceReply.MAX_QUOTE, VoiceReply.of(long)!!.text!!.length)
        assertNull(VoiceReply.of(VoiceLine.Photo(3, other, false, "u", 0))!!.text)
        vm.voice.reply(line)
        vm.voice.cancelReply()
        assertNull(vm.room.replyTo)
        vm.voice.close(); advanceUntilIdle()
    }

    @Test fun mentionsAreFoundTypedAndInserted() {
        assertEquals("", VoiceController.mentionQuery("hey @"))
        assertEquals("Pri", VoiceController.mentionQuery("@Pri"))
        assertEquals("Rahul K", VoiceController.mentionQuery("hi @Rahul K"))
        assertNull("an e-mail is not a mention", VoiceController.mentionQuery("mail me@x"))
        assertNull("done typing", VoiceController.mentionQuery("@Priya "))
        assertNull(VoiceController.mentionQuery("no at here"))
        assertEquals("hey @Priya ", VoiceController.insertMention("hey @Pr", "Priya"))
        assertTrue(VoiceController.mentions("ok @dev, you next", "Dev"))
        assertTrue(VoiceController.mentions("@DEV", "Dev"))
        assertFalse("another name that starts the same", VoiceController.mentions("@Devika hi", "Dev"))
        assertFalse(VoiceController.mentions("dev without at", "Dev"))
        assertFalse(VoiceController.mentions("@", ""))
    }

    // ---- polls ----

    @Test fun pollsAreCountedVotedChangedAndClosed() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.open("v-chai"); advanceUntilIdle()
        val line = vm.room.lines!!.filterIsInstance<VoiceLine.Poll>().single()
        val poll = vm.room.polls.getValue(line.pollId)
        assertEquals(listOf(5, 2, 2), poll.counts)
        assertEquals(9, poll.total)
        assertNull(poll.mine)
        assertEquals(56, poll.percent(0))

        vm.voice.votePoll(line.pollId, 1); advanceUntilIdle()
        assertEquals(1, vm.room.polls.getValue(line.pollId).mine)
        assertEquals(listOf(5, 3, 2), vm.room.polls.getValue(line.pollId).counts)
        vm.voice.votePoll(line.pollId, 0); advanceUntilIdle()
        assertEquals("changed while open", listOf(6, 2, 2), vm.room.polls.getValue(line.pollId).counts)
        assertEquals(0, vm.room.polls.getValue(line.pollId).mine)

        // A listener can't ask or close; the host closes it; then no more votes.
        assertFalse(vm.voice.canClose(vm.room.polls.getValue(line.pollId)))
        vm.voice.createPoll("Samosa or vada pav?", listOf("Samosa", "Vada pav")); advanceUntilIdle()
        assertEquals(UiText(R.string.poll_e_not_allowed), vm.notice())
        fake.voice.closeFromHost(line.pollId); runCurrent()
        val closed = vm.room.polls.getValue(line.pollId)
        assertTrue(closed.closed)
        assertEquals("a broadcast has no answer of mine: the one I know stays", 0, closed.mine)
        vm.voice.votePoll(line.pollId, 2); advanceUntilIdle()
        assertEquals(0, vm.room.polls.getValue(line.pollId).mine)
        vm.voice.close(); advanceUntilIdle()
    }

    @Test fun theHostAsksANewPollAndTheLastOneCloses() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(NewVoiceRoom("Late night", RoomCategory.TALK)); advanceUntilIdle()
        vm.voice.createPoll("  ", listOf("a", "b")); runCurrent()
        assertEquals(UiText(R.string.poll_e_bad_request), vm.notice())
        vm.voice.createPoll("Same?", listOf("Yes", "yes")); runCurrent()
        assertEquals("answers differ in more than case", UiText(R.string.poll_e_bad_request), vm.notice())
        vm.voice.createPoll("Too few?", listOf("Only one", " ")); runCurrent()
        assertEquals(UiText(R.string.poll_e_bad_request), vm.notice())

        var asked = false
        vm.voice.createPoll("Movie tonight?", listOf("Yes", "No", "Maybe")) { asked = true }; advanceUntilIdle()
        assertTrue(asked)
        val first = vm.room.lines!!.filterIsInstance<VoiceLine.Poll>().last()
        assertTrue(first.fromMe)
        assertEquals(listOf("Yes", "No", "Maybe"), first.options)
        assertFalse(vm.room.polls.getValue(first.pollId).closed)
        assertTrue(vm.voice.canClose(vm.room.polls.getValue(first.pollId)))

        vm.voice.createPoll("Which one?", listOf("Sholay", "DDLJ")); advanceUntilIdle()
        assertTrue("a new poll closes the last", vm.room.polls.getValue(first.pollId).closed)
        val second = vm.room.lines!!.filterIsInstance<VoiceLine.Poll>().last()
        vm.voice.closePoll(second.pollId); advanceUntilIdle()
        assertTrue(vm.room.polls.getValue(second.pollId).closed)
        vm.voice.votePoll(second.pollId, 0); advanceUntilIdle()
        assertNull("closed: no vote is sent", vm.room.polls.getValue(second.pollId).mine)

        // A reply to a poll quotes its question.
        vm.voice.reply(second)
        assertEquals(VoiceReply.Kind.POLL, vm.room.replyTo?.kind)
        assertEquals("Which one?", vm.room.replyTo?.text)
        vm.voice.send("DDLJ forever"); advanceUntilIdle()
        assertEquals(second.id, (vm.room.lines!!.last() as VoiceLine.Text).reply?.id)
        vm.voice.close(); advanceUntilIdle()
    }

    @Test fun pollErrorsAreSaidInTheReadersLanguage() {
        fun e(code: String) = VoiceController.pollError(com.yaari.chat.data.BackendException(code))
        assertEquals(UiText(R.string.poll_e_closed), e("poll_closed"))
        assertEquals(UiText(R.string.poll_e_not_found), e("not_found"))
        assertEquals(UiText(R.string.poll_e_bad_request), e("bad_request"))
        assertEquals(UiText(R.string.poll_e_slow_down), e("slow_down"))
        assertEquals(UiText(R.string.poll_e_not_allowed), e("not_allowed"))
        assertNull(e("network"))
        assertTrue(VoicePoll.valid("Q?", listOf("a", "b")))
        assertFalse(VoicePoll.valid("Q?", listOf("a", "b", "c", "d", "e")))
        assertFalse(VoicePoll.valid("Q".repeat(121), listOf("a", "b")))
        assertFalse(VoicePoll.valid("Q?", listOf("a", "b".repeat(41))))
    }

    @Test fun aPollLineThatArrivesIsCountedAndBroadcastsKeepMyAnswer() = runTest(dispatcher) {
        val extra = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 8)
        val live = object : Backend by fake {
            override fun voiceSession(roomId: String): Flow<VoiceEvent> = merge(fake.voiceSession(roomId), extra)
        }
        val vm = signedIn(live)
        vm.voice.open("v-cricket"); advanceUntilIdle()
        val id = fake.voice.hostAsks("v-cricket", "Who wins?", listOf("India", "Australia"))!!
        // Before the room's own answers come in (from 1.5 s on).
        advanceTimeBy(400); runCurrent()
        assertTrue(vm.room.lines!!.any { it is VoiceLine.Poll && it.pollId == id })
        assertEquals(listOf(0, 0), vm.room.polls.getValue(id).counts)
        vm.voice.votePoll(id, 0); advanceTimeBy(300); runCurrent()
        assertEquals(0, vm.room.polls.getValue(id).mine)
        // Someone else's vote arrives over the room's channel (mine = null there).
        extra.emit(VoiceEvent.PollChanged(VoicePoll(id, "Who wins?", listOf("India", "Australia"), listOf(1, 1), 2, false, "h", null)))
        runCurrent()
        assertEquals(listOf(1, 1), vm.room.polls.getValue(id).counts)
        assertEquals(0, vm.room.polls.getValue(id).mine)
        vm.voice.close(); advanceUntilIdle()
    }
}

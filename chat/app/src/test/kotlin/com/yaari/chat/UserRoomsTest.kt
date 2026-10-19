package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.NewRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.UiText
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

/** User-created rooms against the FakeBackend, which applies the server's rules (008_user_rooms.sql). */
@OptIn(ExperimentalCoroutinesApi::class)
class UserRoomsTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_000_000L
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today })
    private val india = Countries.byIso("IN")!!

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** Signs in a new person on [backend]: a 26-year-old man unless told otherwise. */
    private fun TestScope.signedIn(
        phone: String = "9876543210", gender: Gender = Gender.MALE, year: String = "2000", backend: Backend = fake,
    ): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), clock = { now }, today = { today })
        advanceUntilIdle()
        vm.requestOtp(india, phone); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft(name = "U$phone", gender = gender, day = "1", month = "1", year = year, agreed = true)); advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        vm.selectTab(Tab.ROOMS); advanceUntilIdle()
        return vm
    }

    /** Rooms never go idle (presence beat): step the clock instead of advanceUntilIdle. */
    private fun TestScope.step(ms: Long = 100) {
        advanceTimeBy(ms); runCurrent()
    }

    private val cricket = NewRoom("  Cricket fans ", RoomCategory.SPORTS, 8, GenderRule.ANY, 18, 100, false)

    @Test fun createOpensTheRoomWithItsCode() = runTest(dispatcher) {
        val vm = signedIn()
        vm.createRoom(cricket.copy(title = " x ")); step()
        assertEquals(UiText(R.string.err_room_title), vm.state.value.notice)
        assertNull(vm.state.value.room)

        var closedSheet = false
        vm.createRoom(cricket) { closedSheet = true }; step()
        val open = vm.state.value.room!!
        assertTrue(closedSheet)
        assertEquals("Cricket fans", open.room.title)
        assertTrue(open.room.mine && open.showCode)
        assertEquals(6, open.room.code!!.length)
        assertEquals("⚽", open.room.emoji)
        val listed = vm.state.value.rooms.single { it.id == open.room.id }
        assertTrue(listed.mine && !listed.official)
        assertEquals(open.room.code, listed.code)
        assertNull(listed.refusal)
        vm.dismissRoomCode()
        assertFalse(vm.state.value.room!!.showCode)

        // A second room closes the first one (one open room per owner).
        vm.leaveRoom(); step()
        vm.createRoom(cricket.copy(title = "Second room")); step()
        val rooms = vm.state.value.rooms.filter { it.mine }
        assertEquals(listOf("Second room"), rooms.map { it.title })
        vm.leaveRoom(); step()
    }

    @Test fun privateRoomIsJoinedByCodeAndConditionsHold() = runTest(dispatcher) {
        val owner = signedIn()
        owner.createRoom(NewRoom("Girls study group", RoomCategory.STUDY, 5, GenderRule.FEMALE, 20, 30, isPrivate = true)); step()
        val room = owner.state.value.room!!.room
        val code = room.code!!
        owner.leaveRoom(); step()

        // A 25-year-old woman: the room is not listed, but the code (any case) lets her in.
        val her = signedIn("9876500001", Gender.FEMALE, "2001")
        assertTrue(her.state.value.rooms.none { it.id == room.id })
        her.joinByCode(code.lowercase()); step()
        assertEquals(room.id, her.state.value.room?.room?.id)
        assertFalse(her.state.value.room!!.room.mine)
        assertNull("members don't see the code", her.state.value.room!!.room.code)
        her.leaveRoom(); step()

        // A man is told why, and stays on the list.
        val him = signedIn("9876500002", Gender.MALE, "2001")
        him.joinByCode(code); step()
        assertNull(him.state.value.room)
        assertEquals(UiText(R.string.err_room_gender), him.state.value.notice)

        // A woman outside the age range, a wrong code, and a malformed one.
        val older = signedIn("9876500003", Gender.FEMALE, "1980")
        older.joinByCode(code); step()
        assertEquals(UiText(R.string.err_room_age), older.state.value.notice)
        older.consumeNotice()
        older.joinByCode("ZZZZ22"); step()
        assertEquals(UiText(R.string.err_room_code_unknown), older.state.value.notice)
        older.consumeNotice()
        older.joinByCode("AB"); step()
        assertEquals(UiText(R.string.err_room_code), older.state.value.notice)
        assertNull(older.state.value.room)
    }

    @Test fun refusedRoomsShowTheReasonInsteadOfTrying() = runTest(dispatcher) {
        var joins = 0
        val counting = object : Backend by fake {
            override suspend fun joinRoom(roomId: String) { joins++; fake.joinRoom(roomId) }
        }
        val vm = signedIn(backend = counting)
        val rooms = vm.state.value.rooms
        val women = rooms.single { it.genderRule == GenderRule.FEMALE }
        val full = rooms.single { it.maxMembers == 2 }
        assertEquals("room_gender", women.refusal)
        assertEquals("room_full", full.refusal)
        assertTrue("official rooms are always open", rooms.filter { it.official }.all { it.refusal == null })

        vm.enterRoom(women); step()
        assertNull(vm.state.value.room)
        assertEquals(UiText(R.string.err_room_women), vm.state.value.notice)
        vm.consumeNotice()
        vm.enterRoom(full); step()
        assertEquals(UiText(R.string.err_room_full), vm.state.value.notice)
        assertEquals("never asked the server", 0, joins)

        // An open one works and counts me in.
        val open = rooms.single { !it.official && it.refusal == null }
        vm.enterRoom(open); step()
        assertEquals(open.id, vm.state.value.room?.room?.id)
        assertEquals(1, joins)
        vm.leaveRoom(); step()
    }

    @Test fun ownerClosesTheRoom() = runTest(dispatcher) {
        val vm = signedIn()
        vm.createRoom(cricket); step()
        val id = vm.state.value.room!!.room.id
        vm.closeRoom(); step()
        assertTrue(vm.state.value.room!!.closed)
        assertTrue("gone from the list", vm.state.value.rooms.none { it.id == id })
        vm.sendRoom("anyone?"); step()
        assertTrue(vm.state.value.room!!.messages.none { it.fromMe })
        vm.leaveRoom(); step()
        assertNull(vm.state.value.room)
    }

    @Test fun roomClosedWhileInsideDisablesTheComposer() = runTest(dispatcher) {
        var closed = false
        val closing = object : Backend by fake {
            override suspend fun joinRoom(roomId: String) = if (closed) throw BackendException("room_closed") else fake.joinRoom(roomId)
            override suspend fun sendRoom(roomId: String, text: String) =
                if (closed) throw BackendException("room_closed") else fake.sendRoom(roomId, text)
        }
        val vm = signedIn(backend = closing)
        val open = vm.state.value.rooms.single { !it.official && it.refusal == null }
        vm.enterRoom(open); step()
        vm.sendRoom("hello"); step()
        assertTrue(vm.state.value.room!!.messages.any { it.fromMe })
        closed = true // the owner closes it
        step(AppViewModel.ROOM_BEAT_MS + 100) // the next presence beat finds out
        assertTrue(vm.state.value.room!!.closed)
        assertNull("no error toast, the room shows it", vm.state.value.notice)
        vm.leaveRoom(); step()

        // Writing is the other way to find out.
        closed = false
        vm.enterRoom(open); step()
        closed = true
        vm.sendRoom("still there?"); step()
        assertTrue(vm.state.value.room!!.closed)
        assertNull(vm.state.value.notice)
        vm.leaveRoom(); step()
    }
}

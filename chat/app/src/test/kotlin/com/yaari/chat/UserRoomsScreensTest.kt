package com.yaari.chat

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.GenderRule
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** User rooms through the real UI: the list with conditions, the create sheet, the owner's room, closing it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class UserRoomsScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(ControlledRandom(), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun click(text: String) = compose.onAllNodes(hasText(text, substring = true))[0].performClick()

    @Test fun userRooms() {
        compose.setContent { YaariTheme { YaariApp(vm) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Dev", Gender.MALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
        compose.runOnUiThread { vm.selectTab(Tab.ROOMS) }
        waitFor("Global Lounge")

        // The list: people's rooms with owner, count and conditions; the ones I can't enter say why.
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("actions")
        waitFor("Rooms by people")
        shot("22_user_rooms")
        click("Chai & chill")
        compose.waitUntil(5_000) { vm.state.value.notice != null || vm.state.value.room != null }
        assertNull("a women-only room doesn't open for a man", vm.state.value.room)

        // Create a room
        click("Create room")
        waitFor("Create a room")
        compose.onNode(hasSetTextAction() and hasText("Room name")).performTextInput("Cricket night 🏏")
        compose.onAllNodes(hasText("⚽  Sports"))[0].performClick()
        compose.onAllNodes(hasText("Men"))[0].performClick()
        val sliders = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        sliders[0].performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        sliders[1].performSemanticsAction(SemanticsActions.SetProgress) { it(21f) }
        sliders[2].performSemanticsAction(SemanticsActions.SetProgress) { it(35f) }
        waitFor("21–35")
        shot("23_create_room")
        compose.onAllNodes(hasText("Create room"))
            .fetchSemanticsNodes().size.let { assertTrue(it >= 1) }
        compose.onAllNodes(hasText("Create room")).let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick()

        // The new room opens with its code
        waitFor("Your room is open!")
        val room = vm.state.value.room!!.room
        assertEquals(GenderRule.MALE to (21 to 35), room.genderRule to (room.minAge to room.maxAge))
        assertEquals(12, room.maxMembers)
        shot("24_room_created")
        click("Done")
        waitFor("Room code")
        shot("25_my_room")

        // Closing it (with a confirmation) leaves a closed room without a composer
        click("Close room")
        waitFor("Close this room?")
        compose.onAllNodes(hasText("Close room")).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        waitFor("Nobody can write here any more")
        assertTrue(vm.state.value.room!!.closed)
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        shot("26_room_closed")
        click("Back to rooms")
        compose.waitUntil(5_000) { vm.state.value.room == null }
        compose.onAllNodes(hasContentDescription("Settings")).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }
}

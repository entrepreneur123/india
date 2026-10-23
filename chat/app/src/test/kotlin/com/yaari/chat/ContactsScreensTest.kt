package com.yaari.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.FirstRun
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.ContactApp
import com.yaari.chat.model.Gender
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ContactsEditorScreen
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 044 screens, to compare with the web's: my contacts editor, a friend's profile with and without their contacts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ContactsScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val ananya = FakeBackend.POOL.first { it.id == "p3" }
    private val editor = mutableStateOf(false)

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/contacts_$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun signIn(backend: FakeBackend): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today }, firstRun = FirstRun.Seen)
        compose.setContent { YaariTheme { Box { YaariApp(vm, online = MutableStateFlow(true)); if (editor.value) ContactsEditorScreen(vm, {}) } } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "1998", "Pune", "", agreed = true)) }
        compose.waitUntil(5_000) { vm.state.value.me != null }
        compose.runOnUiThread { vm.answerFriend(ananya, true) }
        compose.waitUntil(5_000) { vm.state.value.friends.isNotEmpty() }
        return vm
    }

    @Test fun editor() {
        val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
        signIn(backend)
        runBlocking { backend.setContacts(mapOf(ContactApp.WHATSAPP to "+91 98765 43210")) }
        compose.runOnUiThread { editor.value = true }
        waitFor("Never share contacts")
        compose.onNode(hasSetTextAction() and hasText("Instagram")).performTextInput("@asha.k")
        compose.onNode(hasSetTextAction() and hasText("Telegram")).performTextInput("asha k")
        waitFor("Use up to 40 letters")
        shot("editor")
    }

    @Test fun friendShares() {
        val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
        val vm = signIn(backend)
        runBlocking { backend.setContacts(mapOf(ContactApp.WHATSAPP to "+919876543210")); backend.shareContacts(ananya.id, true) }
        compose.runOnUiThread { vm.openProfile(ananya) }
        waitFor("ananya.dances")
        shot("friend_shared")
    }

    @Test fun friendHasNotShared() {
        val backend = object : FakeBackend(kotlin.random.Random(11), today = { today }, instant = true) {
            override suspend fun contactsOf(userId: String) = super.contactsOf(userId)?.copy(contacts = null)
        }
        val vm = signIn(backend)
        compose.runOnUiThread { vm.openProfile(ananya) }
        waitFor("hasn’t shared contacts yet")
        shot("friend_not_shared")
    }
}

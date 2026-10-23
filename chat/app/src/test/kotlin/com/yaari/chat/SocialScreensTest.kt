package com.yaari.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.FirstRun
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.InboxKind
import com.yaari.chat.model.Interest
import com.yaari.chat.model.RoomQuery
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.FirstRunStep
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 0.9.0 screens, to compare with the web's (Notifications, room search, interests, photos, the tour). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class SocialScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/social_$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun signIn(firstRun: FirstRun = FirstRun.Seen): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today }, firstRun = firstRun)
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "1998", "Pune", "", agreed = true)) }
        compose.waitUntil(5_000) { vm.state.value.me != null }
        return vm
    }

    private fun person(id: String) = FakeBackend.POOL.first { it.id == id }

    @Test fun inbox() {
        val now = System.currentTimeMillis()
        backend.notify(InboxKind.FRIEND_ACCEPT, person("p7"), at = now - 86_400_000L)
        backend.notify(InboxKind.MESSAGE, person("p5"), matchId = "m-sofia", at = now - 12 * 60_000L)
        backend.notify(InboxKind.MESSAGE, person("p5"), matchId = "m-sofia", at = now - 12 * 60_000L)
        backend.notify(InboxKind.ROOM_INVITE, person("p1"), roomId = "v-chai", at = now - 4 * 60_000L)
        val vm = signIn()
        runBlocking { backend.markInboxRead(listOf(2L)) } // seen earlier
        waitFor("Meet someone new")
        compose.runOnUiThread { vm.social.openInbox() }
        waitFor("invited you to Chai pe charcha")
        shot("inbox")
    }

    @Test fun roomSearch() {
        val vm = signIn()
        compose.runOnUiThread {
            vm.voice.search(RoomQuery("chai", "hi"))
            vm.selectTab(Tab.ROOMS)
        }
        waitFor("Chai pe charcha")
        shot("room_search")
    }

    @Test fun interestsThenTour() {
        val vm = signIn(FirstRun.Memory())
        waitFor("What are you into?")
        listOf("Cricket", "Music", "Travel").forEach { compose.onNodeWithText(it).performClick() }
        shot("interests")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { vm.social.state.value.firstRun == FirstRunStep.TOUR }
        waitFor("Tap Start")
        shot("tour")
    }

    @Test fun gallery() {
        val vm = signIn()
        runBlocking {
            backend.setInterests(listOf(Interest.CRICKET, Interest.MUSIC, Interest.TRAVEL))
            listOf(0xFFE57373.toInt(), 0xFF5C8DEB.toInt()).forEachIndexed { i, c -> backend.addProfilePhoto(photo("p$i", c)) }
        }
        compose.runOnUiThread {
            vm.social.refreshBadges()
            vm.selectTab(Tab.ME)
        }
        // The profile on screen is the one from sign-up; reload it with the extras.
        compose.runOnUiThread { vm.social.removePhoto("none") }
        waitFor("Cricket") // my profile shows them as others see it (editing is in Edit profile)
        shot("gallery")
    }

    /** A small picture on disk, so the gallery shows real tiles offline. */
    private fun photo(name: String, color: Int): String {
        val bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(color)
            drawCircle(120f, 100f, 50f, Paint().apply { this.color = Color.WHITE })
        }
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + file.absolutePath
    }
}

package com.yaari.chat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Gender
import com.yaari.chat.model.LookKind
import com.yaari.chat.ui.AppViewModel
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 046 through the real UI: the shop's walls, stickers and VIP tabs, a chat on a wall with stickers, a VIP stranger. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class PaidScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 23)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)
    private val vm = AppViewModel(backend, DemoBilling(), today = { today })

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/paid_$name.png")
    }

    /** Sheets are windows of their own: the whole screen. */
    private fun screen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/paid_$name.png")
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = compose.waitUntil(5_000) { has(text) }
    private fun click(text: String) = compose.onAllNodes(hasText(text))[0].also { runCatching { it.performScrollTo() } }.performClick()

    private fun signIn(vararg owned: String) {
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Mumbai", "", agreed = true)) }
        waitFor("Meet someone new")
        runBlocking { owned.forEach { backend.redeem(it, "demo-$it") } }
    }

    private fun openShop(tab: String) {
        compose.runOnUiThread { vm.selectTab(Tab.ME) }
        waitFor("Looks")
        click("Looks")
        waitFor("Everyone sees your look")
        click(tab)
    }

    @Test fun theShopsWallsTab() {
        signIn("wall_paisley")
        openShop("Walls")
        waitFor("Starry night")
        click("Starry night")
        shot("01_shop_walls_stars")
        click("Paisley")
        click("Use this wall")
        compose.waitUntil(5_000) { vm.looks.shop.value.shop?.wall == "wall_paisley" }
        waitFor("In use")
        shot("02_shop_wall_in_use")
    }

    @Test fun theShopsStickersAndVipTabs() {
        signIn("sticker_chai")
        openShop("Stickers")
        waitFor("Cricket")
        click("Chai time")
        waitFor("send them from any chat")
        shot("03_shop_stickers")
        // Opened on a tab, as a chat's "Get pack" does.
        compose.runOnUiThread { vm.looks.openShop(LookKind.VIP) }
        waitFor("STRANGERS SEE")
        shot("04_shop_vip")
        runBlocking { backend.redeem("vip_anon", "demo-vip") }
        compose.runOnUiThread { vm.looks.loadShop() }
        waitFor("Show me only as")
        click("Incognito")
        compose.waitUntil(5_000) { vm.looks.shop.value.shop?.anon == true }
        shot("05_shop_vip_on")
    }

    @Test fun aChatOnMyWallWithStickers() {
        signIn("wall_midnight", "sticker_namaste")
        runBlocking { backend.setWall("wall_midnight") }
        compose.runOnUiThread { vm.startMatching() }
        compose.waitUntil(5_000) { vm.state.value.chat != null }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread { vm.sendText("Hii, chai pe chalein?") }
        compose.waitUntil(5_000) { vm.state.value.chat!!.messages.size >= 2 }
        compose.runOnUiThread { vm.sendText(":st:namaste/1:") }
        compose.waitUntil(5_000) { vm.state.value.chat!!.messages.any { it.text == ":st:namaste/1:" } }
        shot("06_chat_wall_sticker")
        compose.onNode(hasContentDescription("Stickers")).performClick()
        waitFor("Chai time")
        compose.onAllNodes(hasText("Love"))[0].performScrollTo().performClick()
        waitFor("Get this pack")
        screen("07_sticker_picker_locked")
    }

    @Test fun aVipStrangerInRecentAndTheirProfile() {
        signIn()
        compose.runOnUiThread { vm.selectTab(Tab.RECENT) }
        click("Matches")
        waitFor("Sticker")
        shot("08_recent_vip_sticker")
        compose.runOnUiThread { vm.openProfile(FakeBackend.VIP) }
        waitFor("friends see the full profile")
        shot("09_profile_vip")
    }
}

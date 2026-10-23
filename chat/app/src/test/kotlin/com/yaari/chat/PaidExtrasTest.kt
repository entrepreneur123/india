package com.yaari.chat

import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.Products
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.Gender
import com.yaari.chat.model.LookKind
import com.yaari.chat.model.Profile
import com.yaari.chat.model.Sticker
import com.yaari.chat.model.Stickers
import com.yaari.chat.model.isVip
import com.yaari.chat.model.nameAge
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.UiText
import com.yaari.chat.ui.WALLS
import com.yaari.chat.ui.nameAgeFlag
import com.yaari.chat.ui.wallOf
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 046: chat walls, sticker packs and VIP incognito: the same data and rules as the web and the server. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaidExtrasTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 23)
    private var now = 1_800_000_000_000L
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(): AppViewModel {
        val vm = AppViewModel(fake, DemoBilling(), clock = { now }, today = { today }).also { advanceUntilIdle() }
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "1998", "Pune", "", agreed = true)); advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        return vm
    }

    private fun TestScope.buy(vm: AppViewModel, item: String) {
        vm.buyLook(Robolectric.buildActivity(android.app.Activity::class.java).get(), item); advanceUntilIdle()
    }

    private suspend fun refused(code: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (e: BackendException) {
            assertEquals(code, e.code)
        }
    }

    @Test fun theCatalogueHas11NewItemsBoughtThroughThePurchaseCode() {
        val byKind = Cosmetic.entries.groupBy { it.kind }
        assertEquals(37, Cosmetic.entries.size)
        assertEquals(listOf(6, 4, 1), listOf(LookKind.WALL, LookKind.STICKER, LookKind.VIP).map { byKind.getValue(it).size })
        Cosmetic.entries.forEach { assertTrue(it.id, it.id.startsWith(it.kind.code + "_") && Cosmetic.isLook(it.id)) }
        assertFalse("an ID change is consumed, not a look", Cosmetic.isLook(Products.HANDLE_CHANGE))
        assertEquals(listOf(true, false), listOf(LookKind.FRAME, LookKind.WALL).map { it.wearable })
    }

    @Test fun stickersAreWholeMessagesOfEightPerPack() {
        assertEquals(Cosmetic.entries.filter { it.kind == LookKind.STICKER }, Stickers.PACKS.keys.toList())
        Stickers.PACKS.forEach { (pack, art) ->
            assertEquals(pack.id, 8, art.size)
            art.forEach { assertTrue("${pack.id} ${it.caption}", it.emoji.isNotEmpty() && it.caption.length in 1..16) }
        }
        assertEquals(":st:namaste/1:", Stickers.body(Cosmetic.STICKER_NAMASTE, 1))
        assertEquals(":st:love/8:", Stickers.body(Cosmetic.STICKER_LOVE, 8))
        Stickers.PACKS.forEach { (pack, art) ->
            (1..8).forEach { n -> assertEquals(Sticker(pack.id, n, art[n - 1]), Stickers.of(Stickers.body(pack, n))) }
        }
        assertEquals("a pack this app doesn't know yet is still a sticker", Sticker("sticker_diwali", 3, null), Stickers.of(":st:diwali/3:"))
        listOf(":st:namaste/0:", ":st:namaste/9:", " :st:namaste/1:", ":st:namaste/1: hi", "hi :st:chai/2:", ":st:Chai/2:", ":st:x/1:", "", null)
            .forEach { assertNull(it.toString(), Stickers.of(it)) }
        assertEquals("☕ Sticker", Stickers.preview(":st:chai/1:", "Sticker"))
        assertEquals("Sticker", Stickers.preview(":st:diwali/1:", "Sticker"))
        assertEquals("see you :)", Stickers.preview("see you :)", "Sticker"))
    }

    @Test fun everyWallOnSaleIsDrawnAndUnknownOnesAreNone() {
        assertEquals(Cosmetic.entries.filter { it.kind == LookKind.WALL }.toSet(), WALLS.keys)
        Cosmetic.entries.filter { it.kind == LookKind.WALL }.forEach { assertNotNull(it.id, wallOf(it.id)) }
        listOf(null, "", "wall_disco", "badge_crown").forEach { assertNull(it.toString(), wallOf(it)) }
    }

    @Test fun vipIncognitoHidesTheAge() {
        val vip = Profile("x", "VIP", Gender.FEMALE, 0, "", "")
        assertTrue(vip.isVip)
        assertTrue("a room line's sender has no age", isVip("VIP", 0, null))
        assertFalse("someone really called VIP", isVip("VIP", 24, null))
        assertFalse(isVip("VIP", 0, "https://x/y.jpg"))
        assertFalse(isVip("Priya", 0, null))
        assertEquals("Priya, 24", nameAge("Priya", 24))
        assertEquals("VIP", nameAge("VIP", 0))
        assertEquals("VIP", vip.nameAgeFlag())
        assertEquals("Priya, 22 🇮🇳", vip.copy(name = "Priya", age = 22, country = "IN").nameAgeFlag())
    }

    @Test fun wallsAreBoughtThenChosenAndOnlyOwnedOnes() = runTest(dispatcher) {
        val vm = signedIn()
        refused("not_owned") { fake.setWall("wall_stars") }
        buy(vm, "wall_stars")
        assertEquals(UiText(R.string.item_bought, UiText(R.string.look_wall_stars)), vm.state.value.notice)
        val shop = vm.looks.shop.value.shop!!
        assertTrue("wall_stars" in shop.owned)
        assertNull("a wall is chosen by hand", shop.wall)
        vm.looks.setWall("wall_stars"); advanceUntilIdle()
        assertEquals("wall_stars", vm.looks.shop.value.shop!!.wall)
        assertEquals("wall_stars", fake.shop().wall)
        refused("not_owned") { fake.setWall("badge_crown") }
        vm.looks.setWall(null); advanceUntilIdle()
        assertNull(fake.shop().wall)
        assertEquals(UiText(R.string.looks_saved), vm.state.value.notice)
    }

    @Test fun aStickerNeedsItsPack() = runTest(dispatcher) {
        val vm = signedIn()
        vm.startMatching(); advanceTimeBy(5_000); runCurrent()
        val chat = vm.state.value.chat!!
        var back = false
        vm.sendText(":st:chai/2:") { back = true }; advanceUntilIdle()
        assertEquals("not the generic not_owned", UiText(R.string.err_sticker_not_owned), vm.state.value.notice)
        assertTrue(back)
        buy(vm, "sticker_chai")
        vm.sendText(":st:chai/2:"); advanceUntilIdle()
        assertEquals(":st:chai/2:", vm.state.value.chat!!.messages.last { it.fromMe }.text)
        assertEquals(chat.match.id, vm.state.value.chat!!.match.id)
        // Plain text and emoji stay free.
        vm.sendText("hi 🙏"); advanceUntilIdle()
        assertEquals("hi 🙏", vm.state.value.chat!!.messages.last { it.fromMe }.text)
    }

    @Test fun incognitoIsASwitchOnceOwned() = runTest(dispatcher) {
        val vm = signedIn()
        refused("not_owned") { fake.setAnon(true) }
        fake.setAnon(false) // turning it off never needs it
        buy(vm, "vip_anon")
        vm.looks.setAnon(true); advanceUntilIdle()
        assertEquals(UiText(R.string.vip_on), vm.state.value.notice)
        assertTrue(fake.shop().anon && vm.looks.shop.value.shop!!.anon)
        vm.looks.setAnon(false); advanceUntilIdle()
        assertFalse(fake.shop().anon)
    }

    @Test fun theDemoHasAVipStrangerWhoSentASticker() = runTest(dispatcher) {
        val vm = signedIn()
        vm.refreshRecents(); advanceUntilIdle()
        val r = vm.state.value.recents.single { it.partner.isVip }
        assertEquals("🙏 Sticker", Stickers.preview(r.lastText!!, "Sticker"))
        assertEquals("", r.partner.city)
        assertTrue("no ID to show", fake.looks(listOf(r.partner.id)).isEmpty())
        assertTrue("never found by ID", vm.findByHandle("vip").isEmpty())
    }
}

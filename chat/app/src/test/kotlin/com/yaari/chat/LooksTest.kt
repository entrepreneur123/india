package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.Billing
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.Products
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.Gender
import com.yaari.chat.model.HandleCheck
import com.yaari.chat.model.Look
import com.yaari.chat.model.LookKind
import com.yaari.chat.R
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.LooksController
import com.yaari.chat.ui.ProfileDraft
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

/**
 * 0.15.0: IDs (sign-up, Edit profile, the live check) and looks (the shop, wearing, buying, everyone's lookup).
 * Robolectric only for the Activity a purchase sheet needs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LooksTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 22)
    private var now = 1_800_000_000_000L
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val asha = ProfileDraft("Asha", Gender.FEMALE, "1", "1", "1998", "Pune", "", agreed = true)

    private fun TestScope.vm(backend: Backend = fake, billing: Billing = DemoBilling()) =
        AppViewModel(backend, billing, clock = { now }, today = { today }).also { advanceUntilIdle() }

    private fun TestScope.atProfileStep(vm: AppViewModel, phone: String = "9876543210") {
        vm.requestOtp(Countries.byIso("IN")!!, phone); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
    }

    private fun TestScope.signedIn(backend: Backend = fake, billing: Billing = DemoBilling(), draft: ProfileDraft = asha): AppViewModel {
        val vm = vm(backend, billing)
        atProfileStep(vm)
        vm.createProfile(draft); advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        return vm
    }

    private suspend fun refused(code: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (e: BackendException) {
            assertEquals(code, e.code)
        }
    }

    // ---- IDs ----

    @Test fun everyNewProfileHasAFreeIdAndCanChooseOneAtSignUp() = runTest(dispatcher) {
        val free = signedIn()
        assertTrue(free.state.value.me!!.handle!!.matches(Regex("asha_[0-9]{4}")))
        fake.signOut()
        val chosen = vm()
        atProfileStep(chosen, "9876500000")
        chosen.createProfile(asha.copy(handle = " asha.pune ")); advanceUntilIdle()
        assertEquals("trimmed, as the server stores it", "asha.pune", chosen.state.value.me!!.handle)
        assertNull(chosen.state.value.notice)
    }

    @Test fun theIdFieldChecksAsItIsTyped() = runTest(dispatcher) {
        val vm = vm()
        atProfileStep(vm) // signed in, no profile yet: handle_check works already
        vm.looks.checkHandle("li sa", null)
        assertEquals("the phone knows at once", HandleCheck.INVALID, vm.looks.handle.value!!.result)
        vm.looks.checkHandle("yaari_team", null)
        assertEquals(HandleCheck.RESERVED, vm.looks.handle.value!!.result)
        vm.looks.checkHandle("priya.mumb", null)
        vm.looks.checkHandle("priya.mumbai", null)
        assertTrue(vm.looks.handle.value!!.checking)
        advanceTimeBy(LooksController.CHECK_MS - 1); runCurrent()
        assertTrue("the server is asked 400 ms after the last keystroke", vm.looks.handle.value!!.checking)
        advanceTimeBy(2); runCurrent()
        assertEquals(HandleCheck.TAKEN, vm.looks.handle.value!!.result)
        // A taken ID doesn't make a profile; the field says why.
        vm.createProfile(asha.copy(handle = "priya.mumbai")); advanceUntilIdle()
        assertEquals(UiText(R.string.err_handle_taken), vm.state.value.onboarding.error)
        assertNull(vm.state.value.me)
        vm.looks.checkHandle("asha.pune", null); advanceUntilIdle()
        assertEquals(HandleCheck.OK, vm.looks.handle.value!!.result)
    }

    @Test fun anIdTakenInBetweenKeepsTheFreeOneAndSaysSo() = runTest(dispatcher) {
        val vm = signedIn(draft = asha.copy(handle = "Priya.Mumbai")) // taken, case-blind; not checked before
        val free = vm.state.value.me!!.handle!!
        assertTrue(free.startsWith("asha_"))
        assertEquals(UiText(R.string.handle_kept, free), vm.state.value.notice)
    }

    @Test fun badIdsAndFancyNamesAreRefusedAtSignUp() = runTest(dispatcher) {
        val vm = vm()
        atProfileStep(vm)
        vm.createProfile(asha.copy(handle = "li sa")); advanceUntilIdle()
        assertEquals(UiText(R.string.err_handle_invalid), vm.state.value.onboarding.error)
        vm.createProfile(asha.copy(handle = "support")); advanceUntilIdle()
        assertEquals(UiText(R.string.err_handle_reserved), vm.state.value.onboarding.error)
        vm.createProfile(asha.copy(name = "𝓐𝓼𝓱𝓪")); advanceUntilIdle()
        assertEquals(UiText(R.string.err_name_invalid), vm.state.value.onboarding.error)
        assertNull(vm.state.value.me)
        // The server says the same (another app could skip the phone's check).
        refused("name_invalid") { fake.updateProfile("𝓐𝓼𝓱𝓪", "", "", null) }
    }

    @Test fun editProfileChangesTheIdOnceAMonthOrWithAPaidChangeButASpellingAnyTime() = runTest(dispatcher) {
        val vm = signedIn()
        val me = vm.state.value.me!!
        var saved = 0
        vm.saveProfile(me.name, me.city, me.bio, null, "asha.pune") { saved++ }; advanceUntilIdle()
        assertEquals("the free ID changes at once", "asha.pune", vm.state.value.me!!.handle)
        assertEquals("asha.pune", vm.looks.people.value[me.id]!!.handle)
        vm.looks.loadShop(); advanceUntilIdle()
        assertEquals(now + 30 * 86_400_000L, vm.looks.shop.value.shop!!.handleNext)
        vm.saveProfile(me.name, me.city, me.bio, null, "Asha.Pune") { saved++ }; advanceUntilIdle()
        assertEquals("a new spelling is always fine", "Asha.Pune", vm.state.value.me!!.handle)
        vm.saveProfile("Asha R", me.city, me.bio, null, "asha.r") { saved++ }; advanceUntilIdle()
        assertEquals(UiText(R.string.err_handle_cooldown), vm.state.value.notice)
        assertEquals("nothing saved when the ID is refused", "Asha", vm.state.value.me!!.name)
        assertEquals(2, saved)
        // A paid ID change lets one change through, and is used up.
        vm.buyLook(Robolectric.buildActivity(android.app.Activity::class.java).get(), Products.HANDLE_CHANGE); advanceUntilIdle()
        assertEquals(UiText(R.string.handle_pass_bought), vm.state.value.notice)
        assertEquals(1, vm.looks.shop.value.shop!!.handlePasses)
        vm.saveProfile("Asha R", me.city, me.bio, null, "asha.r") { saved++ }; advanceUntilIdle()
        assertEquals("asha.r" to "Asha R", vm.state.value.me!!.handle to vm.state.value.me!!.name)
        assertEquals(0, fake.shop().handlePasses)
        refused("handle_cooldown") { fake.setHandle("asha.again") }
        now += 30 * 86_400_000L
        assertEquals("asha.again", fake.setHandle("asha.again"))
        // Empty or against the rules: said on the phone, nothing is sent.
        vm.saveProfile("Asha R", me.city, me.bio, null, "") { saved++ }; advanceUntilIdle()
        assertEquals(UiText(R.string.err_handle_invalid), vm.state.value.notice)
        assertEquals(3, saved)
    }

    // ---- finding people by ID, and the block list (042) ----

    @Test fun peopleAreFoundByIdUntilBlockedAndComeBackWhenUnblocked() = runTest(dispatcher) {
        val vm = signedIn()
        assertEquals(listOf("priya.mumbai"), vm.findByHandle("@PRIYA").map { it.handle })
        assertTrue(vm.findByHandle("  ").isEmpty())
        val priya = vm.findByHandle("priya").single()
        vm.openProfile(priya); advanceUntilIdle()
        assertEquals(priya.id, vm.state.value.viewing?.id)
        fake.block(priya.id)
        assertTrue(vm.findByHandle("priya").isEmpty())
        assertEquals(listOf(priya.id), vm.blocks().map { it.id })
        var ok: Boolean? = null
        vm.unblock(priya) { ok = it }; advanceUntilIdle()
        assertEquals(true, ok)
        assertEquals(UiText(R.string.unblocked, priya.name), vm.state.value.notice)
        assertTrue(vm.blocks().isEmpty())
        assertEquals(1, vm.findByHandle("priya").size)
    }

    // ---- looks: the shop, wearing, buying (FakeBackend keeps the server's rules) ----

    @Test fun theShopSellsTheCatalogueAndNothingIsOwnedAtFirst() = runTest(dispatcher) {
        signedIn()
        val shop = fake.shop()
        assertEquals(Cosmetic.entries.map { it.id }, shop.items.map { it.id })
        assertEquals(26, shop.items.size)
        assertEquals(129, shop.items.first { it.id == "color_rainbow" }.priceInr)
        assertTrue(shop.owned.isEmpty())
        assertEquals(Look(shop.look!!.userId, shop.look.handle), shop.look)
        assertNull("the free ID changes at once", shop.handleNext)
    }

    @Test fun onlyOwnedLooksOfTheirOwnKindCanBeWorn() = runTest(dispatcher) {
        signedIn()
        refused("not_owned") { fake.equipLook("badge_crown", null, null, null) }
        fake.redeem("badge_crown", "demo-badge_crown")
        fake.redeem("frame_fire", "demo-frame_fire")
        fake.redeem("frame_frost", "demo-frame_frost")
        val look = fake.shop().look!!
        assertEquals("the first of a kind is worn at once, the second is not", "badge_crown" to "frame_fire", look.badge to look.frame)
        refused("bad_request") { fake.equipLook("frame_frost", null, null, null) }
        val frost = fake.equipLook(null, null, null, "frame_frost")
        assertNull("all four at once: no badge now", frost.badge)
        assertEquals("frame_frost", frost.frame)
        assertEquals(setOf("badge_crown", "frame_fire", "frame_frost"), fake.shop().owned)
    }

    @Test fun buyingIsInstantInTheDemoAndWearingFollowsEverywhere() = runTest(dispatcher) {
        val vm = signedIn()
        val me = vm.state.value.me!!.id
        vm.looks.loadShop(); advanceUntilIdle()
        // The demo's billing hands back a token at once; the server's check makes it mine (and worn: the first badge).
        vm.buyLook(Robolectric.buildActivity(android.app.Activity::class.java).get(), "badge_star"); advanceUntilIdle()
        assertEquals(UiText(R.string.look_bought), vm.state.value.notice)
        assertNull(vm.looks.shop.value.busy)
        assertEquals(setOf("badge_star"), vm.looks.shop.value.shop!!.owned)
        assertEquals("badge_star", vm.looks.people.value[me]!!.badge)
        vm.looks.tryOn(LookKind.BADGE, "badge_crown")
        assertEquals("tried, not worn", "badge_crown", vm.looks.shop.value.trying!!.badge)
        assertEquals("badge_star", vm.looks.shop.value.shop!!.look!!.badge)
        vm.looks.wear(LookKind.BADGE, "badge_crown"); advanceUntilIdle()
        assertEquals(UiText(R.string.err_not_owned), vm.state.value.notice)
        vm.looks.wear(LookKind.BADGE, null); advanceUntilIdle()
        assertNull(vm.looks.shop.value.shop!!.look!!.badge)
        assertNull("my @ID everywhere follows", vm.looks.people.value[me]!!.badge)
    }

    @Test fun looksArePurchasedThroughPlayAndNeverConsumed() = runTest(dispatcher) {
        val finished = mutableListOf<String>()
        val redeemed = mutableListOf<String>()
        var owned = listOf<Pair<String, String>>()
        val billing = object : Billing {
            override suspend fun prices() = mapOf("badge_crown" to "₹ 99.00")
            override suspend fun buy(activity: android.app.Activity, productId: String, accountId: String): String? = null
            override suspend fun finish(productId: String, token: String) { finished += token }
            override suspend fun ownedPurchases() = owned
        }
        val counting = object : Backend by fake {
            override suspend fun redeem(productId: String, purchaseToken: String): Entitlements {
                redeemed += purchaseToken
                return fake.redeem(productId, purchaseToken)
            }
        }
        val vm = signedIn(counting, billing)
        assertEquals("₹ 99.00", vm.state.value.prices["badge_crown"])
        // Paid, but the server's answer was lost: Play still lists the look; it is sent again, once.
        owned = listOf("badge_crown" to "play-token-crown")
        vm.restorePurchases(); advanceUntilIdle()
        assertEquals(listOf("play-token-crown"), redeemed)
        assertTrue("badge_crown" in fake.shop().owned)
        vm.resumed(); advanceUntilIdle()
        assertEquals("not again while the app runs", listOf("play-token-crown"), redeemed)
        assertTrue("a look is never consumed", finished.isEmpty())
        // Next start (a new app process): Play lists it for good, the server already counts it as mine: nothing sent.
        val next = vm(counting, billing)
        assertNotNull(next.state.value.me)
        assertEquals(listOf("play-token-crown"), redeemed)
        assertTrue(finished.isEmpty())
    }

    // ---- everyone's ID and look: one batched lookup ----

    @Test fun idsAndLooksOnScreenAreAskedTogetherAndKeptAWhile() = runTest(dispatcher) {
        val asked = mutableListOf<List<String>>()
        val counting = object : Backend by fake {
            override suspend fun looks(userIds: List<String>): List<Look> = fake.looks(userIds).also { asked += userIds }
        }
        val vm = signedIn(counting)
        vm.looks.want(listOf("p1", "p3"), now)
        vm.looks.want(listOf("p6", "p1"), now)
        advanceTimeBy(LooksController.BATCH_MS + 1); runCurrent()
        assertEquals("one request for everything asked within a frame", listOf(listOf("p1", "p3", "p6")), asked)
        val priya = vm.looks.people.value["p1"]!!
        assertEquals(Look("p1", "priya.mumbai", "badge_crown", "color_rose", "font_script", "frame_sakura"), priya)
        assertEquals(Look("p2", "lucas_sp"), fake.looks(listOf("p2")).single())
        vm.looks.want(listOf("p1"), System.currentTimeMillis())
        advanceUntilIdle()
        assertEquals("known ones wait", 1, asked.size)
        vm.block(FakeBackend.POOL.first { it.id == "p3" }); advanceUntilIdle()
        assertTrue("someone blocked is left out", fake.looks(listOf("p3")).isEmpty())
    }
}

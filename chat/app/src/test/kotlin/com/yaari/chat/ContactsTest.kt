package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.FirstRun
import com.yaari.chat.data.LinkBus
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.data.Push
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.ContactApp
import com.yaari.chat.model.PersonRow
import com.yaari.chat.model.Gender
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** 044: friends exchange messenger contacts; the fake keeps the server's rules (contacts_clean, share_contacts, unshare_on_unfriend). */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = { 1_000_000L }, today = { today }, voiceAmbient = false)
    private val p3 get() = FakeBackend.POOL.first { it.id == "p3" }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(backend: Backend = fake): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today }, photos = PhotoCheck.None, firstRun = FirstRun.Seen, push = Push.Off, links = LinkBus())
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Pune", "", agreed = true)); advanceUntilIdle()
        return vm
    }

    private suspend fun refused(code: String, block: suspend () -> Unit) {
        try {
            block(); fail("expected $code")
        } catch (e: BackendException) {
            assertEquals(code, e.code)
        }
    }

    @Test fun contactsAreCleanedLikeTheServerAndBadOnesNamed() = runTest(dispatcher) {
        signedIn()
        val stored = fake.setContacts(mapOf(ContactApp.WHATSAPP to " +91 98765-43210 ", ContactApp.INSTAGRAM to "@@asha.k", ContactApp.LINE to "  "))
        assertEquals(mapOf(ContactApp.WHATSAPP to "+919876543210", ContactApp.INSTAGRAM to "asha.k"), stored)
        assertEquals(stored, fake.myContacts().contacts)
        try {
            fake.setContacts(mapOf(ContactApp.TELEGRAM to "asha k")); fail()
        } catch (e: BackendException) {
            assertEquals("contact_invalid", e.code)
            assertEquals("telegram", e.detail)
        }
        assertEquals("a refused save keeps the old ones", stored, fake.myContacts().contacts)
        for ((app, v) in listOf(ContactApp.PHONE to "12345", ContactApp.PHONE to "++919876543210", ContactApp.DISCORD to "a".repeat(41), ContactApp.WECHAT to "प्रिया")) {
            assertNull("$app $v", app.clean(v))
        }
    }

    @Test fun linksOpenTheAppsAndTheRestAreCopied() {
        assertEquals("https://wa.me/919876543210", ContactApp.WHATSAPP.link("+919876543210"))
        assertEquals("tel:+919876543210", ContactApp.PHONE.link("+919876543210"))
        assertEquals("https://t.me/asha", ContactApp.TELEGRAM.link("asha"))
        assertEquals("https://line.me/ti/p/~asha", ContactApp.LINE.link("asha"))
        listOf(ContactApp.KAKAOTALK, ContactApp.WECHAT, ContactApp.DISCORD).forEach { assertNull(it.link("asha")) }
    }

    @Test fun sharingNeedsAFriendAndOnlyFriendsSeeAnything() = runTest(dispatcher) {
        val vm = signedIn()
        assertNull("not friends yet: nothing to show", fake.contactsOf(p3.id))
        refused("not_friends") { fake.shareContacts(p3.id, true) }
        vm.answerFriend(p3, true); advanceUntilIdle()
        vm.contacts.load(p3.id); advanceUntilIdle()
        val theirs = vm.contacts.state.value.of[p3.id]!!
        assertEquals("Ananya shares hers in the demo", "+919812345678", theirs.contacts?.get(ContactApp.WHATSAPP))
        assertFalse(theirs.mineShared)
        vm.contacts.share(p3.id, p3.name, true); advanceUntilIdle()
        assertTrue(vm.contacts.state.value.of[p3.id]!!.mineShared)
        assertEquals(setOf(p3.id), fake.myContacts().sharedWith)
        assertEquals(UiText(R.string.contacts_shared, p3.name), vm.state.value.notice)
    }

    @Test fun unfriendingEndsSharingBothWays() = runTest(dispatcher) {
        val vm = friendsWithAnanyaSharing()
        vm.removeFriend(p3); advanceUntilIdle()
        assertNull(vm.contacts.state.value.of[p3.id])
        assertNull(fake.contactsOf(p3.id))
        assertTrue(fake.myContacts().sharedWith.isEmpty())
        refused("not_friends") { fake.shareContacts(p3.id, true) }
    }

    @Test fun blockingEndsSharingBothWays() = runTest(dispatcher) {
        val vm = friendsWithAnanyaSharing()
        vm.block(p3); advanceUntilIdle()
        assertNull(vm.contacts.state.value.of[p3.id])
        assertTrue(fake.myContacts().sharedWith.isEmpty())
        vm.unblock(PersonRow(p3.id, "", p3.name, null)) {}; advanceUntilIdle()
        assertNull("unblocking doesn't bring back the friendship, so nothing shows", fake.contactsOf(p3.id))
    }

    private suspend fun TestScope.friendsWithAnanyaSharing(): AppViewModel {
        val vm = signedIn()
        vm.answerFriend(p3, true); advanceUntilIdle()
        vm.contacts.load(p3.id); advanceUntilIdle()
        vm.contacts.share(p3.id, p3.name, true); advanceUntilIdle()
        assertEquals(setOf(p3.id), fake.myContacts().sharedWith)
        return vm
    }

    @Test fun badInputIsRefusedHereAndTheServersRefusalNamesTheField() = runTest(dispatcher) {
        var saves = 0
        val strict = object : Backend by fake {
            override suspend fun setContacts(contacts: Map<ContactApp, String>): Map<ContactApp, String> {
                saves++
                throw BackendException("contact_invalid", "snapchat")
            }
        }
        val vm = signedIn(strict)
        vm.contacts.save(mapOf(ContactApp.PHONE to "12")); advanceUntilIdle()
        assertEquals(ContactApp.PHONE, vm.contacts.state.value.refused)
        assertEquals("never sent", 0, saves)
        var done = false
        vm.contacts.save(mapOf(ContactApp.SNAPCHAT to "asha")) { done = true }; advanceUntilIdle()
        assertEquals(1, saves)
        assertEquals(ContactApp.SNAPCHAT, vm.contacts.state.value.refused)
        assertFalse(done)
    }
}

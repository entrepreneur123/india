package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DeepLink
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.Entitlements
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.FirstRun
import com.yaari.chat.data.LinkBus
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.data.Products
import com.yaari.chat.data.Push
import com.yaari.chat.data.PushConfig
import com.yaari.chat.data.PushRegistrar
import com.yaari.chat.data.PushTokens
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Badges
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.InboxKind
import com.yaari.chat.model.Interest
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomQuery
import com.yaari.chat.model.Target
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.FirstRunStep
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.delay
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

/** 0.9.0: notifications and unread counts, room search and invites, interests, photos, the first run, push, refunds. */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 18)
    private val fake = FakeBackend(ControlledRandom(), clock = { 1_000_000L }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val links = LinkBus()

    private fun TestScope.signedIn(
        backend: Backend = fake, firstRun: FirstRun = FirstRun.Seen, photos: PhotoCheck = PhotoCheck.None, push: Push = Push.Off,
    ): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today }, photos = photos, firstRun = firstRun, push = push, links = links)
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Pune", "", agreed = true)); advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        return vm
    }

    private val p3 get() = FakeBackend.POOL.first { it.id == "p3" }

    // ---- notifications and unread counts ----

    @Test fun badgesCountTheWaitingRequestAndOpeningNotificationsReadsThem() = runTest(dispatcher) {
        val vm = signedIn()
        val b = vm.social.state.value.badges
        assertEquals(1, b.inbox)
        assertEquals(1, b.friends)
        assertEquals("Recent counts requests and unread chats", 1, b.recent)

        vm.social.openInbox(); advanceUntilIdle()
        val s = vm.social.state.value
        val item = s.inbox!!.single()
        assertEquals(InboxKind.FRIEND_REQUEST, item.kind)
        assertTrue("still waiting for my answer", item.pending)
        assertTrue("what was new stays highlighted while open", s.isNew(item))
        assertEquals("opening marks everything read", 0, s.badges.inbox)

        vm.inboxAnswer(item, accept = true); advanceUntilIdle()
        assertEquals(FriendState.FRIENDS, vm.state.value.friendState("p3"))
        assertFalse("answered: no longer pending", vm.social.state.value.inbox!!.single().pending)
        assertEquals(0, vm.social.state.value.badges.friends)

        vm.social.closeInbox()
        vm.social.openInbox(); advanceUntilIdle()
        assertFalse("reopened: nothing new", vm.social.state.value.isNew(vm.social.state.value.inbox!!.single()))
    }

    @Test fun aFriendsThreadCountsUnreadMessagesUntilRead() = runTest(dispatcher) {
        val vm = signedIn()
        vm.answerFriend(p3, true); advanceUntilIdle()
        val thread = fake.friendThread("p3")
        fake.partnerSays(thread, "hi!")
        fake.partnerSays(thread, "you there?")
        vm.social.refreshBadges(); advanceUntilIdle()
        assertEquals(2, vm.social.state.value.badges.unread(thread))
        assertEquals("one chat with unread messages", 1, vm.social.state.value.badges.recent)
        vm.social.openInbox(); advanceUntilIdle()
        val message = vm.social.state.value.inbox!!.first { it.kind == InboxKind.MESSAGE }
        assertEquals("a friend's messages fold into one line", 2, message.count)

        vm.inboxChat(message); advanceUntilIdle()
        assertFalse(vm.social.state.value.inboxOpen)
        assertEquals(thread, vm.state.value.dm?.recent?.matchId)
        vm.closeDm(); advanceUntilIdle()
        assertEquals("read on screen", 0, vm.social.state.value.badges.unread(thread))
    }

    /** A slow server: counts take 1 s, reads 2 s. A count asked before a read landed would bring its number back. */
    private val slow = object : Backend by fake {
        override suspend fun badges(): Badges = fake.badges().also { delay(1_000) }
        override suspend fun markRead(matchId: String, upto: Long) = delay(2_000).also { fake.markRead(matchId, upto) }
        override suspend fun markInboxRead(ids: List<Long>?) = delay(2_000).also { fake.markInboxRead(ids) }
    }

    @Test fun readingAChatClearsItsNumberAtOnceAndAStaleCountDoesNotBringItBack() = runTest(dispatcher) {
        val vm = signedIn(slow)
        vm.answerFriend(p3, true); advanceUntilIdle()
        val thread = fake.friendThread("p3")
        fake.partnerSays(thread, "hi!")
        vm.social.refreshBadges(); advanceUntilIdle()
        assertEquals(1, vm.social.state.value.badges.unread(thread))

        vm.social.refreshBadges(); runCurrent() // asked before the read: still counts the message
        vm.social.readChat(thread, Long.MAX_VALUE); runCurrent()
        assertEquals("gone at once", 0, vm.social.state.value.badges.unread(thread))
        assertEquals(0, vm.social.state.value.badges.recent)
        advanceTimeBy(1_500); runCurrent()
        assertEquals("the stale count is dropped", 0, vm.social.state.value.badges.unread(thread))
        advanceUntilIdle()
        assertEquals(0, vm.social.state.value.badges.unread(thread))
        assertEquals("the counts asked after the read", fake.badges(), vm.social.state.value.badges)
    }

    @Test fun openingNotificationsClearsTheBellAtOnceAndForGood() = runTest(dispatcher) {
        val vm = signedIn(slow)
        assertEquals(1, vm.social.state.value.badges.inbox)
        vm.social.refreshBadges(); runCurrent() // a poll on its way
        vm.social.openInbox(); runCurrent()
        assertEquals("gone at once", 0, vm.social.state.value.badges.inbox)
        advanceTimeBy(1_500); runCurrent()
        assertEquals(0, vm.social.state.value.badges.inbox)
        vm.social.closeInbox()
        vm.social.refreshBadges(); runCurrent() // asked while the read is still on its way
        advanceUntilIdle()
        assertEquals("still read after later counts", 0, vm.social.state.value.badges.inbox)
        assertEquals(0, fake.badges().inbox)
    }

    @Test fun anInviteLetsMeIntoTheRoomAndAnEndedOneSaysSo() = runTest(dispatcher) {
        val vm = signedIn()
        fake.notify(InboxKind.ROOM_INVITE, p3, roomId = "v-chai")
        vm.social.openInbox(); advanceUntilIdle()
        val invite = vm.social.state.value.inbox!!.first { it.kind == InboxKind.ROOM_INVITE }
        assertEquals("Chai pe charcha", invite.roomTitle)
        assertTrue(invite.roomOpen)
        vm.inboxJoin(invite); advanceUntilIdle()
        assertEquals("v-chai", vm.voice.state.value?.roomId)
        assertFalse(vm.social.state.value.inboxOpen)

        vm.voice.close(); advanceUntilIdle()
        assertTrue("an unknown invite", runCatching { fake.joinInvite(999) }.exceptionOrNull() is BackendException)
    }

    @Test fun inviteAFriendFromTheRoomImIn() = runTest(dispatcher) {
        val vm = signedIn()
        vm.answerFriend(p3, true); advanceUntilIdle()
        vm.voice.open("v-chai"); advanceUntilIdle()
        vm.social.invite("v-chai", p3); advanceUntilIdle()
        assertEquals(listOf("v-chai" to "p3"), fake.invited)
        assertEquals(setOf("p3"), vm.social.state.value.invited["v-chai"])
        vm.voice.close(); advanceUntilIdle()
        vm.social.invite("v-chai", p3); advanceUntilIdle()
        assertEquals("not in the room any more", UiText(R.string.err_not_allowed), vm.state.value.notice)
    }

    // ---- room search ----

    @Test fun roomSearchByWordsAndLanguageMostPeopleFirst() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.refreshList(); advanceUntilIdle()
        val all = vm.voice.list.value.rooms!!
        assertEquals("sorted by people present", all.sortedByDescending { it.members }.map { it.id }, all.map { it.id })
        assertEquals("hi", all.first { it.id == "v-chai" }.lang)

        vm.voice.search(RoomQuery("CHAI")); advanceUntilIdle()
        assertEquals(listOf("v-chai"), vm.voice.list.value.rooms!!.map { it.id })
        vm.voice.search(RoomQuery(lang = "bn")); advanceUntilIdle()
        assertEquals(listOf("v-adda"), vm.voice.list.value.rooms!!.map { it.id })
        vm.voice.search(RoomQuery(lang = "hi")); advanceUntilIdle()
        vm.voice.filter(RoomCategory.MUSIC)
        assertEquals("language and category together", listOf("v-bolly"), vm.voice.list.value.shown!!.map { it.id })
        vm.voice.search(RoomQuery("zzz", "hi")); advanceUntilIdle()
        assertTrue(vm.voice.list.value.rooms!!.isEmpty())
    }

    @Test fun theHostChangesTheRoomsLanguage() = runTest(dispatcher) {
        val vm = signedIn()
        vm.voice.create(com.yaari.chat.model.NewVoiceRoom("Bangla adda", RoomCategory.TALK, lang = "bn")); advanceUntilIdle()
        val id = vm.voice.state.value!!.roomId
        assertEquals("bn", vm.voice.state.value!!.room!!.lang)
        vm.voice.saveSettings("Bangla adda", "", "pt-BR"); advanceUntilIdle()
        assertEquals("pt-BR", vm.voice.state.value!!.room!!.lang)
        assertEquals("pt-BR", fake.voiceRooms(RoomQuery(lang = "pt-br")).single { it.id == id }.lang)
    }

    // ---- interests, photos, first run ----

    @Test fun interestsAreThreeToFive() = runTest(dispatcher) {
        val vm = signedIn()
        assertFalse(Interest.valid(Interest.entries.take(2)))
        assertTrue(Interest.valid(emptyList()))
        assertTrue(Interest.valid(Interest.entries.take(5)))
        assertFalse(Interest.valid(Interest.entries.take(6)))
        vm.social.saveInterests(Interest.entries.take(2).toSet()); advanceUntilIdle()
        vm.social.saveInterests(Interest.entries.take(6).toSet()); advanceUntilIdle()
        assertTrue("too few or too many: nothing sent", vm.state.value.me!!.interests.isEmpty())
        assertEquals("bad_request", runCatching { fake.setInterests(Interest.entries.take(6)) }.exceptionOrNull()?.message)
        vm.social.saveInterests(setOf(Interest.TRAVEL, Interest.CRICKET, Interest.MUSIC)); advanceUntilIdle()
        assertEquals("the server's order", listOf(Interest.MUSIC, Interest.CRICKET, Interest.TRAVEL), vm.state.value.me!!.interests)
        assertEquals(UiText(R.string.interests_saved), vm.state.value.notice)
    }

    @Test fun firstRunAsksInterestsOnceThenThreeCardsOnce() = runTest(dispatcher) {
        val seen = FirstRun.Memory()
        val vm = signedIn(firstRun = seen)
        assertEquals(FirstRunStep.INTERESTS, vm.social.state.value.firstRun)
        vm.social.skipInterests()
        assertEquals(FirstRunStep.TOUR, vm.social.state.value.firstRun)
        assertTrue(seen.done(FirstRun.Step.INTERESTS))
        vm.social.nextCard(); vm.social.nextCard()
        assertEquals(2, vm.social.state.value.tourCard)
        vm.social.nextCard()
        assertNull("the last card ends it", vm.social.state.value.firstRun)
        assertTrue(seen.done(FirstRun.Step.TOUR))
        vm.social.start(vm.state.value.me!!)
        assertNull("seen once: never again", vm.social.state.value.firstRun)

        val skipper = FirstRun.Memory(FirstRun.Step.INTERESTS)
        vm.social.reset()
        val other = AppViewModel(fake, DemoBilling(), firstRun = skipper)
        advanceUntilIdle()
        assertEquals("interests already asked: straight to the tour", FirstRunStep.TOUR, other.social.state.value.firstRun)
        other.social.endTour()
        assertTrue("Skip ends it too", skipper.done(FirstRun.Step.TOUR))
    }

    @Test fun profilePhotosAreCheckedOnThePhoneAndAtMostFour() = runTest(dispatcher) {
        var nude = true
        val checked = mutableListOf<String>()
        val check = object : PhotoCheck {
            override suspend fun shrink(uri: String) = "$uri.jpg"
            override suspend fun nude(jpegUri: String) = nude.also { checked += jpegUri }
        }
        val vm = signedIn(photos = check)
        vm.social.addPhoto("content://a", 0); advanceUntilIdle()
        assertEquals(UiText(R.string.photos_nude), vm.state.value.notice)
        assertTrue(vm.state.value.me!!.photos.isEmpty())
        nude = false
        repeat(4) { vm.social.addPhoto("content://p$it", vm.state.value.me!!.photos.size); advanceUntilIdle() }
        assertEquals(listOf("content://p0.jpg", "content://p1.jpg", "content://p2.jpg", "content://p3.jpg"), vm.state.value.me!!.photos.map { it.url })
        vm.social.addPhoto("content://p4", vm.state.value.me!!.photos.size); advanceUntilIdle()
        assertEquals(4, vm.state.value.me!!.photos.size)
        assertEquals(UiText(R.string.err_not_allowed), vm.state.value.notice)
        assertFalse("a fifth is not even checked", "content://p4.jpg" in checked)
        vm.social.removePhoto(vm.state.value.me!!.photos.first().id); advanceUntilIdle()
        assertEquals(3, vm.state.value.me!!.photos.size)
    }

    // ---- push ----

    private class FakeTokens(val token: String? = "fcm-token-0123456789abcdef") : PushTokens {
        var asked = 0
        override suspend fun token(): String? = token.also { asked++ }
    }

    private val config = PushConfig("1:1:android:abc", "key", "yaari-chat", "1234")

    @Test fun pushOffNeverTouchesFirebase() = runTest(dispatcher) {
        val tokens = FakeTokens()
        val off = PushRegistrar(PushConfig.OFF, { tokens }, fake, { "hi" }, { "Asia/Kolkata" })
        val vm = signedIn(push = off, firstRun = FirstRun.Memory(FirstRun.Step.INTERESTS, FirstRun.Step.TOUR))
        assertFalse(off.enabled)
        assertEquals(0, tokens.asked)
        assertNull(fake.deviceToken)
        vm.answerFriend(p3, true); advanceUntilIdle()
        assertFalse("no push: the permission is not asked for", vm.social.state.value.askNotifications)
        off.renewed("new-token-0123456789")
        assertNull(fake.deviceToken)
    }

    @Test fun pushOnRegistersAfterSignInAndAsksPermissionAfterTheFirstFriend() = runTest(dispatcher) {
        val tokens = FakeTokens()
        val seen = FirstRun.Memory(FirstRun.Step.INTERESTS, FirstRun.Step.TOUR)
        val on = PushRegistrar(config, { tokens }, fake, { "hi" }, { "Asia/Kolkata" })
        val vm = signedIn(push = on, firstRun = seen)
        assertEquals("fcm-token-0123456789abcdef", fake.deviceToken)
        assertFalse("not at launch", vm.social.state.value.askNotifications)
        vm.answerFriend(p3, true); advanceUntilIdle()
        assertTrue("after the first friend", vm.social.state.value.askNotifications)
        vm.social.notificationsAsked()
        assertFalse(vm.social.state.value.askNotifications)
        assertTrue(seen.done(FirstRun.Step.NOTIFICATIONS))
        vm.signOut(); advanceUntilIdle()
        assertNull("signed out: no pushes to this phone", fake.deviceToken)
    }

    // ---- links ----

    @Test fun aRoomLinkWaitsThroughSignUp() = runTest(dispatcher) {
        links.send(DeepLink.parse("https://yaari-web-indol.vercel.app/r/v-cricket"))
        val vm = signedIn()
        advanceUntilIdle()
        assertEquals("v-cricket", vm.voice.state.value?.roomId)
        assertNull(links.pending.value)
        vm.voice.close(); advanceUntilIdle()
        links.send(DeepLink.Inbox); advanceUntilIdle()
        assertTrue("a tapped friend-request push opens Notifications", vm.social.state.value.inboxOpen)
    }

    // ---- Plus refunds: the server ends it, the app follows on resume ----

    @Test fun aRefundedPlusEndsOnResume() = runTest(dispatcher) {
        var revoked = false
        val server = object : Backend by fake {
            override suspend fun entitlements() = if (revoked) Entitlements() else fake.entitlements()
        }
        val vm = signedIn(server)
        fake.redeem(Products.PREMIUM, "token-1")
        vm.resumed(); advanceUntilIdle()
        assertTrue(vm.state.value.premium)
        vm.setTarget(Target.FEMALE)
        revoked = true
        vm.resumed(); advanceUntilIdle()
        assertFalse(vm.state.value.premium)
        assertEquals("Plus-only choice reset", Target.ANY, vm.state.value.target)
    }
}

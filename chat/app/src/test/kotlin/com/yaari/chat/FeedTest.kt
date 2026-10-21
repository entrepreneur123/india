package com.yaari.chat

import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.data.DeepLink
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.data.PushChannel
import com.yaari.chat.data.PushMessage
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Accent
import com.yaari.chat.model.FeedTab
import com.yaari.chat.model.FriendState
import com.yaari.chat.model.Gender
import com.yaari.chat.model.InboxKind
import com.yaari.chat.model.LikeState
import com.yaari.chat.model.PostVisibility
import com.yaari.chat.model.ProfilePrompt
import com.yaari.chat.model.ProfileStyle
import com.yaari.chat.model.PromptQ
import com.yaari.chat.model.ReportReason
import com.yaari.chat.model.SpokenLang
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** 0.11.0: profile style, posts with hearts and comments, the feed, and their notifications (033_profiles_feed.sql). */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 18)
    private var now = 10_000_000_000L
    private val fake = FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.signedIn(backend: Backend = fake, photos: PhotoCheck = PhotoCheck.None): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today }, photos = photos)
        advanceUntilIdle()
        vm.requestOtp(Countries.byIso("IN")!!, "9876543210"); advanceUntilIdle()
        vm.verifyOtp(FakeBackend.DEMO_OTP); advanceUntilIdle()
        vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "2000", "Pune", "", agreed = true)); advanceUntilIdle()
        assertNotNull(vm.state.value.me)
        return vm
    }

    // ---- the feed ----

    @Test fun discoverShowsPublicPostsNewestFirstWithAuthorsAndCounts() = runTest(dispatcher) {
        val vm = signedIn()
        vm.selectTab(Tab.FEED); advanceUntilIdle()
        assertEquals("Friends: nobody yet", emptyList<Any>(), vm.feed.state.value.page(FeedTab.FRIENDS).posts)
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        val posts = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!
        assertTrue(posts.isNotEmpty())
        assertTrue(posts.all { it.visibility == PostVisibility.PUBLIC && it.author != null })
        assertEquals(posts.sortedByDescending { it.createdAt }, posts)
        assertTrue("seeded hearts", posts.any { it.likes > 0 })
        assertEquals(Accent.ROSE, posts.first { it.id == "p1-0" }.author!!.style.accent)
        assertTrue(vm.feed.state.value.page(FeedTab.DISCOVER).end)
    }

    @Test fun friendsFeedHasMyAndMyFriendsPostsAndANewPostGoesOnTop() = runTest(dispatcher) {
        val vm = signedIn()
        vm.answerFriend(FakeBackend.POOL.first { it.id == "p3" }, true); advanceUntilIdle()
        vm.selectTab(Tab.FEED); advanceUntilIdle()
        val before = vm.feed.state.value.page(FeedTab.FRIENDS).posts!!
        assertTrue(before.isNotEmpty() && before.all { it.author?.id == "p3" })
        vm.addPost("Hello feed", null, PostVisibility.PUBLIC); advanceUntilIdle()
        assertEquals("Hello feed", vm.feed.state.value.page(FeedTab.FRIENDS).posts!!.first().text)
        assertEquals("Hello feed", vm.state.value.me!!.posts.first().text)
        assertEquals(UiText(com.yaari.chat.R.string.msg_posted), vm.state.value.notice)
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        assertEquals("public: in Discover too", "Hello feed", vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first().text)
    }

    @Test fun twentyPostsADayThenSlowDown() = runTest(dispatcher) {
        val vm = signedIn()
        repeat(20) { vm.addPost("post $it", null); advanceUntilIdle() }
        assertEquals(20, vm.state.value.me!!.posts.size)
        vm.addPost("one more", null); advanceUntilIdle()
        assertEquals(UiText(com.yaari.chat.R.string.err_slow_down), vm.state.value.notice)
        assertEquals(20, vm.state.value.me!!.posts.size)
    }

    @Test fun pagesFollowTheOldestPostShown() = runTest(dispatcher) {
        signedIn()
        val all = mutableListOf<String>()
        repeat(25) { fake.addPost("p$it", null, PostVisibility.FRIENDS); now += 5_000_000L } // under 20 a day
        val first = fake.feed(FeedTab.FRIENDS)
        assertEquals(20, first.size)
        val second = fake.feed(FeedTab.FRIENDS, first.last())
        assertEquals(5, second.size)
        all += (first + second).map { it.text }
        assertEquals((24 downTo 0).map { "p$it" }, all)
    }

    // ---- hearts ----

    @Test fun heartIsOptimisticAndTheServerSettlesIt() = runTest(dispatcher) {
        val vm = signedIn()
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        val p = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == "p1-0" }
        vm.feed.toggleLike(p)
        val now1 = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == p.id }
        assertTrue(now1.liked)
        assertEquals(p.likes + 1, now1.likes)
        advanceUntilIdle()
        assertEquals(p.likes + 1, fake.post(p.id)!!.likes)
        vm.feed.like(now1); advanceUntilIdle() // double tap on a liked post changes nothing
        assertEquals(p.likes + 1, fake.post(p.id)!!.likes)
        vm.feed.toggleLike(now1); advanceUntilIdle()
        assertFalse(fake.post(p.id)!!.liked)
        assertEquals(p.likes, vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == p.id }.likes)
    }

    @Test fun aFailedHeartGoesBack() = runTest(dispatcher) {
        val broken = object : FakeBackend(ControlledRandom(), clock = { now }, today = { today }, voiceAmbient = false) {
            override suspend fun likePost(postId: String, on: Boolean): LikeState = throw BackendException("network")
        }
        val vm = signedIn(broken)
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        val p = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first()
        vm.feed.toggleLike(p); advanceUntilIdle()
        val back = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == p.id }
        assertEquals(p.liked, back.liked)
        assertEquals(p.likes, back.likes)
        assertEquals(UiText(com.yaari.chat.R.string.err_network), vm.state.value.notice)
    }

    // ---- comments ----

    @Test fun commentsWithRepliesCountsAndRemoval() = runTest(dispatcher) {
        val vm = signedIn()
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        val p = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == "p1-0" }
        vm.feed.openComments(p); advanceUntilIdle()
        val list = vm.feed.state.value.thread!!.comments!!
        assertEquals(3, list.size)
        assertEquals("Emma", list[1].replyName)
        assertFalse(list.any { it.canRemove })
        vm.feed.replyTo(list[0])
        var cleared = false
        vm.feed.send("  Same here!  ") { cleared = true }; advanceUntilIdle()
        val t = vm.feed.state.value.thread!!
        assertTrue(cleared)
        assertNull(t.replyTo)
        val mine = t.comments!!.last()
        assertEquals("Same here!", mine.body)
        assertEquals(list[0].id, mine.replyTo)
        assertTrue(mine.mine && mine.canRemove)
        assertEquals(p.comments + 1, vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == p.id }.comments)
        vm.feed.remove(mine); advanceUntilIdle()
        assertEquals(3, vm.feed.state.value.thread!!.comments!!.size)
        assertEquals(p.comments, vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == p.id }.comments)
    }

    @Test fun tenCommentsAMinute() = runTest(dispatcher) {
        val vm = signedIn()
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        vm.feed.openComments(vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first()); advanceUntilIdle()
        repeat(10) { vm.feed.send("c$it"); advanceUntilIdle() }
        vm.feed.send("eleven"); advanceUntilIdle()
        assertEquals(UiText(com.yaari.chat.R.string.err_slow_down), vm.state.value.notice)
        assertFalse(vm.feed.state.value.thread!!.comments!!.any { it.body == "eleven" })
    }

    @Test fun commentsOnMyPostCanBeRemovedByMeAndNotifyMe() = runTest(dispatcher) {
        val vm = signedIn()
        vm.addPost("My post", null, PostVisibility.PUBLIC); advanceUntilIdle()
        val post = vm.state.value.me!!.posts.first()
        fake.reactToPost(post.id, "p1")
        fake.reactToPost(post.id, "p5")
        fake.reactToPost(post.id, "p7", "Nice one")
        vm.social.openInbox(); advanceUntilIdle()
        val items = vm.social.state.value.inbox!!
        val likes = items.first { it.kind == InboxKind.POST_LIKE }
        assertEquals("one line per post", 3, likes.count)
        assertEquals("the newest liker", "p7", likes.actor.id)
        assertEquals(post.id, likes.post?.id)
        val comment = items.first { it.kind == InboxKind.POST_COMMENT }
        vm.inboxPost(comment); advanceUntilIdle()
        assertFalse(vm.social.state.value.inboxOpen)
        assertEquals(post.id, vm.feed.state.value.detail?.id)
        val c = vm.feed.state.value.thread!!.comments!!.single()
        assertTrue("under my post", c.canRemove && !c.mine)
        vm.feed.remove(c); advanceUntilIdle()
        assertEquals(0, vm.state.value.me!!.posts.first().comments)
    }

    // ---- delete, report, block ----

    @Test fun deletingMyPostRemovesItEverywhere() = runTest(dispatcher) {
        val vm = signedIn()
        vm.addPost("Oops", null); advanceUntilIdle()
        vm.selectTab(Tab.FEED); advanceUntilIdle()
        val post = vm.feed.state.value.page(FeedTab.FRIENDS).posts!!.first()
        vm.feed.openDetail(post)
        vm.feed.delete(post); advanceUntilIdle()
        assertTrue(vm.state.value.me!!.posts.isEmpty())
        assertTrue(vm.feed.state.value.page(FeedTab.FRIENDS).posts!!.isEmpty())
        assertNull(vm.feed.state.value.detail)
        assertEquals(UiText(com.yaari.chat.R.string.msg_post_deleted), vm.state.value.notice)
    }

    @Test fun reportingHidesThePostAndCommentAndKeepsTheReason() = runTest(dispatcher) {
        val vm = signedIn()
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        val p = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == "p1-0" }
        vm.feed.openComments(p); advanceUntilIdle()
        val c = vm.feed.state.value.thread!!.comments!!.first()
        vm.feed.report(c, ReportReason.HARASSMENT); advanceUntilIdle()
        assertFalse(vm.feed.state.value.thread!!.comments!!.any { it.id == c.id })
        vm.feed.report(p, ReportReason.FAKE); advanceUntilIdle()
        assertEquals(listOf("comment:${c.id}" to ReportReason.HARASSMENT, "post:p1-0" to ReportReason.FAKE), fake.postReports)
        assertNull(vm.feed.state.value.thread)
        vm.feed.refresh(); advanceUntilIdle()
        assertFalse("stays hidden after a refresh", vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.any { it.id == "p1-0" })
    }

    @Test fun publicPostersCanBeOpenedAndBlockingClearsTheirPosts() = runTest(dispatcher) {
        val vm = signedIn()
        vm.feed.select(FeedTab.DISCOVER); advanceUntilIdle()
        val priya = vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first { it.id == "p1-0" }.author!!
        vm.openProfile(priya); advanceUntilIdle()
        val p = vm.state.value.viewing!!
        assertEquals("Chai first, then everything else", p.style.status)
        assertEquals(3, p.style.prompts.size)
        assertEquals(listOf(SpokenLang.EN, SpokenLang.HI, SpokenLang.MR), p.style.languages)
        vm.addFriend(p); advanceUntilIdle()
        assertEquals("a public poster can be asked", FriendState.REQUESTED, vm.state.value.friendState(p.id))
        vm.block(p); advanceUntilIdle()
        assertFalse(vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.any { it.author?.id == p.id })
    }

    // ---- profile style ----

    @Test fun styleIsSavedWholeAndShownOnMyProfile() = runTest(dispatcher) {
        val vm = signedIn()
        val style = ProfileStyle(
            accent = Accent.OCEAN, status = "  Sunsets and chai ", languages = listOf(SpokenLang.HI, SpokenLang.EN),
            prompts = listOf(ProfilePrompt(PromptQ.FOOD, "Vada pav"), ProfilePrompt(PromptQ.GOAL, "Learn the guitar")),
        )
        var done = false
        vm.social.saveStyle(style, "file:///cover.jpg") { done = true }; advanceUntilIdle()
        assertTrue(done)
        val me = vm.state.value.me!!.style
        assertEquals(Accent.OCEAN, me.accent)
        assertEquals("Sunsets and chai", me.status)
        assertEquals("file:///cover.jpg", me.cover)
        assertEquals(2, me.prompts.size)
        // Removing the cover keeps the rest.
        vm.social.saveStyle(me.copy(cover = null, coverId = null), null); advanceUntilIdle()
        assertNull(vm.state.value.me!!.style.cover)
        assertEquals(Accent.OCEAN, vm.state.value.me!!.style.accent)
    }

    @Test fun badStylesAndNudeCoversAreRefused() = runTest(dispatcher) {
        val nude = object : PhotoCheck {
            override suspend fun shrink(uri: String) = uri
            override suspend fun nude(jpegUri: String) = true
        }
        val vm = signedIn(photos = nude)
        vm.social.saveStyle(ProfileStyle(prompts = listOf(ProfilePrompt(PromptQ.FOOD, "a"), ProfilePrompt(PromptQ.FOOD, "b"))), null); advanceUntilIdle()
        assertEquals(UiText(com.yaari.chat.R.string.style_bad), vm.state.value.notice)
        vm.social.saveStyle(ProfileStyle(languages = SpokenLang.entries.take(7)), null); advanceUntilIdle()
        assertEquals(UiText(com.yaari.chat.R.string.style_bad), vm.state.value.notice)
        vm.social.saveStyle(ProfileStyle(accent = Accent.MINT), "file:///nude.jpg"); advanceUntilIdle()
        assertEquals(UiText(com.yaari.chat.R.string.photos_nude), vm.state.value.notice)
        assertNull("nothing saved", vm.state.value.me!!.style.accent)
        assertFalse(vm.social.state.value.savingStyle)
    }

    @Test fun styleRules() {
        assertTrue(ProfileStyle().valid())
        assertFalse(ProfileStyle(status = "x".repeat(61)).valid())
        assertFalse(ProfileStyle(prompts = PromptQ.entries.take(4).map { ProfilePrompt(it, "a") }).valid())
        assertFalse(ProfileStyle(prompts = listOf(ProfilePrompt(PromptQ.SONG, " "))).valid())
        assertEquals(listOf(SpokenLang.EN, SpokenLang.VI), SpokenLang.of(listOf("vi", "xx", "en")))
        assertEquals(Accent.SLATE, Accent.of("slate"))
        assertNull(Accent.of("neon"))
        assertEquals(PostVisibility.FRIENDS, PostVisibility.of(null))
    }

    // ---- notifications and push ----

    @Test fun unknownInboxKindsAreIgnoredAndPostPushesGoToSocial() {
        assertNull(InboxKind.of("post_share"))
        val like = PushMessage.parse(mapOf("kind" to "post_like", "id" to "9", "actor" to "u-1", "actor_name" to "Priya", "n" to "3"))!!
        assertEquals(PushChannel.SOCIAL, like.channel)
        assertEquals(DeepLink.Inbox, like.link)
        assertEquals(3, like.count)
        val comment = PushMessage.parse(mapOf("kind" to "post_comment", "id" to "10", "actor" to "u-1", "actor_name" to "Priya"))!!
        assertEquals(PushChannel.SOCIAL, comment.channel)
        assertNull(PushMessage.parse(mapOf("kind" to "post_share", "actor_name" to "Priya")))
    }
}

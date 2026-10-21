package com.yaari.chat

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.domain.Countries
import com.yaari.chat.model.Accent
import com.yaari.chat.model.FeedTab
import com.yaari.chat.model.Gender
import com.yaari.chat.model.PostVisibility
import com.yaari.chat.model.ProfilePrompt
import com.yaari.chat.model.ProfileStyle
import com.yaari.chat.model.PromptQ
import com.yaari.chat.model.SpokenLang
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.ProfileDraft
import com.yaari.chat.ui.Tab
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 0.11.0 screens: the feed, a post's comments, decorated profiles and their editor, post notifications. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class FeedScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val backend = FakeBackend(kotlin.random.Random(11), today = { today }, instant = true)

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/feed_$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun signIn(): AppViewModel {
        val vm = AppViewModel(backend, DemoBilling(), today = { today })
        compose.setContent { YaariTheme { YaariApp(vm, online = MutableStateFlow(true)) } }
        waitFor("Mobile number")
        compose.runOnUiThread { vm.requestOtp(Countries.byIso("IN")!!, "9876543210") }
        waitFor("6-digit code")
        compose.runOnUiThread { vm.verifyOtp(FakeBackend.DEMO_OTP) }
        waitFor("Create your profile")
        compose.runOnUiThread { vm.createProfile(ProfileDraft("Asha", Gender.FEMALE, "1", "1", "1998", "Pune", "Chai and long walks", agreed = true)) }
        compose.waitUntil(5_000) { vm.state.value.me != null }
        return vm
    }

    private val myStyle = ProfileStyle(
        accent = Accent.VIOLET, status = "Collecting sunsets", languages = listOf(SpokenLang.HI, SpokenLang.EN, SpokenLang.MR),
        prompts = listOf(
            ProfilePrompt(PromptQ.WEEKEND, "Sinhagad at sunrise, then misal pav"),
            ProfilePrompt(PromptQ.TALK, "You send me songs I have never heard"),
        ),
    )

    @Test fun friendsAndDiscover() {
        val vm = signIn()
        compose.runOnUiThread { vm.answerFriend(FakeBackend.POOL.first { it.id == "p3" }, true) }
        compose.waitUntil(5_000) { vm.state.value.friends.isNotEmpty() }
        compose.onNodeWithText("Feed").performClick()
        waitFor("What’s on your mind?")
        waitFor("Rehearsal day")
        shot("01_friends")
        compose.onNodeWithText("Discover").performClick()
        waitFor("Marine Drive evenings")
        shot("02_discover")
        // A heart: outline to red, the count goes up.
        compose.onAllNodes(hasContentDescription("Like")).onFirst().performClick()
        compose.waitUntil(5_000) { vm.feed.state.value.page(FeedTab.DISCOVER).posts!!.first().liked }
        shot("03_liked")
    }

    @Test fun commentsSheet() {
        val vm = signIn()
        compose.runOnUiThread { vm.selectTab(Tab.FEED); vm.feed.select(FeedTab.DISCOVER) }
        waitFor("Marine Drive evenings")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("View all 3 comments"))
        compose.onAllNodes(hasText("View all 3 comments")).onFirst().performClick()
        waitFor("Golden hour hits different")
        shot("04_comments")
        compose.onAllNodes(hasText("Reply")).onFirst().performClick()
        waitFor("Replying to Emma")
        compose.onNode(hasSetTextAction()).performTextInput("Adding it to my list")
        shot("05_reply")
        compose.onNode(hasContentDescription("Send")).performClick()
        compose.waitUntil(5_000) { vm.feed.state.value.thread?.comments?.any { it.body == "Adding it to my list" } == true }
        waitFor("Reply to Emma")
    }

    @Test fun decoratedProfilesAndTheEditor() {
        val vm = signIn()
        runBlocking { backend.setProfileStyle(myStyle) }
        compose.runOnUiThread { vm.social.removePhoto("none") } // reloads my profile with the style
        compose.waitUntil(5_000) { vm.state.value.me?.style?.accent == Accent.VIOLET }
        compose.onNodeWithText("Me").performClick()
        waitFor("Collecting sunsets")
        shot("06_my_profile")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sinhagad", substring = true))
        shot("07_my_prompts")
        compose.onAllNodes(hasText("Customise")).onFirst().performClick()
        waitFor("Profile colour")
        shot("08_editor")
        compose.onNode(hasContentDescription("Ocean")).performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { vm.state.value.me?.style?.accent == Accent.OCEAN }
        assertEquals(myStyle.prompts, vm.state.value.me!!.style.prompts)
        // Someone else's: a public poster's sheet from Discover.
        compose.runOnUiThread { vm.openProfile(FakeBackend.POOL.first { it.id == "p1" }) }
        waitFor("Chai first, then everything else")
        shot("09_their_profile")
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Kesariya", substring = true))
        shot("10_their_prompts")
    }

    @Test fun composerWithAudience() {
        signIn()
        compose.onNodeWithText("Feed").performClick()
        waitFor("What’s on your mind?")
        compose.onNodeWithText("What’s on your mind?").performClick()
        waitFor("Who can see this?")
        compose.onNodeWithText("Everyone").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Monsoon is here and so am I")
        shot("11_new_post")
        compose.onNodeWithText("Share").performClick()
        waitFor("Monsoon is here and so am I")
        shot("12_my_post_in_feed")
    }

    @Test fun likeAndCommentNotifications() {
        val vm = signIn()
        val post = runBlocking { backend.addPost("Sunday market finds", FakeBackend.ART_CITY, PostVisibility.PUBLIC) }
        backend.reactToPost(post.id, "p1")
        backend.reactToPost(post.id, "p5")
        backend.reactToPost(post.id, "p7", "Where is this?")
        compose.runOnUiThread { vm.social.openInbox() }
        waitFor("and 2 others liked your post")
        shot("13_inbox")
        compose.onNodeWithText("Emma commented on your post").performClick()
        waitFor("Where is this?")
        shot("14_post_from_inbox")
        assertTrue(vm.feed.state.value.detail?.id == post.id)
    }
}

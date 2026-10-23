package com.yaari.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yaari.chat.ui.InlineVideo
import com.yaari.chat.ui.LinkActions
import com.yaari.chat.ui.openLink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A YouTube link tapped in a chat plays in its card under the link (LinkViewer.kt openLink, VideoCard). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InlineVideoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val links = LinkActions(null)

    @After fun tearDown() {
        InlineVideo.playing = null
        InlineVideo.shown.clear()
    }

    @Test fun aVideoWithItsCardOnScreenPlaysThereAndNothingOpens() {
        InlineVideo.shown += "9-98_BCj8Fk"
        openLink(context, links, "https://www.youtube.com/watch?v=9-98_BCj8Fk")
        assertEquals("9-98_BCj8Fk", InlineVideo.playing)
        assertNull(shadowOf(context as android.app.Application).nextStartedActivity)
        assertNull(links.shown)
    }

    @Test fun aVideoWithNoCardGoesToThePhoneAndAPageToTheViewer() {
        openLink(context, links, "https://youtu.be/9-98_BCj8Fk")
        assertNull(InlineVideo.playing)
        assertEquals("https://youtu.be/9-98_BCj8Fk", shadowOf(context as android.app.Application).nextStartedActivity?.dataString)
        openLink(context, links, "https://example.com/a")
        assertEquals("https://example.com/a", links.shown?.url)
    }
}

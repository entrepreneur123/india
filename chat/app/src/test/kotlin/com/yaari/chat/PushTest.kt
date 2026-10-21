package com.yaari.chat

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import org.robolectric.RuntimeEnvironment
import com.yaari.chat.data.DeepLink
import com.yaari.chat.data.PushChannel
import com.yaari.chat.data.PushMessage
import com.yaari.chat.model.InboxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Room links, and a push's data (functions/push) to the notification and where tapping it goes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    // ---- links ----

    @Test fun roomLinksById_codeAndTheOlderVoiceParameter() {
        assertEquals(DeepLink.Room("v-3f2a9c"), DeepLink.parse("https://yaari-web-indol.vercel.app/r/v-3f2a9c"))
        assertEquals(DeepLink.Code("AB12CD"), DeepLink.parse("https://yaari-web-indol.vercel.app/r/ab12cd/"))
        assertEquals(DeepLink.Room("v-chai"), DeepLink.parse("https://yaari-web-indol.vercel.app/?voice=v-chai"))
        assertNull("another site", DeepLink.parse("https://evil.example/r/v-chai"))
        assertNull("not a room", DeepLink.parse("https://yaari-web-indol.vercel.app/r/hello-world"))
        assertNull("deeper path", DeepLink.parse("https://yaari-web-indol.vercel.app/r/v-chai/x"))
        assertNull(DeepLink.parse("not a link"))
        assertNull(DeepLink.parse(null))
    }

    @Test fun sharedLinksUseTheCodeOnlyForPrivateRooms() {
        assertEquals("https://yaari-web-indol.vercel.app/r/v-chai", DeepLink.roomLink("v-chai", "AB12CD", isPrivate = false))
        assertEquals("https://yaari-web-indol.vercel.app/r/AB12CD", DeepLink.roomLink("v-chai", "AB12CD", isPrivate = true))
        listOf(
            DeepLink.Room("v-chai"), DeepLink.Code("AB12CD"), DeepLink.Invite(42, "v-chai"), DeepLink.Inbox,
            DeepLink.Thread("0b6c1f0e-1d2a-4c5e-9f00-1234567890ab", "u-1", "Sofía & Co"), DeepLink.Thread("m-1", null, ""),
        ).forEach { assertEquals("round trip $it", it, DeepLink.parse(it.uri())) }
    }

    // ---- push data ----

    private fun data(vararg pairs: Pair<String, String>) = mapOf("channel" to "x", "actor" to "u-1", "actor_name" to "Sofia", "id" to "7") + pairs

    @Test fun pushDataToMessageChannelAndLink() {
        val msg = PushMessage.parse(data("kind" to "message", "n" to "2", "match" to "m-1"))!!
        assertEquals(PushChannel.MESSAGES, msg.channel)
        assertEquals(DeepLink.Thread("m-1", "u-1", "Sofia"), msg.link)
        val invite = PushMessage.parse(data("kind" to "room_invite", "room" to "v-chai"))!!
        assertEquals(PushChannel.ROOMS, invite.channel)
        assertEquals(DeepLink.Invite(7, "v-chai"), invite.link)
        val request = PushMessage.parse(data("kind" to "friend_request"))!!
        assertEquals(PushChannel.SOCIAL, request.channel)
        assertEquals(DeepLink.Inbox, request.link)
        assertEquals(PushChannel.SOCIAL, PushMessage.parse(data("kind" to "friend_accept"))!!.channel)
        assertNull("unknown kind", PushMessage.parse(data("kind" to "promo")))
        assertNull("no name", PushMessage.parse(data("kind" to "message") + ("actor_name" to " ")))
        assertEquals("missing n is one", 1, PushMessage.parse(data("kind" to "message"))!!.count)
        assertNotEquals("one notification per chat", msg.key, PushMessage.parse(data("kind" to "message", "match" to "m-2"))!!.key)
    }

    @Test fun notificationTextIsWrittenInTheAppsLanguage() {
        fun text(vararg p: Pair<String, String>) = PushNotifier.text(context, PushMessage.parse(data(*p))!!)
        assertEquals("Sofia sent you 3 messages", text("kind" to "message", "n" to "3", "match" to "m"))
        assertEquals("Sofia sent you a message", text("kind" to "message", "match" to "m"))
        assertEquals("Sofia wants to be friends", text("kind" to "friend_request"))
        assertEquals("Sofia accepted your friend request", text("kind" to "friend_accept"))
        assertEquals("Sofia invited you to a room", text("kind" to "room_invite", "room" to "v-chai"))
    }

    @Test fun shownOnItsChannelAndTappingOpensTheChat() {
        val msg = PushMessage.parse(data("kind" to "message", "n" to "2", "match" to "m-1"))!!
        PushNotifier.show(context, msg)
        val manager = context.getSystemService(NotificationManager::class.java)
        val shown = shadowOf(manager).allNotifications.single()
        assertEquals("messages", shown.channelId)
        assertEquals("Sofia sent you 2 messages", shown.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        val tap = shadowOf(shown.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component?.className)
        assertEquals(DeepLink.Thread("m-1", "u-1", "Sofia"), DeepLink.parse(tap.dataString))
        assertTrue("three channels", PushChannel.entries.all { manager.getNotificationChannel(it.id) != null })

        PushNotifier.show(context, msg.copy(count = 3))
        assertEquals("a newer push of the same chat replaces it", 1, shadowOf(manager).allNotifications.size)
    }
}

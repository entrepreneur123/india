package com.yaari.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.yaari.chat.data.DeepLink
import com.yaari.chat.data.PushChannel
import com.yaari.chat.data.PushMessage
import com.yaari.chat.model.InboxKind

/**
 * Turns a push ([PushMessage]: ids and a name only) into a notification in the app's language, on its channel
 * (messages / social / rooms, each mutable on Android 8+). Tapping opens MainActivity with the [DeepLink].
 */
object PushNotifier {
    /** The sentence, as in Notifications. */
    fun text(context: Context, m: PushMessage): String = when (m.kind) {
        InboxKind.MESSAGE ->
            if (m.count > 1) context.resources.getQuantityString(R.plurals.inbox_messages, m.count, m.actorName, m.count)
            else context.getString(R.string.inbox_message_one, m.actorName)
        InboxKind.FRIEND_REQUEST -> context.getString(R.string.inbox_request, m.actorName)
        InboxKind.FRIEND_ACCEPT -> context.getString(R.string.inbox_accept, m.actorName)
        InboxKind.ROOM_INVITE -> context.getString(R.string.push_invite, m.actorName)
    }

    fun channelName(context: Context, c: PushChannel) = context.getString(
        when (c) {
            PushChannel.MESSAGES -> R.string.push_channel_messages
            PushChannel.SOCIAL -> R.string.push_channel_social
            PushChannel.ROOMS -> R.string.push_channel_rooms
        },
    )

    /** The channels, named in the app's language (created again when the language changes: the names follow). */
    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            PushChannel.entries.map { c ->
                val importance = if (c == PushChannel.SOCIAL) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
                NotificationChannel(c.id, channelName(context, c), importance)
            },
        )
    }

    /** What tapping it opens: the app, with the link (only MainActivity reads it; nothing outside can send these). */
    fun intent(context: Context, link: DeepLink): Intent =
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(link.uri().toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun build(context: Context, m: PushMessage): Notification {
        val open = PendingIntent.getActivity(
            context, m.key.hashCode(), intent(context, m.link), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, m.channel.id)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(text(context, m))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(if (m.kind == InboxKind.MESSAGE) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(if (m.channel == PushChannel.SOCIAL) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            // The lock screen shows that something came, not from whom.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    /** Shows it (replacing the older one of the same chat, person or room); nothing without the permission. */
    fun show(context: Context, m: PushMessage) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        channels(context)
        try {
            manager.notify(TAG, m.key.hashCode(), build(context, m))
        } catch (_: SecurityException) {
            Unit // the permission was taken back meanwhile
        }
    }

    private const val TAG = "yaari"
}

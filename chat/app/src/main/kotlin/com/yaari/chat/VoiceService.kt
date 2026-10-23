package com.yaari.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yaari.chat.data.VoiceBus
import com.yaari.chat.ui.AppViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Keeps a room's audio or music alive while the room is minimised or the app is in the background (as X Spaces does): a
 * foreground service of type mediaPlayback (the room's audio), plus microphone while I may talk, with the ongoing
 * "In a voice room · Leave" notification. It holds no state of its own: the room lives in [AppViewModel]; the service
 * only follows it ([Follow]) and passes the notification's buttons back through [VoiceBus].
 */
class VoiceService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LEAVE -> {
                VoiceBus.send(VoiceBus.Request.LEAVE)
                return START_NOT_STICKY
            }
            ACTION_SHOW -> show(
                intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getBooleanExtra(EXTRA_HOST, false), intent.getBooleanExtra(EXTRA_MIC, false),
            )
        }
        // Not restarted after the process dies: the room went with it.
        return START_NOT_STICKY
    }

    private fun show(title: String, host: Boolean, mic: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.voice_notif_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 1, activityIntent(this, end = false), immutable)
        // A listener leaves from here; the host's room would end, so the host gets the room with its confirm instead.
        val leave = if (host) PendingIntent.getActivity(this, 2, activityIntent(this, end = true), immutable)
        else PendingIntent.getService(this, 3, Intent(this, VoiceService::class.java).setAction(ACTION_LEAVE), immutable)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setColor(getColor(R.color.brand_saffron))
            .setContentTitle(title.ifEmpty { getString(R.string.voice_notif_text) })
            .setContentText(getString(if (host) R.string.voice_notif_text_host else R.string.voice_notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(if (host) R.string.voice_end_confirm else R.string.voice_leave), leave)
            .build()
        val playback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        val microphone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        val withMic = mic && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, playback or (if (withMic) microphone else 0))
        } catch (e: RuntimeException) {
            // Not allowed right now (started from the background, or the microphone type refused): listening still works
            // while the app is open; try without the microphone, else give up quietly.
            runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, playback) }.onFailure { stopSelf() }
        }
    }

    companion object {
        private const val CHANNEL = "voice"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_SHOW = "com.yaari.chat.voice.SHOW"
        private const val ACTION_LEAVE = "com.yaari.chat.voice.LEAVE"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_MIC = "mic"
        /** Opens the app on the room (MainActivity reads it). */
        const val ACTION_OPEN = "com.yaari.chat.voice.OPEN"
        const val EXTRA_END = "end"

        private fun activityIntent(context: Context, end: Boolean) = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN).putExtra(EXTRA_END, end)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** The notification's intent arrived at the activity: pass it on to the room. */
        fun handle(intent: Intent?) {
            if (intent?.action != ACTION_OPEN) return
            VoiceBus.send(if (intent.getBooleanExtra(EXTRA_END, false)) VoiceBus.Request.END else VoiceBus.Request.OPEN)
        }

        private fun start(context: Context, title: String, host: Boolean, mic: Boolean) {
            val intent = Intent(context, VoiceService::class.java).setAction(ACTION_SHOW)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_HOST, host).putExtra(EXTRA_MIC, mic)
            // Starting from the background is refused on Android 12+; the room started in the foreground anyway.
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        private fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, VoiceService::class.java)) }
        }

        private var askedNotifications = false

        /** What the notification shows; a change restarts it ([muted]: unmuting adds the microphone type). */
        private data class Shown(val roomId: String, val title: String, val host: Boolean, val onStage: Boolean, val muted: Boolean)

        /**
         * Runs the service while I'm in a voice room and keeps its notification up to date. Asks once (per app run) for
         * the notification permission when a room opens (Android 13+); without it the room still works, the
         * notification just doesn't show.
         */
        @Composable
        fun Follow(vm: AppViewModel) {
            val context = LocalContext.current
            val s by vm.voice.state.collectAsState()
            val roomId = s?.roomId
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(roomId) {
                if (roomId == null || askedNotifications || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
                askedNotifications = true
                runCatching { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            // Only while there is something to keep alive: the room's audio (someone is on the stage) or its music
            // playing. A room nobody talks or plays music in is a chat room: no service, no notification.
            // Collected here rather than read in composition: Compose stops recomposing while the app is in the
            // background, and leaving there (the notification's Leave button) must stop the service at once.
            LaunchedEffect(vm) {
                vm.voice.state
                    .map { v -> v?.takeIf { it.keepAlive }?.let { Shown(it.roomId, it.room?.title.orEmpty(), it.isHost, it.me?.seat != null, it.me?.muted != false) } }
                    .distinctUntilChanged()
                    // Unmuting (right after the microphone permission was granted) adds the microphone type.
                    .collect { n -> if (n == null) stop(context) else start(context, n.title, n.host, n.onStage) }
            }
            DisposableEffect(Unit) { onDispose { stop(context) } }
        }
    }
}

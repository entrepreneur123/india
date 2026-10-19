package com.yaari.chat

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yaari.chat.data.Backend
import com.yaari.chat.data.Billing
import com.yaari.chat.data.VideoToken
import com.yaari.chat.live.PlayBilling
import com.yaari.chat.live.SupabaseBackend
import com.yaari.chat.model.Profile
import com.yaari.chat.ui.Avatar
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Live build: Supabase (Mumbai) + LiveKit video + Google Play Billing. */
object Flavor {
    private var backend: Backend? = null
    private var billing: Billing? = null

    fun backend(app: Application): Backend = backend ?: SupabaseBackend(app).also { backend = it }
    fun billing(app: Application): Billing = billing ?: PlayBilling(app).also { billing = it }

    private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @Composable
    fun VideoStage(token: VideoToken?, mic: Boolean, cam: Boolean, partner: Profile, me: Profile, modifier: Modifier) {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
        }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it.values.all { ok -> ok } }
        LaunchedEffect(Unit) { if (!granted) ask.launch(PERMISSIONS) }

        if (token == null || !granted) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(partner, 140.dp)
                    Text(
                        stringResource(if (!granted) R.string.allow_camera else R.string.connecting),
                        color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return
        }

        // Adaptive stream: the receiver gets a resolution that fits its view and network.
        // Dynacast: layers nobody watches are not sent. Both matter on Indian mobile networks.
        val room = remember(token) {
            LiveKit.create(context.applicationContext, options = RoomOptions(adaptiveStream = true, dynacast = true))
        }
        val remote = remember(room) { TextureViewRenderer(context).also { room.initVideoRenderer(it) } }
        val local = remember(room) { TextureViewRenderer(context).also { room.initVideoRenderer(it); it.setMirror(true) } }
        var remoteOn by remember { mutableStateOf(false) }
        var reconnecting by remember { mutableStateOf(false) }
        var connected by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }

        LaunchedEffect(room) {
            launch {
                room.events.collect { e ->
                    when (e) {
                        is RoomEvent.TrackSubscribed -> (e.track as? VideoTrack)?.let { it.addRenderer(remote); remoteOn = true }
                        is RoomEvent.TrackUnsubscribed -> if (e.track is VideoTrack) remoteOn = false
                        is RoomEvent.Reconnecting -> reconnecting = true
                        is RoomEvent.Reconnected -> reconnecting = false
                        else -> Unit
                    }
                }
            }
            // A network drop or bad token must not crash the app: show it and let the user end the call.
            try {
                room.connect(token.url, token.token)
                connected = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
        // Mic and camera follow the buttons once connected (camera/mic errors are shown as "failed", never thrown).
        LaunchedEffect(room, connected, mic) {
            if (connected) runCatching { room.localParticipant.setMicrophoneEnabled(mic) }.onFailure { failed = true }
        }
        LaunchedEffect(room, connected, cam) {
            if (!connected) return@LaunchedEffect
            runCatching {
                room.localParticipant.setCameraEnabled(cam)
                if (cam) (room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack)?.addRenderer(local)
            }.onFailure { failed = true }
        }
        DisposableEffect(room) {
            onDispose {
                room.disconnect()
                remote.release()
                local.release()
                room.release()
            }
        }

        Box(modifier) {
            AndroidView({ remote }, Modifier.fillMaxSize())
            if (!remoteOn) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(partner, 140.dp)
                    Text(stringResource(R.string.waiting_video, partner.name), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 12.dp))
                }
            }
            if (failed) {
                Text(
                    stringResource(R.string.call_failed), color = Color.White,
                    modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(12.dp),
                )
            }
            if (reconnecting) {
                Text(
                    stringResource(R.string.reconnecting), color = Color.White,
                    modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(12.dp),
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp).size(96.dp, 128.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xFF333333)),
                contentAlignment = Alignment.Center,
            ) {
                if (cam) AndroidView({ local }, Modifier.fillMaxSize()) else Avatar(me, 56.dp)
            }
        }
    }
}

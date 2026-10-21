package com.yaari.chat

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yaari.chat.data.Backend
import com.yaari.chat.data.Billing
import com.yaari.chat.data.CrashReports
import com.yaari.chat.data.PhotoCheck
import com.yaari.chat.data.Shield
import com.yaari.chat.data.VideoToken
import com.yaari.chat.data.watchNudity
import com.yaari.chat.live.FrameGrabber
import com.yaari.chat.live.NudityModel
import com.yaari.chat.live.PhotoModel
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
    /** Voice rooms send the microphone through LiveKit: asked the first time someone unmutes. */
    const val voiceMicPermission = true

    fun billing(app: Application): Billing = billing ?: PlayBilling(app).also { billing = it }

    /** Crashes are written down here and sent on the next launch (SupabaseBackend's telemetry). */
    fun crashReports(app: Application) = CrashReports.install(SupabaseBackend.reportsDir(app), SupabaseBackend.VERSION)

    /** Room photos are checked by the call check's nudity model before they are sent. */
    fun photoCheck(app: Application): PhotoCheck = PhotoModel(app)

    /** Third-party licenses beyond the font (title to raw text resource). */
    val licenses: List<Pair<String, Int>> = listOf("nsfw_model (nudity check)" to R.raw.nsfw_model_license)

    private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @Composable
    fun VideoStage(token: VideoToken?, mic: Boolean, cam: Boolean, partner: Profile, me: Profile, onNudity: () -> Unit, modifier: Modifier) {
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
        // Nudity check on the partner's video (data/Nudity.kt): blurred from their first frame until the first clean
        // check (at most 8 s), covered while flagged. Starts with their video, so a late camera is checked too.
        val frames = remember(room) { FrameGrabber() }
        var partnerVideo by remember { mutableStateOf(false) }
        var shield by remember { mutableStateOf(Shield.CHECKING) }
        var flagged by remember { mutableStateOf(false) }
        val flag by rememberUpdatedState(onNudity)

        LaunchedEffect(room) {
            launch {
                room.events.collect { e ->
                    when (e) {
                        is RoomEvent.TrackSubscribed -> (e.track as? VideoTrack)?.let {
                            it.addRenderer(remote)
                            it.addRenderer(frames)
                            remoteOn = true
                            partnerVideo = true
                        }
                        is RoomEvent.TrackUnsubscribed -> (e.track as? VideoTrack)?.let {
                            it.removeRenderer(frames)
                            remoteOn = false
                        }
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
        LaunchedEffect(room, partnerVideo) {
            if (!partnerVideo) return@LaunchedEffect
            var model: NudityModel? = null
            try {
                watchNudity(
                    open = { NudityModel.open(context.applicationContext, frames).also { model = it } },
                    onShield = {
                        shield = it
                        if (it == Shield.HIDDEN && !flagged) { flagged = true; flag() }
                    },
                )
            } finally {
                model?.close() // after the last check: watchNudity returns only once it is done
            }
        }
        DisposableEffect(room) {
            onDispose {
                room.disconnect()
                remote.release()
                local.release()
                room.release()
            }
        }

        val covered = shield != Shield.CLEAR
        Box(modifier) {
            // Blur needs Android 12; older phones get an almost opaque scrim instead.
            val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            AndroidView({ remote }, Modifier.fillMaxSize().then(if (covered && blurs) Modifier.blur(64.dp) else Modifier))
            if (covered) Box(Modifier.fillMaxSize().background(if (blurs) Color.Black.copy(alpha = 0.35f) else Color(0xF20E0E14)))
            if (remoteOn && shield == Shield.HIDDEN) {
                Column(
                    Modifier.align(Alignment.Center).widthIn(max = 320.dp).padding(24.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        stringResource(R.string.video_hidden_title), color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.video_hidden_body), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else if (remoteOn && shield == Shield.CHECKING && !failed && !reconnecting) {
                Row(
                    Modifier.align(Alignment.Center).background(Color(0x99000000), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.GppGood, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.video_checking), color = Color.White, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
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
                Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 16.dp).size(108.dp, 152.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1E27))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (cam) AndroidView({ local }, Modifier.fillMaxSize()) else Avatar(me, 56.dp)
            }
        }
    }
}

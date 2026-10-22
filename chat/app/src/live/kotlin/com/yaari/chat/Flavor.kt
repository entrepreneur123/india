package com.yaari.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.yaari.chat.data.PartnerNotice
import com.yaari.chat.data.Signal
import com.yaari.chat.data.cameraWanted
import com.yaari.chat.data.partnerNotice
import com.yaari.chat.data.signalBars
import io.livekit.android.room.participant.AudioPresets
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.VideoCodec
import io.livekit.android.room.track.VideoPreset169
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
import com.yaari.chat.data.PushRegistrar
import com.yaari.chat.live.Fcm
import com.yaari.chat.live.FcmTokens
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

    private var push: PushRegistrar? = null

    /** Firebase starts only when its values were given at build time (live/Fcm.kt). */
    fun initPush(app: Application) = Fcm.init(app)

    /** Phone push through FCM; [PushRegistrar] does nothing while Firebase is not configured. */
    fun push(app: Application): PushRegistrar = push ?: PushRegistrar(
        Fcm.config, { FcmTokens() }, backend(app), { AppLocale.matchLanguage(app) }, { java.util.TimeZone.getDefault().id },
    ).also { push = it }

    /** Crashes are written down here and sent on the next launch (SupabaseBackend's telemetry). */
    fun crashReports(app: Application) = CrashReports.install(SupabaseBackend.reportsDir(app), SupabaseBackend.VERSION)

    /** Room photos are checked by the call check's nudity model before they are sent. */
    fun photoCheck(app: Application): PhotoCheck = PhotoModel(app)

    /** Third-party licenses beyond the font (title to raw text resource). */
    val licenses: List<Pair<String, Int>> = listOf("nsfw_model (nudity check)" to R.raw.nsfw_model_license)

    private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun ConnectionQuality.signal() = when (this) {
        ConnectionQuality.EXCELLENT -> Signal.EXCELLENT
        ConnectionQuality.GOOD -> Signal.GOOD
        ConnectionQuality.POOR -> Signal.POOR
        ConnectionQuality.LOST -> Signal.LOST
        ConnectionQuality.UNKNOWN -> Signal.UNKNOWN
    }

    @Composable
    fun VideoStage(token: VideoToken?, mic: Boolean, cam: Boolean, partner: Profile, me: Profile, onNudity: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        // The screen stays on for the whole call, as in a phone call app.
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        // Microphone is needed for the call; the camera is optional (they can still hear me without it).
        var micOk by remember { mutableStateOf(has(context, Manifest.permission.RECORD_AUDIO)) }
        var camOk by remember { mutableStateOf(has(context, Manifest.permission.CAMERA)) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            micOk = has(context, Manifest.permission.RECORD_AUDIO)
            camOk = has(context, Manifest.permission.CAMERA)
        }
        LaunchedEffect(Unit) { if (!micOk || !camOk) ask.launch(PERMISSIONS) }
        // In the background Android takes the camera away (a frozen frame for my partner): it is turned off until I'm
        // back. Coming back from Settings also re-reads the permissions.
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        LaunchedEffect(foreground) {
            if (foreground) {
                micOk = has(context, Manifest.permission.RECORD_AUDIO)
                camOk = has(context, Manifest.permission.CAMERA)
            }
        }
        val openSettings = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Unit
        }

        if (token == null || !micOk) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(partner, 140.dp)
                    Text(
                        stringResource(if (!micOk) R.string.allow_camera else R.string.connecting),
                        color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                    )
                    if (!micOk) {
                        Text(
                            stringResource(R.string.open_settings), color = Color.White, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                                .clickable(role = Role.Button, onClick = openSettings).padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            return
        }

        // Adaptive stream: the receiver gets a resolution that fits its view and network.
        // Dynacast: layers nobody watches are not sent. Both matter on Indian mobile networks.
        // Audio: LiveKit's call mode (MODE_IN_COMMUNICATION, so the phone's echo canceller runs), speaker unless a
        // Bluetooth or wired headset is in; WebRTC echo cancellation, noise suppression and auto gain on.
        // Video: front camera 720p, VP8 (hardware encoder on most phones, software otherwise) with simulcast layers.
        val room = remember(token) {
            LiveKit.create(
                context.applicationContext,
                options = RoomOptions(
                    adaptiveStream = true,
                    dynacast = true,
                    audioTrackCaptureDefaults = LocalAudioTrackOptions(noiseSuppression = true, echoCancellation = true, autoGainControl = true),
                    audioTrackPublishDefaults = AudioTrackPublishDefaults(audioBitrate = AudioPresets.MUSIC.maxBitrate, dtx = true, red = true),
                    videoTrackCaptureDefaults = LocalVideoTrackOptions(position = CameraPosition.FRONT, captureParams = VideoPreset169.H720.capture),
                    videoTrackPublishDefaults = VideoTrackPublishDefaults(
                        videoEncoding = VideoPreset169.H720.encoding, simulcast = true, videoCodec = VideoCodec.VP8.codecName,
                    ),
                ),
            )
        }
        val remote = remember(room) { TextureViewRenderer(context).also { room.initVideoRenderer(it) } }
        // Only my own preview is mirrored, like a mirror; what my partner receives is not.
        val local = remember(room) { TextureViewRenderer(context).also { room.initVideoRenderer(it); it.setMirror(true) } }
        var remoteOn by remember { mutableStateOf(false) }
        var reconnecting by remember { mutableStateOf(false) }
        var connected by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var dropped by remember { mutableStateOf(false) }
        var deviceBusy by remember { mutableStateOf(false) }
        // Call health: my bars, and a notice instead of a frozen picture when my partner's network fails.
        var myQuality by remember { mutableStateOf(Signal.UNKNOWN) }
        var partnerQuality by remember { mutableStateOf(Signal.UNKNOWN) }
        var partnerPaused by remember { mutableStateOf(false) }
        var partnerLeft by remember { mutableStateOf(false) }
        var partnerCam by remember { mutableStateOf(true) }
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
                            partnerCam = !e.publication.muted
                        }
                        is RoomEvent.TrackUnsubscribed -> (e.track as? VideoTrack)?.let {
                            it.removeRenderer(frames)
                            remoteOn = false
                        }
                        is RoomEvent.TrackMuted ->
                            if (e.participant != room.localParticipant && e.publication.source == Track.Source.CAMERA) partnerCam = false
                        is RoomEvent.TrackUnmuted ->
                            if (e.participant != room.localParticipant && e.publication.source == Track.Source.CAMERA) partnerCam = true
                        is RoomEvent.TrackStreamStateChanged ->
                            if (e.trackPublication.kind == Track.Kind.VIDEO) partnerPaused = e.streamState == Track.StreamState.PAUSED
                        is RoomEvent.ConnectionQualityChanged ->
                            if (e.participant == room.localParticipant) myQuality = e.quality.signal() else partnerQuality = e.quality.signal()
                        is RoomEvent.ParticipantDisconnected -> partnerLeft = true
                        is RoomEvent.ParticipantConnected -> {
                            partnerLeft = false
                            partnerQuality = e.participant.connectionQuality.signal()
                        }
                        is RoomEvent.Reconnecting -> reconnecting = true
                        is RoomEvent.Reconnected -> reconnecting = false
                        // LiveKit gave up reconnecting (or the server ended the room): say so, not a frozen frame.
                        is RoomEvent.Disconnected -> if (connected) {
                            reconnecting = false
                            dropped = true
                        }
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
        // Mic and camera follow the buttons once connected. A device another app holds is a message, not a failed call.
        LaunchedEffect(room, connected, mic) {
            if (connected) runCatching { room.localParticipant.setMicrophoneEnabled(mic) }.onFailure { if (it is CancellationException) throw it; deviceBusy = true }
        }
        val camOn = cameraWanted(cam, camOk, foreground)
        LaunchedEffect(room, connected, camOn) {
            if (!connected) return@LaunchedEffect
            runCatching {
                room.localParticipant.setCameraEnabled(camOn)
                if (camOn) (room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack)?.addRenderer(local)
            }.onFailure { if (it is CancellationException) throw it; deviceBusy = true }
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

        val notice = partnerNotice(partnerQuality, partnerPaused, partnerLeft)
        // Their camera is off or their picture stopped: their avatar, not a black or frozen frame.
        val partnerHidden = !partnerCam || notice == PartnerNotice.LOST || notice == PartnerNotice.PAUSED
        val covered = shield != Shield.CLEAR
        val busy = failed || dropped || reconnecting
        Box(modifier) {
            // Blur needs Android 12; older phones get an almost opaque scrim instead.
            val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            AndroidView({ remote }, Modifier.fillMaxSize().then(if (covered && blurs) Modifier.blur(64.dp) else Modifier))
            if (covered) Box(Modifier.fillMaxSize().background(if (blurs) Color.Black.copy(alpha = 0.35f) else Color(0xF20E0E14)))
            if (remoteOn && partnerHidden) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0E0E14)), contentAlignment = Alignment.Center) { Avatar(partner, 140.dp) }
            }
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
            } else if (remoteOn && shield == Shield.CHECKING && !busy && !partnerHidden) {
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
            // One status in the middle, most serious first.
            val status = when {
                failed -> stringResource(R.string.call_failed)
                dropped -> stringResource(R.string.call_dropped)
                reconnecting -> stringResource(R.string.reconnecting)
                remoteOn && notice == PartnerNotice.LOST -> stringResource(R.string.partner_lost, partner.name)
                remoteOn && notice == PartnerNotice.PAUSED -> stringResource(R.string.partner_paused, partner.name)
                remoteOn && !partnerCam -> stringResource(R.string.partner_cam_off, partner.name)
                else -> null
            }
            if (status != null) {
                Text(
                    status, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(top = if (remoteOn && partnerHidden) 220.dp else 0.dp).padding(horizontal = 24.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            // Below the name: my connection bars, and a quiet line when theirs is weak.
            Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 20.dp, top = 64.dp)) {
                val bars = if (connected && !busy) signalBars(myQuality) else null
                if (bars != null) {
                    val icon = when (bars) {
                        3 -> Icons.Rounded.SignalCellularAlt
                        2 -> Icons.Rounded.SignalCellularAlt2Bar
                        1 -> Icons.Rounded.SignalCellularAlt1Bar
                        else -> Icons.Rounded.SignalCellularConnectedNoInternet0Bar
                    }
                    val label = stringResource(if (bars >= 3) R.string.net_good else if (bars == 2) R.string.net_ok else R.string.net_poor)
                    Icon(
                        icon, label, tint = Color.White,
                        modifier = Modifier.background(Color(0x8C000000), CircleShape).padding(6.dp).size(16.dp),
                    )
                }
                if (remoteOn && !busy && notice == PartnerNotice.WEAK) {
                    Text(
                        stringResource(R.string.partner_weak, partner.name), color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 6.dp).background(Color(0x8C000000), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (deviceBusy || (cam && !camOk)) {
                    Text(
                        stringResource(if (deviceBusy) R.string.device_busy else R.string.cam_blocked), color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 6.dp, end = 140.dp).background(Color(0x8C000000), RoundedCornerShape(12.dp))
                            .clickable(enabled = !camOk, role = Role.Button, onClick = openSettings).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 16.dp).size(108.dp, 152.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1E27))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (camOn) AndroidView({ local }, Modifier.fillMaxSize()) else Avatar(me, 56.dp)
            }
        }
    }
}

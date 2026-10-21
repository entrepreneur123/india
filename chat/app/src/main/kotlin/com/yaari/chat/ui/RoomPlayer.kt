package com.yaari.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.yaari.chat.R
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceTrack
import com.yaari.chat.model.YouTube
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---- The room's one music player ----------------------------------------------------------------------------------
// "Listen together" keeps playing while the music panel is collapsed, while the room is minimised to the bar over the
// tabs, and while I look around the app (until I leave the room or stop listening). So the player can't live inside the
// panel: anything there leaves the composition when the panel collapses or the room minimises, and the WebView with
// it. There is exactly one player, [RoomPlayerHost], composed above the screens for as long as I listen; a panel only
// holds a [PlayerSlot], an empty frame the host lays the player over (clipped to what of the frame is on screen). With
// no slot on screen the host keeps the player out of sight, still playing.

/** False in screenshot tests: the YouTube WebView is replaced by its loading placeholder. */
val LocalYouTubePlayer = staticCompositionLocalOf { true }

enum class PlayerState { LOADING, READY, FAILED, UNAVAILABLE }

/** Where the player is shown (a [PlayerSlot] on screen, in window pixels) and what it last said. */
@Stable
class RoomPlayer {
    /** The slot's frame, and the part of it that is on screen (a slot scrolled half out of view is cut there). */
    internal var slot by mutableStateOf<Rect?>(null)
    internal var clip by mutableStateOf<Rect?>(null)
    internal var owner: Any? = null

    /** The song's length in seconds (0 until the player knows it). */
    var duration by mutableFloatStateOf(0f)
        internal set
    var state by mutableStateOf(PlayerState.LOADING)
        internal set
}

val LocalRoomPlayer = staticCompositionLocalOf { RoomPlayer() }

/**
 * The frame the room's player shows in: 16:9, at least 200 dp tall (YouTube's minimum). [active]: the slot is the
 * one on screen (a room screen on its way out passes false, so the player leaves with the room's state, not later).
 */
@Composable
fun PlayerSlot(track: VoiceTrack, modifier: Modifier = Modifier, active: Boolean = true) {
    val player = LocalRoomPlayer.current
    val token = remember { Any() }
    val release = {
        if (player.owner === token) {
            player.owner = null
            player.slot = null
            player.clip = null
        }
    }
    DisposableEffect(active) {
        if (!active) release()
        onDispose { release() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = maxOf(200.dp, maxWidth * 9 / 16)
        Box(
            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(Color.Black)
                .onGloballyPositioned { c ->
                    if (!active) return@onGloballyPositioned
                    player.owner = token
                    player.slot = Rect(c.positionInWindow(), c.size.toSize())
                    player.clip = c.boundsInWindow()
                },
        ) {
            // Under the player: what shows for a moment before the player is laid over the frame.
            AsyncImage(YouTube.thumbnail(track.videoId), null, Modifier.fillMaxSize().alpha(0.45f), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * The one player, while the room's music plays for me ([VoiceState.playing]). Over the slot when one is on screen,
 * else out of sight (still playing). Touches reach it only where it shows.
 */
@Composable
fun RoomPlayerHost(vm: AppViewModel) {
    val s by vm.voice.state.collectAsStateWithLifecycle()
    val state = s
    val track = state?.room?.track
    if (state == null || track == null || !state.playing) return
    val player = LocalRoomPlayer.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(track.id) {
        player.duration = 0f
        player.state = PlayerState.LOADING
    }
    val expanded = state.expanded
    val host = state.isHost
    val trackId = track.id
    val onEnded: (() -> Unit)? = if (host) ({ vm.voice.act(VoiceAction.Skip(trackId)) }) else null
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        // Read while laying out and drawing (not composing): a slot that scrolls moves the player frame by frame.
        fun shown() = expanded && player.slot != null && (player.clip?.let { it.width > 0f && it.height > 0f } == true)
        Box(
            Modifier.place { parked ->
                val slot = player.slot
                val clip = player.clip
                if (!expanded || slot == null || clip == null || clip.width <= 0f || clip.height <= 0f) parked
                else Rect(clip.left - origin.x, clip.top - origin.y, clip.right - origin.x, clip.bottom - origin.y)
            }.clipToBounds().graphicsLayer { alpha = if (shown()) 1f else 0f },
        ) {
            Box(
                Modifier.place { parked ->
                    val slot = player.slot
                    val clip = player.clip
                    if (!expanded || slot == null || clip == null) Rect(0f, 0f, parked.width, parked.height)
                    else Rect(slot.left - clip.left, slot.top - clip.top, slot.right - clip.left, slot.bottom - clip.top)
                }.clip(RoundedCornerShape(16.dp)).background(Color.Black).testTag(PLAYER_TAG),
            ) {
                if (LocalYouTubePlayer.current) {
                    YouTubeView(track, onEnded, onDuration = { player.duration = it }) { player.state = it }
                }
                if (player.state != PlayerState.READY) PlayerStatus(track, player.state)
            }
        }
    }
}

/** Test tag of the one player (kept composed while the music plays). */
const val PLAYER_TAG = "room_player"

/**
 * Sizes and places the node at the rectangle [rect] gives (in its parent's pixels), read while laying out, so a slot
 * moving (scrolling, the keyboard) moves the player without recomposing. [rect] gets the parked rectangle: out of
 * sight to the left of the screen, 320 × 180 dp (the WebView keeps a real size, so it keeps playing).
 */
private fun Modifier.place(rect: (parked: Rect) -> Rect) = layout { measurable, _ ->
    val w = 320.dp.toPx()
    val h = 180.dp.toPx()
    val r = rect(Rect(-w - 64.dp.toPx(), 0f, -64.dp.toPx(), h))
    val width = r.width.roundToInt().coerceAtLeast(1)
    val height = r.height.roundToInt().coerceAtLeast(1)
    val p = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
}

/** While the player can't play (loading, YouTube unreachable, a video that won't play embedded): the thumbnail and why. */
@Composable
private fun PlayerStatus(track: VoiceTrack, state: PlayerState) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(YouTube.thumbnail(track.videoId), null, Modifier.fillMaxSize().alpha(0.45f), contentScale = ContentScale.Crop)
        Row(
            Modifier.align(Alignment.Center).widthIn(max = 300.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state == PlayerState.LOADING) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Text(
                stringResource(
                    when (state) {
                        PlayerState.LOADING -> R.string.voice_yt_connecting
                        PlayerState.FAILED -> R.string.voice_yt_failed
                        else -> R.string.voice_yt_unavailable
                    },
                ),
                color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * YouTube's IFrame player (android-youtube-player). It follows the room: the same video at the same position
 * (startedAt, corrected for this phone's clock), paused when the host pauses, put back in step when it drifts. The
 * lifecycle observer pauses it when the app goes to the background (as before: YouTube plays only while the app is
 * open); leaving the room or stopping listening releases it.
 */
@Composable
private fun YouTubeView(track: VoiceTrack, onEnded: (() -> Unit)?, onDuration: (Float) -> Unit, onState: (PlayerState) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(track)
    val ended by rememberUpdatedState(onEnded)
    val report by rememberUpdatedState(onState)
    val duration by rememberUpdatedState(onDuration)
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var ready by remember { mutableStateOf(false) }
    var loadedId by remember { mutableStateOf<String?>(null) }
    val tracker = remember { YouTubePlayerTracker() }
    val view = remember { YouTubePlayerView(context).apply { enableAutomaticInitialization = false } }

    DisposableEffect(view) {
        lifecycle.addObserver(view)
        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.addListener(tracker)
                val t = current
                val at = t.position(System.currentTimeMillis())
                loadedId = t.videoId
                if (t.pausedAt == null) youTubePlayer.loadVideo(t.videoId, at) else youTubePlayer.cueVideo(t.videoId, at)
                player = youTubePlayer
                ready = true
                report(PlayerState.READY)
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                if (state == PlayerConstants.PlayerState.ENDED) ended?.invoke()
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                report(PlayerState.UNAVAILABLE)
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                duration(duration)
            }
        }
        val options = IFramePlayerOptions.Builder(context).controls(1).rel(0).ivLoadPolicy(3).build()
        view.initialize(listener, true, options)
        onDispose {
            lifecycle.removeObserver(view)
            player = null
            view.release()
        }
    }
    // YouTube unreachable (blocked, offline): say so after a while instead of loading forever.
    LaunchedEffect(view) {
        delay(12_000)
        if (!ready) report(PlayerState.FAILED)
    }
    // Follow the room: a new track, the host pausing or resuming.
    LaunchedEffect(player, track.videoId, track.startedAt, track.pausedAt) {
        val p = player ?: return@LaunchedEffect
        val at = track.position(System.currentTimeMillis())
        if (loadedId != track.videoId) {
            loadedId = track.videoId
            if (track.pausedAt == null) p.loadVideo(track.videoId, at) else p.cueVideo(track.videoId, at)
            return@LaunchedEffect
        }
        VoiceController.seekTarget(tracker.currentSecond, track, System.currentTimeMillis())?.let(p::seekTo)
        if (track.pausedAt != null) p.pause() else if (tracker.state != PlayerConstants.PlayerState.PLAYING) p.play()
    }
    // Stay in step with everyone while playing (a slow phone or a buffering pause drifts behind).
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            delay(5_000)
            val t = current
            if (t.pausedAt == null && tracker.state == PlayerConstants.PlayerState.PLAYING) {
                VoiceController.seekTarget(tracker.currentSecond, t, System.currentTimeMillis())?.let(p::seekTo)
            }
        }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}

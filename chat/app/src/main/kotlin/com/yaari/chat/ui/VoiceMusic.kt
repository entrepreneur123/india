package com.yaari.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.yaari.chat.R
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceQueueItem
import com.yaari.chat.model.VoiceTrack
import com.yaari.chat.model.YouTube
import kotlinx.coroutines.delay

/** False in screenshot tests: the YouTube WebView is replaced by its loading placeholder. */
val LocalYouTubePlayer = staticCompositionLocalOf { true }

private enum class PlayerState { LOADING, READY, FAILED, UNAVAILABLE }

/**
 * "Listen together": the room's YouTube track. Open, it plays in YouTube's own IFrame player at least 200 dp tall
 * (YouTube's minimum player size); closed, the player is gone (no hidden background playback: YouTube's rules) and a
 * slim bar says it is paused for me.
 */
@Composable
fun MusicPanel(
    track: VoiceTrack, queue: List<VoiceQueueItem>, isHost: Boolean, open: Boolean, onOpenChange: (Boolean) -> Unit,
    onQueue: () -> Unit, act: (VoiceAction) -> Unit,
) {
    val title = track.title.ifEmpty { stringResource(R.string.voice_yt_video) }
    val upNext = queue.count { !it.pending }
    if (!open) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicIcon(Topic.MUSIC, size = 22.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (track.pausedAt != null) R.string.voice_paused_by_host else R.string.voice_paused_for_you),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            SoftButton(
                stringResource(R.string.voice_listen), { onOpenChange(true) }, accent = true, height = 36.dp,
                leading = { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Color.White) },
            )
        }
        return
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Player(track, onEnded = if (isHost) ({ act(VoiceAction.Skip(track.id)) }) else null)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TopicIcon(Topic.MUSIC, size = 16.dp)
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (track.pausedAt != null) stringResource(R.string.voice_paused_by_host) else stringResource(R.string.voice_added_by, track.addedBy),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            if (isHost) {
                val paused = track.pausedAt != null
                CircleIconButton(
                    if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, stringResource(if (paused) R.string.voice_resume else R.string.voice_pause),
                    { act(if (paused) VoiceAction.Resume else VoiceAction.Pause) }, iconSize = 20.dp,
                )
                CircleIconButton(Icons.Rounded.SkipNext, stringResource(R.string.voice_skip), { act(VoiceAction.Skip(track.id)) }, iconSize = 20.dp)
            }
            Row(
                Modifier.heightIn(min = 48.dp).clip(CircleShape).tapTarget(stringResource(R.string.voice_up_next_n, upNext), onQueue).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp))
                Text("$upNext", style = MaterialTheme.typography.labelLarge)
            }
            CircleIconButton(Icons.Rounded.ExpandLess, stringResource(R.string.voice_collapse), { onOpenChange(false) }, iconSize = 20.dp)
        }
    }
}

/**
 * YouTube's IFrame player (android-youtube-player). It follows the room: the same video at the same position
 * (startedAt, corrected for this phone's clock), paused when the host pauses, put back in step when it drifts.
 * While it can't play (loading, YouTube unreachable, a video that won't play embedded) the thumbnail and a status
 * stand in for it.
 */
@Composable
private fun Player(track: VoiceTrack, onEnded: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = maxOf(200.dp, maxWidth * 9 / 16)
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            var state by remember { mutableStateOf(PlayerState.LOADING) }
            if (LocalYouTubePlayer.current) YouTubeView(track, onEnded) { state = it }
            if (state != PlayerState.READY) {
                AsyncImage(
                    YouTube.thumbnail(track.videoId), null, Modifier.fillMaxSize().alpha(0.45f), contentScale = ContentScale.Crop,
                )
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
    }
}

@Composable
private fun YouTubeView(track: VoiceTrack, onEnded: (() -> Unit)?, onState: (PlayerState) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(track)
    val ended by rememberUpdatedState(onEnded)
    val report by rememberUpdatedState(onState)
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var ready by remember { mutableStateOf(false) }
    var loadedId by remember { mutableStateOf<String?>(null) }
    val tracker = remember { YouTubePlayerTracker() }
    val view = remember { YouTubePlayerView(context).apply { enableAutomaticInitialization = false } }

    // One player while the panel is open; closing the panel or leaving the room releases it. The lifecycle
    // observer pauses it when the app goes to the background (no background playback).
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

/** "Up next": the queue, the host's approvals, and the box to add (host) or suggest (everyone else) a link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(track: VoiceTrack?, queue: List<VoiceQueueItem>, isHost: Boolean, meName: String, act: VoiceActor, onDismiss: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val next = queue.filter { !it.pending }
    // The host sees every suggestion; others see only their own, waiting.
    val pending = queue.filter { it.pending && (isHost || it.addedBy == meName) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SheetTitle(stringResource(R.string.voice_music_title), stringResource(if (isHost) R.string.voice_dj_desc else R.string.voice_suggest_desc), onDismiss)
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    link, { link = it; error = false }, Modifier.weight(1f), singleLine = true, isError = error,
                    placeholder = { Text(stringResource(R.string.voice_paste_link), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.size(8.dp))
                SoftButton(
                    stringResource(if (isHost) R.string.voice_add else R.string.voice_suggest),
                    {
                        val id = YouTube.videoId(link)
                        if (id == null) {
                            error = true
                        } else {
                            busy = true
                            act(
                                if (isHost) VoiceAction.AddTrack(id) else VoiceAction.Suggest(id),
                                UiText(if (isHost) R.string.voice_added else R.string.voice_suggested),
                            ) { ok -> busy = false; if (ok) link = "" }
                        }
                    },
                    accent = true, height = 48.dp, enabled = link.isNotBlank() && !busy,
                )
            }
            Text(
                stringResource(if (error) R.string.voice_bad_link else R.string.voice_link_hint),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (error) FontWeight.Medium else null,
            )
            if (track != null) {
                SectionLabel(stringResource(R.string.voice_now_playing_label))
                TrackRow(track.videoId, track.title, stringResource(R.string.voice_added_by, track.addedBy), playing = true)
            }
            if (isHost && pending.isNotEmpty()) {
                SectionLabel(stringResource(R.string.voice_suggestions))
                pending.forEach { q ->
                    TrackRow(q.videoId, q.title, stringResource(R.string.voice_suggested_by, q.addedBy)) {
                        CircleIconButton(Icons.Rounded.Close, stringResource(R.string.voice_decline), { act(VoiceAction.Approve(q.id, false), null) {} }, iconSize = 20.dp)
                        CircleIconButton(
                            Icons.Rounded.Check, stringResource(R.string.voice_approve), { act(VoiceAction.Approve(q.id, true), null) {} },
                            background = Brand.Pink, tint = Color.White, size = 36.dp, iconSize = 20.dp,
                        )
                    }
                }
            }
            SectionLabel(stringResource(R.string.voice_up_next))
            if (next.isEmpty() && (isHost || pending.isEmpty())) {
                Text(
                    stringResource(R.string.voice_queue_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                )
            } else {
                next.forEach { q ->
                    TrackRow(q.videoId, q.title, stringResource(R.string.voice_added_by, q.addedBy)) {
                        if (isHost) {
                            CircleIconButton(Icons.Outlined.Delete, stringResource(R.string.voice_remove), { act(VoiceAction.RemoveItem(q.id), null) {} }, iconSize = 20.dp)
                            CircleIconButton(
                                Icons.Rounded.PlayArrow, stringResource(R.string.voice_play_now), { act(VoiceAction.Play(q.id), null) {} },
                                background = MaterialTheme.colorScheme.surfaceContainerHighest, size = 36.dp, iconSize = 20.dp,
                            )
                        }
                    }
                }
                if (!isHost) pending.forEach { q -> TrackRow(q.videoId, q.title, stringResource(R.string.voice_waiting)) }
            }
        }
    }
}

/** Runs a room action: [success] is said when it worked; the callback gets whether it did. */
typealias VoiceActor = (VoiceAction, UiText?, (Boolean) -> Unit) -> Unit

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun TrackRow(videoId: String, title: String, sub: String, playing: Boolean = false, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            TopicIcon(Topic.MUSIC, size = 20.dp)
            AsyncImage(YouTube.thumbnail(videoId), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title.ifEmpty { stringResource(R.string.voice_yt_video) }, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

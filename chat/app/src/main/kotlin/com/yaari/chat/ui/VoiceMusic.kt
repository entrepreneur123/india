package com.yaari.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.yaari.chat.R
import com.yaari.chat.model.VoiceAction
import com.yaari.chat.model.VoiceQueueItem
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.VoiceTrack
import com.yaari.chat.model.byVotes
import com.yaari.chat.model.YouTube
import kotlinx.coroutines.delay

/**
 * "Listen together" outside a music room: the room's YouTube track. Before I listen, a slim bar offers it ("Paused for
 * you"). Listening and open, the player ([PlayerSlot]: the room's one player, [RoomPlayerHost]) with its controls;
 * collapsed (the chevron), a compact now-playing strip: the music plays on, also while the room is minimised.
 */
@Composable
fun MusicPanel(
    track: VoiceTrack, queue: List<VoiceQueueItem>, canDj: Boolean, listening: Boolean, open: Boolean, active: Boolean,
    onListen: (Boolean) -> Unit, onOpenChange: (Boolean) -> Unit, onQueue: () -> Unit, act: (VoiceAction) -> Unit,
) {
    val title = track.title.ifEmpty { stringResource(R.string.voice_yt_video) }
    val upNext = queue.size
    if (!listening) {
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
                stringResource(R.string.voice_listen), { onListen(true) }, accent = true, height = 36.dp,
                leading = { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Brand.OnSaffron) },
            )
        }
        return
    }
    if (!open) {
        NowPlayingStrip(track, title) { onOpenChange(true) }
        return
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        PlayerSlot(track, active = active)
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
            CircleIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.voice_collapse), { onOpenChange(false) }, iconSize = 24.dp)
        }
        // The controls on lines of their own, so the title keeps its room on a narrow phone.
        MusicControls(track, canDj, act)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.heightIn(min = 48.dp).clip(CircleShape).tapTarget(stringResource(R.string.voice_up_next_n, upNext), onQueue).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp))
                Text("$upNext", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            SoftButton(
                stringResource(R.string.voice_stop_listening), { onListen(false) }, height = 36.dp,
                leading = { Icon(Icons.Rounded.HeadsetOff, null, Modifier.size(18.dp)) },
            )
        }
    }
}

/**
 * The music panel collapsed: what plays (it keeps playing), in a compact strip with moving bars; a tap or the
 * chevron opens the player again.
 */
@Composable
private fun NowPlayingStrip(track: VoiceTrack, title: String, onExpand: () -> Unit) {
    val color = Brand.Saffron
    val shape = RoundedCornerShape(18.dp)
    val expand = stringResource(R.string.voice_show_player)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(60.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), shape)
            .clickable(role = Role.Button, onClickLabel = expand, onClick = onExpand).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp, 44.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(YouTube.thumbnail(track.videoId), null, Modifier.fillMaxSize().alpha(0.7f), contentScale = ContentScale.Crop)
            if (track.pausedAt == null) LiveBars(color = Color.White, height = 14.dp) else Icon(Icons.Rounded.Pause, null, Modifier.size(18.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(YouTube.songOf(title), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (track.pausedAt != null) R.string.voice_paused_by_host else R.string.music_playing_for_you),
                style = MaterialTheme.typography.bodySmall, color = Brand.SaffronDeep, maxLines = 1,
            )
        }
        CircleIconButton(Icons.Rounded.KeyboardArrowUp, expand, onExpand, iconSize = 22.dp)
    }
}

/** Where the song is in seconds, while it plays (ticks once a second). */
@Composable
private fun rememberPosition(track: VoiceTrack): Float {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(track.id, track.pausedAt, track.startedAt) {
        now = System.currentTimeMillis()
        while (track.pausedAt == null) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return track.position(now)
}

private fun mmss(seconds: Float) = "%d:%02d".format(java.util.Locale.ROOT, seconds.toInt() / 60, seconds.toInt() % 60)

/**
 * Under the room's player. The host and co-hosts (036) control the music for everyone: play / pause, a seek bar, skip.
 * Everyone else sees where the song is (read-only), and controls only their own phone: pause / play (playing again
 * puts it back in step with the room), mute and volume. There is no quality choice: YouTube's player no longer takes one.
 */
@Composable
fun MusicControls(track: VoiceTrack, canDj: Boolean, act: (VoiceAction) -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    val player = LocalRoomPlayer.current
    val color = Brand.Saffron
    val seconds = player.duration
    val at = rememberPosition(track).let { if (seconds > 0) minOf(it, seconds) else it }
    // While dragging, and after letting go until the room's new position arrives (or 3 s pass).
    var drag by remember(track.id) { mutableStateOf<Float?>(null) }
    var held by remember(track.id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(track.startedAt, track.pausedAt) { held = null }
    LaunchedEffect(held) {
        if (held != null) {
            delay(3_000)
            held = null
        }
    }
    val shown = drag ?: held ?: at
    val paused = track.pausedAt != null
    Column(modifier.fillMaxWidth()) {
        if (canDj && seconds > 0) {
            val label = stringResource(R.string.music_seek)
            Slider(
                shown.coerceIn(0f, seconds), { drag = it }, Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = label },
                valueRange = 0f..seconds,
                onValueChangeFinished = {
                    drag?.let { v ->
                        held = v
                        act(VoiceAction.Seek((v * 1000).toLong()))
                    }
                    drag = null
                },
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        } else {
            Box(Modifier.padding(vertical = 13.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                if (seconds > 0) Box(Modifier.fillMaxWidth((shown / seconds).coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(mmss(shown), Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Jakarta)
            if (seconds > 0) Text(mmss(seconds), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Jakarta)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val big = if (large) 22.dp else 20.dp
            if (canDj) {
                CircleIconButton(
                    if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, stringResource(if (paused) R.string.voice_resume else R.string.voice_pause),
                    { act(if (paused) VoiceAction.Resume else VoiceAction.Pause) }, background = MaterialTheme.colorScheme.surfaceContainerHigh, iconSize = big,
                )
                CircleIconButton(
                    Icons.Rounded.SkipNext, stringResource(R.string.music_next), { act(VoiceAction.Skip(track.id)) },
                    background = Brand.Saffron, tint = Brand.OnSaffron, iconSize = big,
                )
                CircleIconButton(Icons.Rounded.Stop, stringResource(R.string.music_stop_all), { act(VoiceAction.Stop) }, iconSize = big)
            } else {
                // My phone only. While the DJ has paused the room there is nothing to play.
                val mine = player.localPaused
                CircleIconButton(
                    if (mine || paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    stringResource(if (mine) R.string.music_play_mine else R.string.music_pause_mine),
                    { player.localPaused = !mine }, background = MaterialTheme.colorScheme.surfaceContainerHigh, iconSize = big, enabled = !paused,
                )
            }
            Spacer(Modifier.weight(1f))
            val muted = player.muted || player.volume == 0
            CircleIconButton(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                stringResource(if (muted) R.string.music_unmute_mine else R.string.music_mute_mine),
                {
                    if (muted) {
                        player.muted = false
                        if (player.volume == 0) player.volume = 60
                    } else {
                        player.muted = true
                    }
                },
                iconSize = 20.dp,
            )
            val volumeLabel = stringResource(R.string.music_volume)
            Slider(
                if (player.muted) 0f else player.volume.toFloat(), { v -> player.volume = v.toInt(); player.muted = false },
                Modifier.width(112.dp).height(32.dp).semantics { contentDescription = volumeLabel }, valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onSurface, activeTrackColor = MaterialTheme.colorScheme.onSurface, inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        if (!canDj && player.localPaused && !paused) {
            Text(
                stringResource(R.string.music_paused_mine), style = MaterialTheme.typography.bodySmall, color = Brand.SaffronDeep,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * A music room's hero: the shared player (the room's one player over its [PlayerSlot]; [active]: this screen is the
 * one shown) with the song, who added it and where it is, the host's controls (pause,
 * play the most voted next), and the song requests by vote. With nothing playing, the way to request the first.
 */
@Composable
fun MusicHero(
    room: VoiceRoom, canDj: Boolean, adding: String?, active: Boolean, onQueue: () -> Unit, act: (VoiceAction) -> Unit, vote: (String, Boolean) -> Unit,
) {
    val color = Brand.Saffron
    val track = room.track
    val ordered = room.queue.byVotes()
    val hostName = room.member(room.hostId)?.profile?.name.orEmpty()
    val music = stringResource(R.string.voice_music_title)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = music }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (track != null) {
            Column {
                PlayerSlot(track, Modifier.shadow(24.dp, RoundedCornerShape(16.dp)), active = active)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            YouTube.songOf(track.title).ifEmpty { stringResource(R.string.voice_yt_video) },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOf(
                                YouTube.artistOf(track.title),
                                if (track.pausedAt != null) stringResource(R.string.voice_paused_by_host) else stringResource(R.string.music_dj, hostName),
                            ).filter { it.isNotEmpty() }.joinToString(" · "),
                            Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MusicControls(track, canDj, act, Modifier.padding(top = 4.dp), large = true)
                Text(
                    stringResource(R.string.voice_added_by, track.addedBy), Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).background(color.copy(alpha = 0.12f))
                    .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopicIcon(Topic.MUSIC, size = 52.dp)
                Text(stringResource(R.string.music_nothing), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    stringResource(R.string.music_nothing_body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                SoftButton(
                    stringResource(if (canDj) R.string.music_add else R.string.music_request), onQueue, accent = true, height = 44.dp,
                    leading = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = Brand.OnSaffron) },
                )
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.voice_up_next), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Row(
                    Modifier.heightIn(min = 40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onQueue).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.music_request), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (ordered.isEmpty() && adding == null) {
                Text(
                    stringResource(R.string.music_no_requests), Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                adding?.let { AddingRow(it) }
                ordered.take(3).forEach { q ->
                    TrackRow(q.videoId, YouTube.songOf(q.title), requestSub(q)) { VoteButton(q, color) { vote(q.id, !q.voted) } }
                }
            }
        }
    }
}

@Composable
private fun requestSub(q: VoiceQueueItem) =
    stringResource(R.string.voice_added_by, q.addedBy) + " · " + pluralStringResource(R.plurals.music_votes, q.votes, q.votes)

/** A request whose title is being looked up before it is added. */
@Composable
private fun AddingRow(videoId: String) {
    TrackRow(videoId, stringResource(R.string.music_adding), "youtu.be/$videoId") {
        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One vote per person and song; tapping again takes it back. */
@Composable
private fun VoteButton(item: VoiceQueueItem, color: Color, onClick: () -> Unit) {
    val title = item.title.ifEmpty { stringResource(R.string.voice_yt_video) }
    val label = stringResource(if (item.voted) R.string.music_unvote else R.string.music_vote, title)
    Row(
        Modifier.height(36.dp).widthIn(min = 56.dp).clip(CircleShape)
            .background(if (item.voted) color else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (item.voted) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label; selected = item.voted }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
    ) {
        val tint = if (item.voted) Brand.OnSaffron else MaterialTheme.colorScheme.onSurface
        Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(20.dp), tint = tint)
        Text("${item.votes}", color = tint, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/** "Up next": the box to request a song, what plays, the requests by vote, and (for the host) play now and remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    track: VoiceTrack?, queue: List<VoiceQueueItem>, canDj: Boolean, adding: String?, act: VoiceActor,
    addSong: (String, (Boolean) -> Unit) -> Unit, vote: (String, Boolean) -> Unit, onDismiss: () -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val ordered = queue.byVotes()
    val color = Brand.Saffron
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SheetTitle(stringResource(R.string.voice_music_title), stringResource(if (canDj) R.string.voice_dj_desc else R.string.voice_suggest_desc), onDismiss)
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
                    stringResource(if (canDj) R.string.voice_add else R.string.music_request),
                    {
                        val id = YouTube.videoId(link)
                        if (id == null) {
                            error = true
                        } else {
                            val sent = link
                            link = ""
                            // Not added (the reason is said): the link comes back, unless something new was typed meanwhile.
                            addSong(id) { ok -> if (!ok && link.isBlank()) link = sent }
                        }
                    },
                    accent = true, height = 48.dp, enabled = link.isNotBlank() && adding == null,
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
            Row(Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.voice_up_next), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (canDj && ordered.isNotEmpty()) {
                    SoftButton(
                        stringResource(R.string.music_next), { act(VoiceAction.Skip(track?.id), null) {} }, height = 36.dp,
                        leading = { Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp)) },
                    )
                } else {
                    Text(stringResource(R.string.music_most_voted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (ordered.isEmpty() && adding == null) {
                Text(
                    stringResource(R.string.music_no_requests), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                )
            } else {
                adding?.let { AddingRow(it) }
                ordered.forEach { q ->
                    TrackRow(q.videoId, q.title, requestSub(q)) {
                        if (canDj) {
                            CircleIconButton(Icons.Outlined.Delete, stringResource(R.string.voice_remove), { act(VoiceAction.RemoveItem(q.id), null) {} }, size = 36.dp, iconSize = 20.dp)
                            CircleIconButton(Icons.Rounded.PlayArrow, stringResource(R.string.voice_play_now), { act(VoiceAction.Play(q.id), null) {} }, size = 36.dp, iconSize = 20.dp)
                        }
                        VoteButton(q, color) { vote(q.id, !q.voted) }
                    }
                }
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
                overflow = TextOverflow.Ellipsis, color = if (playing) MaterialTheme.colorScheme.primaryText else MaterialTheme.colorScheme.onSurface,
            )
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

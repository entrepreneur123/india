package com.yaari.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.awaitVisible
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.Profile
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.SpeakMode
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.VoiceRoomSummary
import kotlinx.coroutines.delay

// ---- Voice rooms on the Rooms tab: the Voice | Chat switch, the live list, the create sheet ----------------------

/** Voice | Chat: two equal options in a tinted track; the chosen one sits on a raised white card. */
@Composable
fun RoomsKindSwitch(voice: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val kind = stringResource(R.string.rooms_kind)
    Row(
        modifier.fillMaxWidth().semantics { paneTitle = kind }.height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(true to R.string.rooms_voice, false to R.string.rooms_chat).forEach { (value, label) ->
            val on = value == voice
            Row(
                Modifier.weight(1f).height(40.dp)
                    .then(if (on) Modifier.shadow(1.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .then(if (on) Modifier.background(if (night) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface) else Modifier)
                    .selectable(on, role = Role.Tab) { onChange(value) },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(if (value) Icons.Outlined.Mic else Icons.Outlined.Forum, null, Modifier.size(18.dp), tint = color)
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = color)
            }
        }
    }
}

/** Three bars that bounce while a room is live (still when animations are off). */
@Composable
fun LiveBars(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 12.dp) {
    val a by rememberLoop(900, 0.35f, 1f, reverse = true, still = 0.9f)
    val b by rememberLoop(900, 0.35f, 1f, reverse = true, offsetMs = 300, still = 0.55f)
    val c by rememberLoop(900, 0.35f, 1f, reverse = true, offsetMs = 600, still = 0.75f)
    Box(
        modifier.size(13.dp, height).drawBehind {
            val w = 3.dp.toPx()
            val gap = 2.dp.toPx()
            listOf(a, b, c).forEachIndexed { i, f ->
                val h = size.height * f
                drawRoundRect(color, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 2))
            }
        },
    )
}

/** Overlapping faces (the stage of a room, the audience strip). */
@Composable
fun FaceStack(people: List<Profile>, size: Dp, ring: Color, modifier: Modifier = Modifier) {
    Layout(
        content = { people.forEach { p -> Box(Modifier.size(size).clip(CircleShape).background(ring).padding(2.dp)) { Avatar(p, size - 4.dp) } } },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val step = size.roundToPx() * 3 / 4 // each face covers a quarter of the one before
        val width = if (placeables.isEmpty()) 0 else step * (placeables.size - 1) + placeables.last().width
        layout(width.coerceAtMost(constraints.maxWidth), placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { i, p -> p.place(i * step, 0) }
        }
    }
}

/** The live voice rooms: refreshed every 15 s while on screen, pull to refresh, mine first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomsList(vm: AppViewModel, here: String?, kindSwitch: @Composable () -> Unit) {
    val list by vm.voice.list.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(900); refreshing = false } }
    LaunchedEffect(Unit) {
        while (true) {
            AppForeground.visible.awaitVisible()
            vm.voice.refreshList()
            delay(VoiceController.LIST_REFRESH_MS)
        }
    }
    PullToRefreshBox(refreshing, onRefresh = { refreshing = true; vm.voice.refreshList() }, Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "kind") { kindSwitch() }
            item(key = "intro") {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.voice_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    SoftButton(
                        stringResource(R.string.voice_start), { creating = true }, accent = true, height = 48.dp,
                        leading = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Color.White) },
                    )
                }
            }
            item(key = "live") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveBars(height = 14.dp)
                    Text(stringResource(R.string.voice_live_now), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.voice_live_hint), Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2,
                    )
                }
            }
            val rooms = list.rooms
            when {
                rooms == null && list.failed -> item(key = "failed") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState(Icons.Rounded.GraphicEq, stringResource(R.string.voice_load_failed), stringResource(R.string.err_network))
                        SoftButton(stringResource(R.string.common_try_again), vm.voice::refreshList, outlined = true)
                    }
                }
                rooms == null -> items(3) { Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).shimmer()) }
                rooms.isEmpty() -> item(key = "none") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState(Icons.Rounded.GraphicEq, stringResource(R.string.voice_none_title), stringResource(R.string.voice_none_body))
                    }
                }
                else -> itemsIndexed(rooms, key = { _, r -> r.id }) { i, r ->
                    VoiceRoomCard(r, here = r.id == here, modifier = Modifier.enterStagger(i)) { vm.voice.open(r.id) }
                }
            }
        }
    }
    if (creating) CreateVoiceSheet(list.creating, onDismiss = { creating = false }) { spec -> vm.voice.create(spec) { creating = false } }
}

@Composable
fun VoiceRoomCard(room: VoiceRoomSummary, here: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val highlight = here || room.mine
    Column(
        modifier.fillMaxWidth().pressScale(interaction).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(if (highlight) 1.5.dp else 1.dp, if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interaction, indication = ripple(), role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TopicChip(room.category.topic(), stringResource(room.category.label)) }
            LiveBars()
            Text(
                stringResource(if (here) R.string.voice_in_room else R.string.voice_live), Modifier.padding(start = 6.dp),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            )
        }
        Text(
            room.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FaceStack(room.speakers, 30.dp, MaterialTheme.colorScheme.surface)
            Text(
                room.speakers.joinToString(", ") { it.name }, Modifier.padding(start = 10.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        room.nowPlaying?.let { title ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TopicIcon(Topic.MUSIC, size = 15.dp)
                Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(room.host, 20.dp)
            Text(
                if (room.mine) stringResource(R.string.voice_your_room) else stringResource(R.string.voice_hosted_by, room.host.name),
                Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.Headphones, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.voice_listening, room.listeners), Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Room topics as a two-column grid of glyph + label (the voice room form). */
@Composable
fun TopicGrid(value: RoomCategory, onChange: (RoomCategory) -> Unit) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RoomCategory.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { c ->
                    val on = value == c
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.weight(1f).height(48.dp)
                            .then(if (!on && !night) Modifier.shadow(1.dp, shape) else Modifier)
                            .clip(shape)
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else if (night) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
                            .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                            .selectable(on, role = Role.RadioButton) { onChange(c) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TopicIcon(c.topic(), size = 22.dp)
                        Text(
                            stringResource(c.label), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Title, topic and who may speak. The server checks every value again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVoiceSheet(busy: Boolean, onDismiss: () -> Unit, onCreate: (NewVoiceRoom) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(RoomCategory.MUSIC) }
    var mode by rememberSaveable { mutableStateOf(SpeakMode.APPROVAL) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.voice_create_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.voice_create_desc), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), onDismiss, Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(18.dp))
            Field(
                title, { if (it.length <= VoiceRoom.MAX_TITLE) title = it }, stringResource(R.string.voice_name),
                counter = "${title.length}/${VoiceRoom.MAX_TITLE}",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            Text(stringResource(R.string.voice_topic), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
            TopicGrid(category) { category = it }
            Text(stringResource(R.string.voice_who_speaks), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOption(Icons.Outlined.BackHand, R.string.voice_mode_approval, R.string.voice_mode_approval_body, mode == SpeakMode.APPROVAL) { mode = SpeakMode.APPROVAL }
                ModeOption(Icons.Outlined.Chair, R.string.voice_mode_open, R.string.voice_mode_open_body, mode == SpeakMode.OPEN) { mode = SpeakMode.OPEN }
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                stringResource(R.string.voice_go), { onCreate(NewVoiceRoom(title, category, mode)) },
                enabled = !busy && title.trim().length >= VoiceRoom.MIN_TITLE,
            )
        }
    }
}

@Composable
private fun ModeOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, body: Int, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(if (on) 1.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .selectable(on, role = Role.RadioButton, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 14.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

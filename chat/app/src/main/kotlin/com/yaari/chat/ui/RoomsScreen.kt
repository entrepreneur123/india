package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.domain.flagOf
import com.yaari.chat.model.Room
import com.yaari.chat.model.RoomMessage
import kotlinx.coroutines.delay

/** Rooms the app knows get a translated name; new rooms added on the server show the server's title. */
private val RoomNames = mapOf(
    "lounge" to R.string.room_lounge, "language" to R.string.room_language, "music" to R.string.room_music,
    "movies" to R.string.room_movies, "gaming" to R.string.room_gaming, "sports" to R.string.room_sports,
    "night" to R.string.room_night, "memes" to R.string.room_memes, "study" to R.string.room_study, "travel" to R.string.room_travel,
)

/** A one-line description of what each known room is for (static, keyed by room id); unknown rooms show none. */
private val RoomAbout = mapOf(
    "lounge" to R.string.room_about_lounge, "language" to R.string.room_about_language, "music" to R.string.room_about_music,
    "movies" to R.string.room_about_movies, "gaming" to R.string.room_about_gaming, "sports" to R.string.room_about_sports,
    "night" to R.string.room_about_night, "memes" to R.string.room_about_memes, "study" to R.string.room_about_study,
    "travel" to R.string.room_about_travel,
)

@Composable
fun Room.name() = (if (official) RoomNames[id] else null)?.let { stringResource(it) } ?: title

@Composable
private fun Room.about() = (if (official) RoomAbout[id] else null)?.let { stringResource(it) }

/** Pastel tints for the room cards (mint, peach, lavender, blush, sky, butter), light and dark. */
private val RoomTint = listOf(0xFFE9F7EF, 0xFFFFF0E6, 0xFFEEF0FF, 0xFFFFEEF3, 0xFFE8F3FF, 0xFFFFF7DF)
private val RoomTintDark = listOf(0xFF15261C, 0xFF2A1D15, 0xFF1B1D33, 0xFF2C171E, 0xFF15212F, 0xFF2A2514)

@Composable
private fun tintFor(i: Int): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Color((if (dark) RoomTintDark else RoomTint)[Math.floorMod(i, RoomTint.size)])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(900); refreshing = false } }
    var sheet by rememberSaveable { mutableStateOf(RoomSheet.NONE) }
    val official = s.rooms.filter { it.official }
    val people = s.rooms.filterNot { it.official }
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_rooms), onSettings)
        PullToRefreshBox(refreshing, onRefresh = { refreshing = true; vm.refreshRooms() }, Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (s.rooms.isEmpty()) {
                    // Skeletons while the list loads, shaped like the real cards.
                    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).shimmer()) }
                    items(4) { Box(Modifier.height(160.dp).clip(RoundedCornerShape(16.dp)).shimmer()) }
                    return@LazyVerticalGrid
                }
                official.firstOrNull()?.let { featured ->
                    item(key = featured.id, span = { GridItemSpan(maxLineSpan) }) { FeaturedRoom(featured) { vm.enterRoom(featured) } }
                }
                item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
                    RoomActions(onCreate = { sheet = RoomSheet.CREATE }, onCode = { sheet = RoomSheet.CODE })
                }
                item(key = "people", span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(stringResource(R.string.rooms_by_people), trailing = people.size.takeIf { it > 0 }?.toString())
                }
                if (people.isEmpty()) {
                    item(key = "people-empty", span = { GridItemSpan(maxLineSpan) }) { NoUserRooms { sheet = RoomSheet.CREATE } }
                }
                itemsIndexed(people, key = { _, r -> r.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, room ->
                    UserRoomCard(room, Modifier.enterStagger(i)) { vm.enterRoom(room) }
                }
                if (official.size > 1) {
                    item(key = "more", span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.rooms_more)) }
                }
                itemsIndexed(official.drop(1), key = { _, r -> r.id }) { i, room ->
                    RoomCard(room, tintFor(i), modifier = Modifier.enterStagger(i)) { vm.enterRoom(room) }
                }
            }
        }
    }
    when (sheet) {
        RoomSheet.CREATE -> CreateRoomSheet(s.roomBusy, onDismiss = { sheet = RoomSheet.NONE }) { spec -> vm.createRoom(spec) { sheet = RoomSheet.NONE } }
        RoomSheet.CODE -> JoinCodeSheet(s.roomBusy, onDismiss = { sheet = RoomSheet.NONE }) { code -> vm.joinByCode(code) { sheet = RoomSheet.NONE } }
        RoomSheet.NONE -> Unit
    }
}

private enum class RoomSheet { NONE, CREATE, CODE }

/** The first room (Global Lounge), wide, on a flat tonal brand card: the one hero element of the tab. */
@Composable
private fun FeaturedRoom(room: Room, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().pressScale(interaction).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interaction, indication = ripple(), role = Role.Button, onClick = onClick).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(room.emoji, fontSize = 32.sp)
            Spacer(Modifier.weight(1f))
            Pill(
                pluralStringResource(R.plurals.online, room.online, room.online), background = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.onSurface, leading = { PresenceDot(6.dp) },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(room.name(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        room.about()?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.align(Alignment.End).height(40.dp).clip(CircleShape).background(Brand.Pink).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.join), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = Color.White)
        }
    }
}

@Composable
private fun RoomCard(room: Room, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.height(160.dp).pressScale(interaction).clip(RoundedCornerShape(16.dp)).background(tint)
            .clickable(interaction, indication = ripple(), role = Role.Button, onClick = onClick).padding(16.dp),
    ) {
        Text(room.emoji, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            room.name(), style = MaterialTheme.typography.titleSmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
        )
        room.about()?.let {
            Text(
                it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        OnlineCount(room.online)
    }
}

@Composable
private fun OnlineCount(n: Int, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (n > 0) PresenceDot(6.dp) else Box(Modifier.size(6.dp).clip(CircleShape).background(Color.Gray))
        Spacer(Modifier.width(6.dp))
        Text(pluralStringResource(R.plurals.online, n, n), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun RoomScreen(vm: AppViewModel, state: RoomState) {
    BackHandler(onBack = vm::leaveRoom)
    SecureScreen()
    val room = state.room
    val code = room.code
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), vm::leaveRoom)
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Text(room.emoji, fontSize = 22.sp) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(room.name(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.closed) {
                    Text(stringResource(R.string.refusal_closed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (room.maxMembers != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PresenceDot(6.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.room_count, maxOf(room.online, 1), room.maxMembers),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    OnlineCount(maxOf(room.online, 1), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (room.mine && !state.closed) {
                Pill(
                    stringResource(R.string.close_room), Modifier.clip(CircleShape).clickable(role = Role.Button) { confirmClose = true },
                    background = Brand.Danger.copy(alpha = 0.1f), color = Brand.Danger,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        if (room.mine && code != null && !state.closed) RoomCodeBar(room, code)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 20.dp, vertical = 10.dp)) {
            if (!room.official) {
                RoomConditions(room)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.room_rules), Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val list = rememberLazyListState()
        LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.size - 1 + if (state.loading) 1 else 0) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().background(chatColors().wall), state = list,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        ) {
            if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) } }
            if (!state.loading && state.messages.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.WavingHand, stringResource(R.string.room_empty_title),
                        stringResource(if (room.mine && !state.closed) R.string.room_empty_owner else R.string.room_empty),
                    )
                }
            }
            itemsIndexed(state.messages, key = { _, m -> m.id }) { i, m ->
                // A run of messages from one person: avatar, name and tail on the first only, 2 dp apart.
                val grouped = i > 0 && state.messages[i - 1].sender.id == m.sender.id
                Box(Modifier.animateItem().padding(top = if (grouped) 2.dp else 8.dp)) {
                    PopIn(m.fromMe) { RoomLine(m, grouped, onProfile = { vm.openProfile(m.sender) }) }
                }
            }
        }
        if (state.closed) {
            RoomClosedBar(vm::leaveRoom)
        } else {
            Composer(
                onText = vm::sendRoom, onMedia = null, onTyping = {}, maxLength = AppViewModel.MAX_ROOM_TEXT,
                hint = stringResource(R.string.message_room, room.name()),
            )
        }
    }
    if (state.showCode && code != null) RoomCreatedDialog(room, code, vm::dismissRoomCode)
    if (confirmClose) CloseRoomDialog(onConfirm = { confirmClose = false; vm.closeRoom() }, onCancel = { confirmClose = false })
}

/** A room message as in a group chat: mine on the right; others with avatar and coloured name on the first of a run. */
@Composable
private fun RoomLine(m: RoomMessage, grouped: Boolean, onProfile: () -> Unit) {
    val colors = chatColors()
    if (m.fromMe) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.fillMaxWidth(0.85f), contentAlignment = Alignment.CenterEnd) {
                BubbleShell(mine = true, tail = !grouped, padding = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)) {
                    TextWithMeta(m.text, colors.text, meta = { BubbleMeta(m.at, mine = false, read = false) })
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(0.85f), verticalAlignment = Alignment.Top) {
        if (grouped) Spacer(Modifier.width(32.dp))
        else Avatar(m.sender, 32.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onProfile))
        BubbleShell(mine = false, tail = !grouped, padding = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 5.dp)) {
            Column {
                if (!grouped) {
                    Row(
                        Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onProfile),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            m.sender.name, color = colors.names[nameColorIndex(m.sender.id)], fontSize = 13.sp, lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = Jakarta, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("${m.sender.age}", color = colors.meta, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = Jakarta)
                        flagOf(m.sender.country).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp) }
                    }
                }
                TextWithMeta(m.text, colors.text, meta = { BubbleMeta(m.at, mine = false, read = false) })
            }
        }
    }
}

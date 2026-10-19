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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.yaari.chat.model.Message
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (s.rooms.isEmpty()) {
                    // Skeletons while the list loads, shaped like the real cards.
                    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)).shimmer()) }
                    items(4) { Box(Modifier.height(164.dp).clip(RoundedCornerShape(24.dp)).shimmer()) }
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
                    RoomCard(room, tintFor(i), bobDelay = i * 180, modifier = Modifier.enterStagger(i)) { vm.enterRoom(room) }
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

/** The first room (Global Lounge), wide, in a slowly flowing brand gradient with a glow underneath. */
@Composable
private fun FeaturedRoom(room: Room, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val flow by rememberLoop(9000, reverse = true, easing = FastOutSlowInEasing, still = 0.3f)
    Column(
        Modifier.fillMaxWidth().pressScale(interaction, 0.98f)
            .glow(Brand.Pink, radius = 22.dp, offsetY = 16.dp, corner = 28.dp, inset = 18.dp, alpha = 0.6f)
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                // background-size 240 %, background-position 0 -> 100 %: a gradient wider than the card sliding across it.
                val span = size.width * 2.4f
                val x = -(span - size.width) * flow
                drawRect(
                    Brush.linearGradient(
                        0f to Color(0xFFFF3D68), 0.45f to Color(0xFFFF7A45), 0.75f to Color(0xFFFF4F8B), 1f to Color(0xFFFF3D68),
                        start = Offset(x, 0f), end = Offset(x + span, size.height * 1.4f),
                    ),
                )
            }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Bobbing(room.emoji, 34.sp, delayMs = 0)
            Spacer(Modifier.weight(1f))
            Pill(
                pluralStringResource(R.plurals.online, room.online, room.online), background = Color.White.copy(alpha = 0.2f), color = Color.White,
                leading = { PresenceDot(6.dp) },
            )
        }
        Spacer(Modifier.height(26.dp))
        Text(room.name(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = Color.White)
        room.about()?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(
                Modifier.height(40.dp).clip(CircleShape).background(Color.White).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.join), color = Brand.Night, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = Brand.Night)
            }
        }
    }
}

@Composable
private fun RoomCard(room: Room, tint: Color, bobDelay: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.height(164.dp).pressScale(interaction, 0.97f).clip(RoundedCornerShape(24.dp)).background(tint)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick).padding(16.dp),
    ) {
        Bobbing(room.emoji, 28.sp, delayMs = bobDelay)
        Spacer(Modifier.height(10.dp))
        Text(
            room.name(), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.5.sp, lineHeight = 19.sp), maxLines = 2,
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

/** An emoji that floats up and down a little, out of step with its neighbours. */
@Composable
private fun Bobbing(emoji: String, size: TextUnit, modifier: Modifier = Modifier, delayMs: Int) {
    val y by rememberLoop(1800, from = -1f, to = 1f, reverse = true, easing = FastOutSlowInEasing, offsetMs = delayMs % 1800, still = 0f)
    Text(emoji, modifier.graphicsLayer { translationY = y * 3.dp.toPx() }, fontSize = size)
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
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.back), vm::leaveRoom, background = Color.Transparent, iconSize = 20.dp)
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
            Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.Pink) } }
            if (!state.loading && state.messages.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.WavingHand, stringResource(R.string.room_empty_title),
                        stringResource(if (room.mine && !state.closed) R.string.room_empty_owner else R.string.room_empty),
                    )
                }
            }
            items(state.messages, key = { it.id }) { m ->
                Box(Modifier.animateItem()) { PopIn(m.fromMe) { RoomLine(m, onProfile = { vm.openProfile(m.sender) }) } }
            }
        }
        if (state.closed) {
            RoomClosedBar(vm::leaveRoom)
        } else {
            Composer(
                onText = vm::sendRoom, onMedia = null, onTyping = {}, maxLength = AppViewModel.MAX_ROOM_TEXT,
                hint = stringResource(R.string.message_room, room.name()), divider = true,
            )
        }
    }
    if (state.showCode && code != null) RoomCreatedDialog(room, code, vm::dismissRoomCode)
    if (confirmClose) CloseRoomDialog(onConfirm = { confirmClose = false; vm.closeRoom() }, onCancel = { confirmClose = false })
}

@Composable
private fun RoomLine(m: RoomMessage, onProfile: () -> Unit) {
    if (m.fromMe) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Bubble(Message(m.id, true, m.text, null, m.at))
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(end = 40.dp), verticalAlignment = Alignment.Top) {
        Avatar(m.sender, 34.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onProfile))
        Column(Modifier.padding(start = 10.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onProfile).padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(m.sender.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("${m.sender.age}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                flagOf(m.sender.country).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp) }
            }
            Text(
                m.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

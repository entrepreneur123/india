package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.model.Room
import com.yaari.chat.model.RoomMessage

/** Rooms the app knows get a translated name; new rooms added on the server show the server's title. */
private val RoomNames = mapOf(
    "lounge" to R.string.room_lounge, "language" to R.string.room_language, "music" to R.string.room_music,
    "movies" to R.string.room_movies, "gaming" to R.string.room_gaming, "sports" to R.string.room_sports,
    "night" to R.string.room_night, "memes" to R.string.room_memes, "study" to R.string.room_study, "travel" to R.string.room_travel,
)

@Composable
fun Room.name() = RoomNames[id]?.let { stringResource(it) } ?: title

/** Two-tone washes per room, light and dark. */
private val RoomWash = listOf(
    0xFFFFE1E8 to 0xFFFFF4F6, 0xFFDCEBFF to 0xFFF1F7FF, 0xFFDDF5E6 to 0xFFF2FBF5, 0xFFFFEBD1 to 0xFFFFF7EC,
    0xFFEAE0FF to 0xFFF6F1FF, 0xFFD5F2F5 to 0xFFEFFAFB,
)
private val RoomWashDark = listOf(
    0xFF3A1E26 to 0xFF221419, 0xFF1C2A3D to 0xFF131B26, 0xFF1C3326 to 0xFF131F18, 0xFF3A2C17 to 0xFF231C11,
    0xFF2B2240 to 0xFF1B1628, 0xFF173336 to 0xFF112122,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(900); refreshing = false } }
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_rooms), onSettings)
        PullToRefreshBox(refreshing, onRefresh = { refreshing = true; vm.refreshRooms() }, Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.rooms_sub), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
                if (s.rooms.isEmpty()) {
                    // Skeletons while the list loads, shaped like the real cards.
                    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)).shimmer()) }
                    items(4) { Box(Modifier.aspectRatio(1.05f).clip(RoundedCornerShape(22.dp)).shimmer()) }
                }
                s.rooms.firstOrNull()?.let { featured ->
                    item(key = featured.id, span = { GridItemSpan(maxLineSpan) }) { FeaturedRoom(featured) { vm.enterRoom(featured) } }
                }
                itemsIndexed(s.rooms.drop(1), key = { _, r -> r.id }) { i, room ->
                    val (from, to) = (if (dark) RoomWashDark else RoomWash)[i % RoomWash.size]
                    RoomCard(room, Brush.linearGradient(listOf(Color(from), Color(to))), bobDelay = i * 180) { vm.enterRoom(room) }
                }
            }
        }
    }
}

/** The busiest room, wide, in the brand gradient. */
@Composable
private fun FeaturedRoom(room: Room, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().height(150.dp).pressScale(interaction, 0.97f).clip(RoundedCornerShape(24.dp)).background(Brand.gradient)
            .clickable(interaction, indication = null, onClick = onClick).padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Pill(stringResource(R.string.room_featured), background = Color.White.copy(alpha = 0.22f), color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(room.name(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            OnlineCount(room.online, Color.White, dot = Color.White)
        }
        Bobbing(room.emoji, 64.sp, Modifier.align(Alignment.TopEnd), delayMs = 0)
    }
}

@Composable
private fun RoomCard(room: Room, wash: Brush, bobDelay: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.aspectRatio(1.05f).pressScale(interaction).clip(RoundedCornerShape(22.dp)).background(wash)
            .clickable(interaction, indication = null, onClick = onClick).padding(16.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Bobbing(room.emoji, 28.sp, delayMs = bobDelay)
        }
        Spacer(Modifier.weight(1f))
        Text(room.name(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        OnlineCount(room.online)
    }
}

/** An emoji that floats up and down a little, out of step with its neighbours. */
@Composable
private fun Bobbing(emoji: String, size: TextUnit, modifier: Modifier = Modifier, delayMs: Int) {
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, delayMillis = delayMs % 900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    Text(emoji, modifier.graphicsLayer { translationY = y * 4.dp.toPx() }, fontSize = size)
}

@Composable
private fun OnlineCount(n: Int, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, dot: Color = Brand.Online) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (n > 0) LiveDot(dot, 7.dp) else Box(Modifier.padding(4.dp).size(7.dp).clip(CircleShape).background(Color.Gray))
        Spacer(Modifier.width(4.dp))
        Text(pluralStringResource(R.plurals.online, n, n), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun RoomScreen(vm: AppViewModel, state: RoomState) {
    BackHandler(onBack = vm::leaveRoom)
    SecureScreen()
    val room = state.room
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(vm::leaveRoom) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Text(room.emoji, fontSize = 22.sp) }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(room.name(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OnlineCount(maxOf(room.online, 1))
            }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val list = rememberLazyListState()
        LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.size) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.room_rules), Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            items(state.messages, key = { it.id }) { m -> Box(Modifier.animateItem()) { RoomLine(m, onProfile = { vm.openProfile(m.sender) }) } }
        }
        Composer(onText = vm::sendRoom, onMedia = null, onTyping = {}, maxLength = AppViewModel.MAX_ROOM_TEXT)
    }
}

@Composable
private fun RoomLine(m: RoomMessage, onProfile: () -> Unit) {
    if (m.fromMe) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
            Bubble(com.yaari.chat.model.Message(m.id, true, m.text, null, m.at))
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
        Avatar(m.sender, 34.dp, Modifier.clip(CircleShape).clickable(onClick = onProfile))
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                "${m.sender.nameWithFlag()} · ${m.sender.age}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onProfile).padding(start = 4.dp, bottom = 2.dp),
            )
            Bubble(com.yaari.chat.model.Message(m.id, false, m.text, null, m.at))
        }
    }
}

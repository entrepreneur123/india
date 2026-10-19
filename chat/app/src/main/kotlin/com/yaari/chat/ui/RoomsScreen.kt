package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

private val RoomTints = listOf(0xFFFFE4EA, 0xFFE3F0FF, 0xFFE6F7EC, 0xFFFFF1DC, 0xFFEFE7FF, 0xFFDDF5F7)
private val RoomTintsDark = listOf(0xFF3A1E26, 0xFF1C2A3D, 0xFF1C3326, 0xFF3A2C17, 0xFF2B2240, 0xFF173336)

@Composable
fun RoomsTab(vm: AppViewModel, s: UiState, onSettings: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_rooms), onSettings)
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            items(s.rooms, key = { it.id }) { room ->
                val tints = if (dark) RoomTintsDark else RoomTints
                Column(
                    Modifier.aspectRatio(1.05f).clip(RoundedCornerShape(22.dp))
                        .background(Color(tints[s.rooms.indexOf(room) % tints.size]))
                        .clickable { vm.enterRoom(room) }.padding(16.dp),
                ) {
                    Text(room.emoji, fontSize = 34.sp)
                    Spacer(Modifier.weight(1f))
                    Text(room.name(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    OnlineCount(room.online)
                }
            }
        }
    }
}

@Composable
private fun OnlineCount(n: Int, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (n > 0) Brand.Online else Color.Gray))
        Spacer(Modifier.width(6.dp))
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
            IconButton(vm::leaveRoom) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
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
            items(state.messages, key = { it.id }) { m -> RoomLine(m, onProfile = { vm.openProfile(m.sender) }) }
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

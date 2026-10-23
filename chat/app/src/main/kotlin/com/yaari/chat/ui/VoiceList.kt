package com.yaari.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.domain.flagOf
import com.yaari.chat.R
import com.yaari.chat.model.Profile
import com.yaari.chat.model.VoiceRoomSummary

// ---- The rooms list: live bars, faces, the room card ----------------------------------------------------------

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

/** A room on the list, as on the web: the category's colour along the top, who is on the stage (or that it's chatting), music on, who may come in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceRoomCard(room: VoiceRoomSummary, here: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val color = topicColor(room.category.topic())
    val live = room.speakers.isNotEmpty()
    val blocked = room.refusal != null && !here
    val highlight = here || room.mine
    Column(
        modifier.fillMaxWidth().alpha(if (blocked) 0.6f else 1f).pressScale(interaction).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(if (highlight) 1.5.dp else 1.dp, if (highlight) color else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .drawBehind { drawRect(color.copy(alpha = 0.8f), size = Size(size.width, 4.dp.toPx())) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                TopicChip(room.category.topic(), stringResource(room.category.label))
                RoomLangChip(room.lang, Modifier.padding(start = 6.dp))
                if (room.rules.isPrivate) {
                    Icon(Icons.Rounded.Lock, stringResource(R.string.chip_private), Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (live || here) {
                LiveBars()
                Text(
                    stringResource(if (here) R.string.voice_in_room else R.string.voice_live), Modifier.padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            room.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (live) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaceStack(room.speakers, 30.dp, MaterialTheme.colorScheme.surface)
                Text(
                    room.speakers.joinToString(", ") { it.name }, Modifier.padding(start = 10.dp).weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicIcon(Topic.CHAT, size = 18.dp)
                Text(stringResource(R.string.rooms_chatting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        room.nowPlaying?.let { title ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TopicIcon(Topic.MUSIC, size = 15.dp)
                Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val conditions = conditionLabels(room.rules)
        if (conditions.isNotEmpty() || blocked) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (blocked) Tag(refusalLabel(room), MaterialTheme.colorScheme.error.copy(alpha = 0.1f), MaterialTheme.colorScheme.error)
                conditions.forEach { Tag(it, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(room.host, 20.dp)
            Text(
                if (room.mine) stringResource(R.string.voice_your_room) + (room.code?.let { " · $it" } ?: "") else stringResource(R.string.voice_hosted_by, room.host.name) + " " + flagOf(room.host.country),
                Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.Group, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.rooms_here, room.members, room.rules.maxMembers), Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(text: String, background: Color, color: Color) {
    Text(
        text, Modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = color, maxLines = 1,
    )
}

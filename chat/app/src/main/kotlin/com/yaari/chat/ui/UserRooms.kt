package com.yaari.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.model.GenderRule
import com.yaari.chat.model.NewRoom
import com.yaari.chat.model.Room
import com.yaari.chat.model.RoomCategory
import kotlin.math.roundToInt

// ---- User rooms: list cards, conditions, create sheet, code sheet, owner tools ------------------

/** Pastel per category, so a list of rooms reads at a glance (light and dark). */
@Composable
private fun categoryTint(c: RoomCategory): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val light = listOf(0xFFFFEEF3, 0xFFE9F7EF, 0xFFEEF0FF, 0xFFFFF0E6, 0xFFE8F3FF, 0xFFFFF7DF)
    val night = listOf(0xFF2C171E, 0xFF15261C, 0xFF1B1D33, 0xFF2A1D15, 0xFF15212F, 0xFF2A2514)
    return Color((if (dark) night else light)[c.ordinal % light.size])
}

/** The short reason on a room I can't enter ("Women only", "Ages 20–30", "Room is full", "Closed"). */
@Composable
fun refusalLabel(room: Room): String = when (room.refusal) {
    "room_gender" -> stringResource(if (room.genderRule == GenderRule.MALE) R.string.chip_men_only else R.string.chip_women_only)
    "room_age" -> stringResource(R.string.chip_ages, room.minAge, room.maxAge)
    "room_full" -> stringResource(R.string.refusal_full)
    "room_closed" -> stringResource(R.string.refusal_closed)
    else -> stringResource(R.string.refusal_other)
}

/** "3/12 online" for a room with a limit, "3 online" otherwise. */
@Composable
fun roomCount(room: Room): String = room.maxMembers?.let { stringResource(R.string.room_count, room.online, it) }
    ?: androidx.compose.ui.res.pluralStringResource(R.plurals.online, room.online, room.online)

/** Who may come in: gender, ages, private. Empty for an open room. */
@Composable
fun conditionLabels(room: Room): List<String> = buildList {
    when (room.genderRule) {
        GenderRule.FEMALE -> add(stringResource(R.string.chip_women_only))
        GenderRule.MALE -> add(stringResource(R.string.chip_men_only))
        GenderRule.ANY -> Unit
    }
    if (room.hasAgeLimit) add(stringResource(R.string.chip_ages, room.minAge, room.maxAge))
}

@Composable
private fun SmallChip(text: String, modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.background, color: Color = MaterialTheme.colorScheme.onSurface, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier.height(26.dp).clip(CircleShape).background(background).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        leading?.invoke()
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = color, maxLines = 1)
    }
}

/** Create (the brand colour, the obvious action) and Enter code, side by side under the featured room. */
@Composable
fun RoomActions(onCreate: () -> Unit, onCode: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(
            stringResource(R.string.rooms_create), onCreate, Modifier.weight(1.2f), height = 48.dp,
            leading = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Color.White) },
        )
        SoftButton(
            stringResource(R.string.rooms_enter_code), onCode, Modifier.weight(1f), height = 48.dp, outlined = true,
            leading = { Icon(Icons.Rounded.Tag, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 0.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun NoUserRooms(onCreate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onCreate).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            stringResource(R.string.rooms_by_people_empty), Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A room someone opened: who made it, how full it is and who may come in. Rooms I can't enter are dimmed with the reason. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserRoomCard(room: Room, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val refused = room.refusal != null
    val fade = Modifier.alpha(if (refused) 0.5f else 1f)
    Row(
        modifier.fillMaxWidth().pressScale(interaction).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(if (room.mine) 1.5.dp else 1.dp, if (room.mine) Brand.Pink else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(interaction, indication = ripple(), role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(fade.size(56.dp).clip(RoundedCornerShape(16.dp)).background(categoryTint(room.category)), contentAlignment = Alignment.Center) {
            Text(room.emoji, fontSize = 28.sp)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Row(fade, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (room.isPrivate) {
                    Icon(Icons.Rounded.Lock, stringResource(R.string.chip_private), Modifier.padding(start = 5.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val by = listOfNotNull(room.ownerName?.let { stringResource(R.string.room_by, it) }, stringResource(room.category.label))
            Text(
                by.joinToString(" · "), fade.padding(top = 1.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val reason = if (refused) refusalLabel(room) else null
                if (reason != null) {
                    SmallChip(
                        reason, background = Brand.Danger.copy(alpha = 0.1f), color = Brand.Danger,
                        leading = { Icon(Icons.Rounded.Block, null, Modifier.size(12.dp), tint = Brand.Danger) },
                    )
                }
                SmallChip(roomCount(room), fade, leading = { if (room.online > 0) PresenceDot(6.dp) else Box(Modifier.size(6.dp).clip(CircleShape).background(Color.Gray)) })
                conditionLabels(room).filter { it != reason }.forEach { SmallChip(it, fade) } // the reason already says it
                if (room.isPrivate) SmallChip(stringResource(R.string.chip_private), fade)
            }
        }
        if (room.mine) {
            Text(
                stringResource(R.string.room_yours), Modifier.padding(start = 8.dp).clip(CircleShape).background(Brand.Pink).padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White, style = MaterialTheme.typography.labelSmall,
            )
        } else if (!refused) {
            Box(Modifier.padding(start = 8.dp).size(32.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.join), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun SheetHeader(title: String, sub: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
        CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), onClose, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun brandSlider() = SliderDefaults.colors(
    thumbColor = Brand.Pink, activeTrackColor = Brand.Pink, inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

/** Title, topic, size, who may join, ages, private. The server checks every value again. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateRoomSheet(busy: Boolean, onDismiss: () -> Unit, onCreate: (NewRoom) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(RoomCategory.CHAT) }
    var max by rememberSaveable { mutableIntStateOf(10) }
    var who by rememberSaveable { mutableStateOf(GenderRule.ANY) }
    var minAge by rememberSaveable { mutableFloatStateOf(Room.MIN_AGE.toFloat()) }
    var maxAge by rememberSaveable { mutableFloatStateOf(Room.MAX_AGE.toFloat()) }
    var private by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SheetHeader(stringResource(R.string.create_title), stringResource(R.string.create_sub), onDismiss)
            Spacer(Modifier.height(18.dp))
            Field(
                title, { if (it.length <= Room.MAX_TITLE) title = it }, stringResource(R.string.create_name),
                counter = "${title.trim().length}/${Room.MAX_TITLE}",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            SheetLabel(stringResource(R.string.create_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomCategory.entries.forEach { c -> Choice("${c.emoji}  ${stringResource(c.label)}", category == c) { category = c } }
            }
            SheetLabel(stringResource(R.string.create_max), "$max")
            Slider(
                max.toFloat(), { max = it.roundToInt() }, valueRange = Room.MIN_MEMBERS.toFloat()..Room.MAX_MEMBERS.toFloat(),
                steps = Room.MAX_MEMBERS - Room.MIN_MEMBERS - 1, colors = brandSlider(),
                track = { SliderDefaults.Track(it, colors = brandSlider(), drawStopIndicator = null, drawTick = { _, _ -> }) },
            )
            SheetLabel(stringResource(R.string.create_who))
            Segmented(
                listOf(GenderRule.ANY to R.string.who_everyone, GenderRule.FEMALE to R.string.who_women, GenderRule.MALE to R.string.who_men),
                who,
            ) { who = it }
            SheetLabel(stringResource(R.string.create_ages), "${minAge.roundToInt()}–${maxAge.roundToInt()}")
            RangeSlider(
                minAge..maxAge, { r -> minAge = r.start.roundToInt().toFloat(); maxAge = r.endInclusive.roundToInt().toFloat() },
                valueRange = Room.MIN_AGE.toFloat()..Room.MAX_AGE.toFloat(), steps = Room.MAX_AGE - Room.MIN_AGE - 1, colors = brandSlider(),
                track = { SliderDefaults.Track(it, colors = brandSlider(), drawStopIndicator = null, drawTick = { _, _ -> }) },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Switch) { private = !private }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.create_private), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.create_private_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    private, { private = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.Pink, checkedThumbColor = Color.White),
                )
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                stringResource(R.string.rooms_create),
                { onCreate(NewRoom(title, category, max, who, minAge.roundToInt(), maxAge.roundToInt(), private)) },
                enabled = !busy && title.trim().length >= Room.MIN_TITLE,
            )
            Text(
                stringResource(R.string.create_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

/** A selectable chip: tonal brand colour with a check when chosen (Material filter chip). */
@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            )
            .selectable(selected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Three equal options in a tinted track; the chosen one sits on a white card. */
@Composable
private fun <T> Segmented(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape)
                    .then(if (on) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
                    .selectable(on, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Six characters, shown big and spaced like a ticket number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCodeSheet(busy: Boolean, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val ready = code.length == Room.CODE_LENGTH && !busy
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SheetHeader(stringResource(R.string.code_title), stringResource(R.string.code_sub), onDismiss)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                code, { v -> code = v.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(Room.CODE_LENGTH) },
                Modifier.fillMaxWidth(), singleLine = true, placeholder = {
                    Text("ABC123", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = codeStyle(), color = MaterialTheme.colorScheme.outline)
                },
                label = { Text(stringResource(R.string.code_hint)) },
                textStyle = codeStyle().copy(textAlign = TextAlign.Center), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (ready) onJoin(code) }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurface, cursorColor = Brand.Pink,
                ),
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(stringResource(R.string.join), { onJoin(code) }, enabled = ready)
        }
    }
}

@Composable
private fun codeStyle() = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = 6.sp)

// ---- Inside a user room ---------------------------------------------------------------------------

/** Topic, owner and who may come in, under the room's title. */
@Composable
fun RoomConditions(room: Room) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallChip("${room.category.emoji} ${stringResource(room.category.label)}")
        room.ownerName?.let { SmallChip(stringResource(R.string.room_by, it)) }
        conditionLabels(room).forEach { SmallChip(it) }
        if (room.isPrivate) {
            SmallChip(
                stringResource(R.string.chip_private),
                leading = { Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface) },
            )
        }
    }
}

/** The owner's room code: tap to copy, or share it. */
@Composable
fun RoomCodeBar(room: Room, code: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { copyCode(context, code) }.padding(vertical = 4.dp)) {
            Text(stringResource(R.string.room_code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(code, style = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.onPrimaryContainer))
        }
        CircleIconButton(Icons.Rounded.ContentCopy, stringResource(R.string.copy_code), { copyCode(context, code) }, tint = MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 20.dp)
        CircleIconButton(Icons.Rounded.Share, stringResource(R.string.share), { shareCode(context, room.title, code) }, tint = MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 20.dp)
    }
}

fun copyCode(context: Context, code: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.room_code), code))
    Toast.makeText(context, context.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
}

fun shareCode(context: Context, title: String, code: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_room_text, title, code))
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Shown in place of the composer once the owner closed the room. */
@Composable
fun RoomClosedBar(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 24.dp, vertical = 18.dp)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.err_room_closed), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            stringResource(R.string.room_closed_sub), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        SoftButton(stringResource(R.string.back_to_rooms), onBack, outlined = true, height = 48.dp)
    }
}

/** Right after creating: the code, big, with Share. */
@Composable
fun RoomCreatedDialog(room: Room, code: String, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.created_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (room.isPrivate) R.string.created_text_private else R.string.created_text), style = MaterialTheme.typography.bodyMedium)
                Text(
                    code,
                    Modifier.padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button) { copyCode(context, code) }.padding(horizontal = 22.dp, vertical = 12.dp),
                    style = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.onSurface),
                )
            }
        },
        confirmButton = { TextButton({ shareCode(context, room.title, code) }) { Text(stringResource(R.string.share), color = Brand.Pink) } },
        dismissButton = { TextButton(onDone) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
fun CloseRoomDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.close_room_title)) },
        text = { Text(stringResource(R.string.close_room_text)) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.close_room), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

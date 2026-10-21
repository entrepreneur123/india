package com.yaari.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.yaari.chat.model.NewVoiceRoom
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomRules
import com.yaari.chat.model.VoiceRoom
import com.yaari.chat.model.VoiceRoomSummary

// ---- Rooms: who may come in, the create sheet, the code sheet, the new room's code -----------------------------

/** The short reason on a room I can't enter ("Women only", "Ages 20–30", "Room is full", "Closed"). */
@Composable
fun refusalLabel(room: VoiceRoomSummary): String = when (room.refusal) {
    "room_gender" -> stringResource(if (room.rules.genderRule == GenderRule.MALE) R.string.chip_men_only else R.string.chip_women_only)
    "room_age" -> stringResource(R.string.chip_ages, room.rules.minAge, room.rules.maxAge)
    "room_full" -> stringResource(R.string.refusal_full)
    "room_closed" -> stringResource(R.string.refusal_closed)
    else -> stringResource(R.string.refusal_other)
}

/** Who may come in: gender and ages. Empty for a room open to everyone. */
@Composable
fun conditionLabels(rules: RoomRules): List<String> = buildList {
    when (rules.genderRule) {
        GenderRule.FEMALE -> add(stringResource(R.string.chip_women_only))
        GenderRule.MALE -> add(stringResource(R.string.chip_men_only))
        GenderRule.ANY -> Unit
    }
    if (rules.hasAgeLimit) add(stringResource(R.string.chip_ages, rules.minAge, rules.maxAge))
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
private fun SheetLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Talk, Music, Games as three chips, and what the chosen one is like underneath. */
@Composable
fun CategoryPicker(value: RoomCategory, onChange: (RoomCategory) -> Unit) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoomCategory.entries.forEach { c ->
                val on = value == c
                val color = topicColor(c.topic())
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.weight(1f).height(72.dp)
                        .then(if (!on && !night) Modifier.shadow(1.dp, shape) else Modifier)
                        .clip(shape)
                        .background(if (night && !on) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
                        .then(if (on) Modifier.background(color.copy(alpha = 0.14f)) else Modifier)
                        .border(if (on) 2.dp else 1.dp, if (on) color else MaterialTheme.colorScheme.outlineVariant, shape)
                        .selectable(on, role = Role.RadioButton) { onChange(c) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    TopicIcon(c.topic(), size = 26.dp)
                    Text(
                        stringResource(c.label), Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    )
                }
            }
        }
        Text(
            stringResource(value.body), Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Equal options in a tinted track; the chosen one sits on a raised card. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, label: String? = null, onSelect: (T) -> Unit) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)
            .selectableGroup().then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, text) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(40.dp)
                    .then(if (on) Modifier.shadow(1.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .then(if (on) Modifier.background(if (night) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface) else Modifier)
                    .selectable(on, role = Role.Tab) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** An age from 18 to 100 ("100+"), as a field that opens a list. */
@Composable
private fun AgeSelect(value: Int, label: String, modifier: Modifier, onChange: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val text = if (value == VoiceRoom.MAX_AGE) "${VoiceRoom.MAX_AGE}+" else "$value"
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(role = Role.DropdownList) { open = true }.semantics { contentDescription = "$label, $text" }
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 280.dp)) {
            (VoiceRoom.MIN_AGE..VoiceRoom.MAX_AGE).forEach { a ->
                DropdownMenuItem({ Text(if (a == VoiceRoom.MAX_AGE) "$a+" else "$a") }, { onChange(a); open = false })
            }
        }
    }
}

/** Name, category, size, who may join, ages, an optional pinned announcement, private. The server checks every value again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomSheet(busy: Boolean, initial: RoomCategory?, onDismiss: () -> Unit, onCreate: (NewVoiceRoom) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(initial ?: RoomCategory.TALK) }
    var max by rememberSaveable { mutableIntStateOf(VoiceRoom.DEFAULT_MEMBERS) }
    var who by rememberSaveable { mutableStateOf(GenderRule.ANY) }
    var minAge by rememberSaveable { mutableIntStateOf(VoiceRoom.MIN_AGE) }
    var maxAge by rememberSaveable { mutableIntStateOf(VoiceRoom.MAX_AGE) }
    var note by rememberSaveable { mutableStateOf("") }
    var private by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SheetHeader(stringResource(R.string.create_title), stringResource(R.string.create_sub), onDismiss)
            SheetLabel(stringResource(R.string.create_name), trailing = "${title.length}/${VoiceRoom.MAX_TITLE}")
            OutlinedTextField(
                title, { if (it.length <= VoiceRoom.MAX_TITLE) title = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(stringResource(R.string.create_name_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp), colors = fieldColors(),
            )
            SheetLabel(stringResource(R.string.create_category))
            CategoryPicker(category) { category = it }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.create_max), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                CircleIconButton(
                    Icons.Rounded.Remove, stringResource(R.string.create_fewer), { max = VoiceController.sizeDown(max) },
                    background = MaterialTheme.colorScheme.surfaceContainerHigh, size = 40.dp, iconSize = 20.dp, enabled = max > VoiceRoom.MIN_MEMBERS,
                )
                Text(
                    "$max", Modifier.width(56.dp).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                CircleIconButton(
                    Icons.Rounded.Add, stringResource(R.string.create_more), { max = VoiceController.sizeUp(max) },
                    background = MaterialTheme.colorScheme.surfaceContainerHigh, size = 40.dp, iconSize = 20.dp, enabled = max < VoiceRoom.MAX_MEMBERS,
                )
            }
            SheetLabel(stringResource(R.string.create_who))
            Segmented(
                listOf(
                    GenderRule.ANY to stringResource(R.string.who_everyone), GenderRule.FEMALE to stringResource(R.string.who_women),
                    GenderRule.MALE to stringResource(R.string.who_men),
                ),
                who, label = stringResource(R.string.create_who),
            ) { who = it }
            SheetLabel(stringResource(R.string.create_ages))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgeSelect(minAge, stringResource(R.string.create_youngest), Modifier.weight(1f)) { minAge = it; if (it > maxAge) maxAge = it }
                Text("–", Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AgeSelect(maxAge, stringResource(R.string.create_oldest), Modifier.weight(1f)) { maxAge = it; if (it < minAge) minAge = it }
            }
            SheetLabel(stringResource(R.string.create_announcement), trailing = "${note.length}/${VoiceRoom.MAX_ANNOUNCEMENT}")
            OutlinedTextField(
                note, { if (it.length <= VoiceRoom.MAX_ANNOUNCEMENT) note = it }, Modifier.fillMaxWidth().heightIn(min = 88.dp),
                placeholder = { Text(stringResource(R.string.create_announcement_placeholder)) }, minLines = 2, maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp), colors = fieldColors(),
            )
            Text(
                stringResource(R.string.create_announcement_hint), Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Switch) { private = !private }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.create_private), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(stringResource(R.string.create_private_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(private, null, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Pink, checkedThumbColor = Color.White))
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                stringResource(R.string.rooms_create),
                { onCreate(NewVoiceRoom(title, category, RoomRules(max, who, minAge, maxAge, private), note)) },
                enabled = !busy && title.trim().length >= VoiceRoom.MIN_TITLE,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedBorderColor = Brand.Pink, cursorColor = Brand.Pink,
)

/** Six characters, shown big and spaced like a ticket number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCodeSheet(busy: Boolean, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val ready = code.length == VoiceRoom.CODE_LENGTH && !busy
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SheetHeader(stringResource(R.string.code_title), stringResource(R.string.code_sub), onDismiss)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                code, { v -> code = v.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(VoiceRoom.CODE_LENGTH) },
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

fun copyCode(context: Context, code: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.room_code), code))
    Toast.makeText(context, context.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
}

fun shareCode(context: Context, title: String, code: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_room_text, title, code))
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Right after opening a private room: its code, big, to copy (tap) or share. */
@Composable
fun RoomCreatedDialog(title: String, code: String, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.created_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.created_text_private), style = MaterialTheme.typography.bodyMedium)
                val copy = stringResource(R.string.room_copy, code)
                Row(
                    Modifier.padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button) { copyCode(context, code) }.semantics(mergeDescendants = true) { contentDescription = copy }
                        .padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(code, style = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.onSurface))
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.padding(start = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton({ shareCode(context, title, code) }) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp), tint = Brand.Pink)
                Text(stringResource(R.string.share), Modifier.padding(start = 6.dp), color = Brand.Pink)
            }
        },
        dismissButton = { TextButton(onDone) { Text(stringResource(R.string.done)) } },
    )
}

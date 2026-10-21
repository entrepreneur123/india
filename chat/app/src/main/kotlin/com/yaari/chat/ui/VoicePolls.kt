package com.yaari.chat.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoicePoll

// ---- Room polls (029): a card in the chat, and the sheet hosts and co-hosts ask one with --------------------------

/** The poll colour (the drawn poll glyph's). */
private val PollColor = Color(Glyph.POLL.color)

/**
 * A poll in the chat: the question and its answers. Before I answer, the answers are big tappable rows; once I have
 * (or it is closed) each shows its share as a bar with the percent and count, my answer marked; while it is open
 * another tap changes my answer. [poll] null: not counted yet (shown as asked, nobody answered).
 */
@Composable
fun PollCard(line: VoiceLine.Poll, poll: VoicePoll?, onVote: (Int) -> Unit, onClose: (() -> Unit)?) {
    val p = poll ?: VoicePoll(line.pollId, line.question, line.options, List(line.options.size) { 0 }, 0, closed = false, by = line.sender.id)
    val results = p.closed || p.mine != null
    val kicker = stringResource(R.string.poll_kicker)
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer).background(PollColor.copy(alpha = 0.10f))
                .border(1.dp, PollColor.copy(alpha = 0.34f), RoundedCornerShape(22.dp))
                .semantics { paneTitle = kicker }
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.POLL, size = 18.dp)
                Text(
                    kicker.uppercase(), Modifier.padding(start = 6.dp).weight(1f), color = PollColor, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = Jakarta,
                )
                if (p.closed) {
                    Row(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.poll_closed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                p.question, Modifier.padding(top = 6.dp, bottom = 10.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.options.forEachIndexed { i, option ->
                    PollOption(option, i, p, results, enabled = !p.closed) { onVote(i) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(
                        pluralStringResource(R.plurals.poll_votes, p.total, p.total),
                        if (line.fromMe) stringResource(R.string.poll_by_you) else stringResource(R.string.poll_by, line.sender.name),
                        when {
                            p.closed -> null
                            p.mine == null -> stringResource(R.string.poll_tap_to_vote)
                            else -> stringResource(R.string.poll_change_hint)
                        },
                    ).filterNotNull().joinToString(" · "),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (onClose != null) {
                    Text(
                        stringResource(R.string.poll_close), Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClose)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = PollColor, style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** One answer: a radio row to tap, or (with [results]) its share as a filling bar, the percent and the count. */
@Composable
private fun PollOption(text: String, index: Int, poll: VoicePoll, results: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val mine = poll.mine == index
    val percent = poll.percent(index)
    val count = poll.count(index)
    val share by animateFloatAsState(if (results) percent / 100f else 0f, tween(450), label = "share")
    val border by animateColorAsState(if (mine) PollColor else MaterialTheme.colorScheme.outlineVariant, label = "border")
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val described = stringResource(R.string.poll_option_desc, text, percent, count)
    val yours = stringResource(R.string.poll_your_answer)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(interaction, 0.98f).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (mine) 2.dp else 1.dp, border, shape)
            .clickable(interaction, indication = ripple(), enabled = enabled && !mine, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = mine
                if (results) contentDescription = described
                if (mine) stateDescription = yours
            },
    ) {
        if (results) {
            Box(Modifier.matchParentSize()) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).background(PollColor.copy(alpha = if (mine) 0.34f else 0.18f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!results) {
                Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                Spacer(Modifier.size(10.dp))
            } else if (mine) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(PollColor), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Color.White)
                }
                Spacer(Modifier.size(10.dp))
            }
            Text(
                text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (results) {
                Text(
                    "$percent%", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (mine) PollColor else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Hosts and co-hosts: a question (1–120 characters) and 2 to 4 answers (1–40 each, all different), with rows to add
 * and remove. Ask is enabled only when the server would take it ([VoicePoll.valid]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollSheet(busy: Boolean, onDismiss: () -> Unit, onAsk: (String, List<String>) -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    val saved = rememberSaveable { mutableStateOf(listOf("", "")) }
    val options = remember { saved.value.toMutableStateList() }
    fun save() { saved.value = options.toList() }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    val dupes = filled.map { it.lowercase() }.let { it.size != it.distinct().size }
    val valid = VoicePoll.valid(question, filled)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PollColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.POLL, size = 26.dp)
                }
                Spacer(Modifier.size(12.dp))
                Box(Modifier.weight(1f)) { SheetTitle(stringResource(R.string.poll_new_title), null, onDismiss) }
            }
            Text(
                stringResource(R.string.poll_new_sub), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.poll_question), Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                question, { question = it.take(VoicePoll.MAX_QUESTION) }, Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.poll_question_hint)) }, maxLines = 3, shape = RoundedCornerShape(14.dp),
                supportingText = { Text("${question.trim().length} / ${VoicePoll.MAX_QUESTION}") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), colors = pollFieldColors(),
            )
            Text(stringResource(R.string.poll_answers), Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { i, o ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            o, { options[i] = it.take(VoicePoll.MAX_OPTION); save() }, Modifier.weight(1f), singleLine = true,
                            placeholder = { Text(stringResource(R.string.poll_option_n, i + 1)) }, shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), colors = pollFieldColors(),
                        )
                        if (options.size > VoicePoll.MIN_OPTIONS) {
                            CircleIconButton(
                                Icons.Rounded.Close, stringResource(R.string.poll_remove_option, i + 1), { options.removeAt(i); save() },
                                Modifier.padding(start = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (dupes) {
                Text(
                    stringResource(R.string.poll_same_answers), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium,
                )
            }
            if (options.size < VoicePoll.MAX_OPTIONS) {
                SoftButton(
                    stringResource(R.string.poll_add_option), { options.add(""); save() }, Modifier.padding(top = 12.dp), height = 44.dp, outlined = true,
                    leading = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                )
            }
            Spacer(Modifier.size(20.dp))
            PrimaryButton(
                stringResource(R.string.poll_ask), { onAsk(question.trim(), filled) }, enabled = valid && !busy, height = 52.dp,
                leading = { GlyphIcon(Glyph.POLL, size = 20.dp) },
            )
        }
    }
}

@Composable
private fun pollFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = PollColor, cursorColor = PollColor)

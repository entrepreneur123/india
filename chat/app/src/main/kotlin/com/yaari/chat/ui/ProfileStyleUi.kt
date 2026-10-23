package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.model.Profile
import com.yaari.chat.model.ProfilePrompt
import com.yaari.chat.model.ProfileStyle
import com.yaari.chat.model.PromptQ
import com.yaari.chat.model.SpokenLang

// ---- A profile's details (033): status, languages and prompts --------------------------------------------------------
// Orange marks and labels on plain white and neutral cards (no gradients). Profiles are decorated by the frames
// people buy (Looks.kt); the free cover and colour went with 0.15.

/** The avatar inside a saffron ring (the ring, a gap in the background colour, then the photo). */
@Composable
fun AccentAvatar(p: Profile, size: Dp, modifier: Modifier = Modifier, gap: Color = MaterialTheme.colorScheme.background, ring: Dp = 2.5.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Brand.Saffron).padding(ring).clip(CircleShape).background(gap).padding(ring), contentAlignment = Alignment.Center) {
        Avatar(p, size - ring * 4)
    }
}

/**
 * The status under the name, in quotation marks: italic and muted, so it reads as their own words and stays apart from
 * the ID-verified chip above it.
 */
@Composable
fun StatusLine(p: Profile, modifier: Modifier = Modifier) {
    val status = p.style.status?.trim()?.takeIf { it.isNotEmpty() } ?: return
    Text(
        stringResource(R.string.profile_status_quote, status), modifier, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

/** The languages someone speaks, by their own names, with a globe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagesRow(p: Profile, modifier: Modifier = Modifier) {
    val langs = p.style.languages.takeIf { it.isNotEmpty() } ?: return
    val label = stringResource(R.string.profile_languages)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = label + ": " + langs.joinToString { it.native } }, verticalAlignment = Alignment.Top) {
        MarkIcon(Mark.GLOBE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(top = 6.dp), size = 18.dp)
        FlowRow(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            langs.forEach { l ->
                Text(
                    l.native, Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Answered prompts as big plain cards: the question small in saffron, the answer large (Hinge style). */
@Composable
fun PromptCards(p: Profile, modifier: Modifier = Modifier) {
    val prompts = p.style.prompts.takeIf { it.isNotEmpty() } ?: return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        prompts.forEach { pr ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(stringResource(pr.q.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primaryText)
                Text(
                    pr.answer, Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

// ---- the editor ----------------------------------------------------------------------------------------------------

/** One prompt being edited (the question may still change). */
private class PromptDraft(q: PromptQ, answer: String) {
    var q by mutableStateOf(q)
    var answer by mutableStateOf(answer)
}

/**
 * "Profile details": a live preview on top, then the status, languages and prompts. Saved all at once
 * (set_profile_style takes them together).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleEditorScreen(vm: AppViewModel, me: Profile, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val social by vm.social.state.collectAsStateWithLifecycle()
    val start = me.style
    var status by rememberSaveable { mutableStateOf(start.status.orEmpty()) }
    var langs by rememberSaveable { mutableStateOf(start.languages.map { it.code }) }
    val prompts = remember { mutableStateListOf(*start.prompts.map { PromptDraft(it.q, it.answer) }.toTypedArray()) }
    val style = ProfileStyle(
        status = status.trim().ifEmpty { null }, languages = SpokenLang.of(langs), prompts = prompts.map { ProfilePrompt(it.q, it.answer.trim()) },
    )
    val ready = style.valid() && !social.savingStyle
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Column {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDone) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.profile_details), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ vm.social.saveStyle(style, onDone) }, enabled = ready) {
                        Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                // Live preview.
                val preview = me.copy(style = style)
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(preview, 64.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(me.nameWithFlag(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusLine(preview)
                    }
                }
                Section(stringResource(R.string.style_status)) {
                    Field(
                        status, { status = it.take(ProfileStyle.MAX_STATUS) }, stringResource(R.string.style_status_hint),
                        counter = "${status.length}/${ProfileStyle.MAX_STATUS}",
                    )
                }
                Section(stringResource(R.string.style_languages), stringResource(R.string.style_languages_hint, SpokenLang.MAX)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpokenLang.entries.forEach { l ->
                            val on = l.code in langs
                            val full = !on && langs.size >= SpokenLang.MAX
                            ToggleChip(l.native, on, enabled = !full) { langs = if (on) langs - l.code else langs + l.code }
                        }
                    }
                }
                Section(stringResource(R.string.style_prompts), stringResource(R.string.style_prompts_hint, ProfilePrompt.MAX)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        prompts.forEachIndexed { i, d ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QuestionPicker(d.q, used = prompts.map { it.q }.toSet() - d.q, color = MaterialTheme.colorScheme.primaryText, modifier = Modifier.weight(1f)) { d.q = it }
                                    IconButton({ prompts.removeAt(i) }) { Icon(Icons.Rounded.Close, stringResource(R.string.style_prompt_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Field(
                                    d.answer, { d.answer = it.take(ProfilePrompt.MAX_ANSWER) }, stringResource(R.string.style_prompt_answer),
                                    Modifier.padding(end = 8.dp), single = false, counter = "${d.answer.length}/${ProfilePrompt.MAX_ANSWER}",
                                )
                            }
                        }
                        if (prompts.size < ProfilePrompt.MAX) {
                            val free = PromptQ.entries.filter { q -> prompts.none { it.q == q } }
                            var open by remember { mutableStateOf(false) }
                            Box {
                                SoftButton(
                                    stringResource(R.string.style_prompt_add), { open = true }, outlined = true,
                                    leading = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                                )
                                DropdownMenu(open, { open = false }) {
                                    free.forEach { q -> DropdownMenuItem({ Text(stringResource(q.label)) }, { open = false; prompts += PromptDraft(q, "") }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A pill to switch on and off (languages), as the interests sheet's. */
@Composable
private fun ToggleChip(text: String, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(CircleShape)
            .then(if (on) Modifier.background(Brand.Saffron) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .toggleable(on, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(16.dp), tint = Brand.OnSaffron)
        Text(
            text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            color = when {
                on -> Brand.OnSaffron
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** The question of a prompt, as a menu of the ones not used yet. */
@Composable
private fun QuestionPicker(q: PromptQ, used: Set<PromptQ>, color: Color, modifier: Modifier = Modifier, onPick: (PromptQ) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.DropdownList) { open = true }.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(q.label), style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.ExpandMore, stringResource(R.string.style_prompt_pick), Modifier.size(20.dp), tint = color)
        }
        DropdownMenu(open, { open = false }) {
            PromptQ.entries.filter { it !in used }.forEach { o -> DropdownMenuItem({ Text(stringResource(o.label)) }, { open = false; onPick(o) }) }
        }
    }
}

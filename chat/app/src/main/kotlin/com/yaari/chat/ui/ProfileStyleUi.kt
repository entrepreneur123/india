package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yaari.chat.R
import com.yaari.chat.model.Accent
import com.yaari.chat.model.Profile
import com.yaari.chat.model.ProfilePrompt
import com.yaari.chat.model.ProfileStyle
import com.yaari.chat.model.PromptQ
import com.yaari.chat.model.SpokenLang

// ---- A profile's own look (033): cover, colour, status, languages and prompts ---------------------------------------
// The colours are a curated palette of two-stop gradients (as the web's): each tints the cover, the avatar ring, the
// prompt cards and the status, and stays readable in both themes.

/** An accent's gradient and the ink for text and small marks on the app's background. */
data class AccentColors(val start: Color, val end: Color, val ink: Color) {
    val brush get() = Brush.linearGradient(listOf(start, end))
}

private val Palette = mapOf(
    Accent.ROSE to AccentColors(Color(0xFFFF5C8A), Color(0xFFFF9A7B), Color(0xFFD81B60)),
    Accent.VIOLET to AccentColors(Color(0xFF7B5CFF), Color(0xFFC56CF0), Color(0xFF6A3DE8)),
    Accent.OCEAN to AccentColors(Color(0xFF2D7FF9), Color(0xFF22C1C3), Color(0xFF1767D6)),
    Accent.MINT to AccentColors(Color(0xFF12B886), Color(0xFF8CE99A), Color(0xFF0B8A64)),
    Accent.SUNSET to AccentColors(Color(0xFFFF6A3D), Color(0xFFFFC14D), Color(0xFFD9480F)),
    Accent.GOLD to AccentColors(Color(0xFFE6A100), Color(0xFFFFD86B), Color(0xFF9A6700)),
    Accent.BERRY to AccentColors(Color(0xFFC2185B), Color(0xFF8E3CF0), Color(0xFFAD1457)),
    Accent.SLATE to AccentColors(Color(0xFF3E4C63), Color(0xFF8193B2), Color(0xFF3E4C63)),
)
private val DefaultAccent = AccentColors(Brand.LogoPink, Brand.LogoOrange, Brand.Pink)

/** The colours of [a] (null: Yaari's own pink to orange); the ink is lighter at night so it stays readable. */
@Composable
fun accentColors(a: Accent?): AccentColors {
    val c = Palette[a] ?: DefaultAccent
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (night) c.copy(ink = lerp(c.start, Color.White, 0.35f)) else c
}

/** The avatar inside its accent ring (the gradient, a gap in the background colour, then the photo). */
@Composable
fun AccentAvatar(p: Profile, size: Dp, modifier: Modifier = Modifier, gap: Color = MaterialTheme.colorScheme.background, ring: Dp = 2.5.dp) {
    val colors = accentColors(p.style.accent)
    Box(modifier.size(size).clip(CircleShape).background(colors.brush).padding(ring).clip(CircleShape).background(gap).padding(ring), contentAlignment = Alignment.Center) {
        Avatar(p, size - ring * 4)
    }
}

/**
 * The cover: the photo, or the accent gradient with soft drawn shapes. [content] sits on top (the customise button).
 */
@Composable
fun ProfileCover(style: ProfileStyle, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val colors = accentColors(style.accent)
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(colors.brush)) {
        if (style.cover != null) {
            CoverImage(style.cover, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f))))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(Color.White.copy(alpha = 0.16f), radius = h * 0.9f, center = Offset(w * 0.92f, h * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.10f), radius = h * 0.55f, center = Offset(w * 0.12f, h * 1.05f))
                drawCircle(Color.White.copy(alpha = 0.22f), radius = h * 0.08f, center = Offset(w * 0.62f, h * 0.32f))
                drawCircle(Color.White.copy(alpha = 0.18f), radius = h * 0.04f, center = Offset(w * 0.34f, h * 0.22f))
            }
        }
        content()
    }
}

/** A cover from the server, the demo's drawn pictures or a file just picked. */
@Composable
private fun CoverImage(uri: String, modifier: Modifier) {
    if (uri.startsWith("demo://")) DemoArt(uri, modifier) else AsyncImage(uri, null, modifier, contentScale = ContentScale.Crop)
}

/** The status under the name: a small accent dot and the line, in the accent's ink. */
@Composable
fun StatusLine(p: Profile, modifier: Modifier = Modifier) {
    val status = p.style.status?.takeIf { it.isNotBlank() } ?: return
    val ink = accentColors(p.style.accent).ink
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accentColors(p.style.accent).brush))
        Text(
            status, Modifier.padding(start = 8.dp), color = ink, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The languages someone speaks, by their own names, with a globe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagesRow(p: Profile, modifier: Modifier = Modifier) {
    val langs = p.style.languages.takeIf { it.isNotEmpty() } ?: return
    val ink = accentColors(p.style.accent).ink
    val label = stringResource(R.string.profile_languages)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = label + ": " + langs.joinToString { it.native } }, verticalAlignment = Alignment.Top) {
        MarkIcon(Mark.GLOBE, ink, Modifier.padding(top = 6.dp), size = 18.dp)
        FlowRow(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            langs.forEach { l ->
                Text(
                    l.native, Modifier.clip(CircleShape).background(ink.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge, color = ink,
                )
            }
        }
    }
}

/** Answered prompts as big tinted cards: the question small in the accent, the answer large (Hinge style). */
@Composable
fun PromptCards(p: Profile, modifier: Modifier = Modifier) {
    val prompts = p.style.prompts.takeIf { it.isNotEmpty() } ?: return
    val colors = accentColors(p.style.accent)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        prompts.forEach { pr ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .background(Brush.linearGradient(listOf(colors.start.copy(alpha = 0.16f), colors.end.copy(alpha = 0.08f))))
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(stringResource(pr.q.label), style = MaterialTheme.typography.labelLarge, color = colors.ink)
                Text(
                    pr.answer, Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** On my profile while nothing is customised yet: what can be added, and a way in. */
@Composable
fun StyleNudge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = accentColors(null)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, colors.brush, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(colors.start.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            MarkIcon(Mark.PALETTE, colors.ink, size = 24.dp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(stringResource(R.string.style_nudge_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.style_nudge), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * "Customise profile": a live preview on top, then the cover, the colour, the status, languages and prompts. Saved all
 * at once (set_profile_style takes the whole style); a new cover is checked on this phone before anything is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleEditorScreen(vm: AppViewModel, me: Profile, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val social by vm.social.state.collectAsStateWithLifecycle()
    val start = me.style
    var accent by rememberSaveable { mutableStateOf(start.accent?.code) }
    var status by rememberSaveable { mutableStateOf(start.status.orEmpty()) }
    var langs by rememberSaveable { mutableStateOf(start.languages.map { it.code }) }
    var cover by rememberSaveable { mutableStateOf(start.cover) }
    var coverId by rememberSaveable { mutableStateOf(start.coverId) }
    var newCover by rememberSaveable { mutableStateOf<String?>(null) }
    val prompts = remember { mutableStateListOf(*start.prompts.map { PromptDraft(it.q, it.answer) }.toTypedArray()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newCover = uri.toString()
            cover = uri.toString()
        }
    }
    val style = ProfileStyle(
        cover = cover, coverId = coverId, accent = Accent.of(accent), status = status.trim().ifEmpty { null },
        languages = SpokenLang.of(langs), prompts = prompts.map { ProfilePrompt(it.q, it.answer.trim()) },
    )
    val ready = style.valid() && !social.savingStyle
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Column {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDone) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.customize_profile), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ vm.social.saveStyle(style, newCover, onDone) }, enabled = ready) {
                        Text(stringResource(if (social.savingStyle) R.string.photos_checking else R.string.save), style = MaterialTheme.typography.labelLarge)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                // Live preview.
                val preview = me.copy(style = style)
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    ProfileCover(style, Modifier.fillMaxWidth().height(128.dp))
                    Row(Modifier.padding(start = 16.dp, top = 88.dp), verticalAlignment = Alignment.Bottom) {
                        AccentAvatar(preview, 76.dp, gap = MaterialTheme.colorScheme.surface)
                        Column(Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                            Text(me.nameWithFlag(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StatusLine(preview)
                        }
                    }
                }
                Section(stringResource(R.string.style_cover)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(
                            stringResource(if (cover == null) R.string.style_cover_add else R.string.style_cover_change),
                            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            leading = { MarkIcon(Mark.IMAGE, MaterialTheme.colorScheme.onSurface, size = 18.dp) },
                        )
                        if (cover != null) SoftButton(stringResource(R.string.style_cover_remove), { cover = null; coverId = null; newCover = null }, outlined = true)
                    }
                }
                Section(stringResource(R.string.style_accent)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Swatch(null, accent == null) { accent = null }
                        Accent.entries.forEach { a -> Swatch(a, accent == a.code) { accent = a.code } }
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
                        val colors = accentColors(Accent.of(accent))
                        prompts.forEachIndexed { i, d ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .background(Brush.linearGradient(listOf(colors.start.copy(alpha = 0.12f), colors.end.copy(alpha = 0.06f))))
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QuestionPicker(d.q, used = prompts.map { it.q }.toSet() - d.q, color = colors.ink, modifier = Modifier.weight(1f)) { d.q = it }
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

/** A colour to choose: its gradient disc, a check and an outer ring when chosen. */
@Composable
private fun Swatch(a: Accent?, selected: Boolean, onClick: () -> Unit) {
    val colors = accentColors(a)
    val label = stringResource(a?.label ?: R.string.accent_default)
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, colors.ink, CircleShape) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(5.dp).clip(CircleShape).background(colors.brush),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = Color.White) }
}

/** A pill to switch on and off (languages), as the interests sheet's. */
@Composable
private fun ToggleChip(text: String, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(CircleShape)
            .then(if (on) Modifier.background(Brand.Pink) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .toggleable(on, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(16.dp), tint = Color.White)
        Text(
            text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            color = when {
                on -> Color.White
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

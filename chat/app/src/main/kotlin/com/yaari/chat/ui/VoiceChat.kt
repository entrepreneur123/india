package com.yaari.chat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.data.FakeVoice
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.VoiceLine
import com.yaari.chat.model.VoiceRoom
import kotlinx.coroutines.launch

// ---- A room's chat: bubbles, photos (blurred until tapped), icebreaker cards, system lines; and the composer ------

/** The talk colour (rooms are always dark: its night shade), for icebreaker cards. */
private val TalkColor = Color(Glyph.ICEBREAKER.color)

/**
 * The room's chat: compact WhatsApp-style bubbles, photos, icebreaker cards and centred system lines in my language. It
 * follows new lines while the reader is at the bottom; scrolled up, it stays put and offers a "new messages" pill
 * instead. [onIcebreaker]: the room offers question cards (a talk room): an empty one invites to post the first, and
 * the newest card offers another.
 */
@Composable
fun VoiceChat(lines: List<VoiceLine>?, modifier: Modifier, onIcebreaker: (() -> Unit)?, onPerson: (String) -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val count = lines?.size ?: 0
    val quiet = lines != null && lines.all { it is VoiceLine.System }
    val lastIcebreaker = lines?.lastOrNull { it is VoiceLine.Icebreaker }?.id
    // Following the newest line while the reader is at the bottom (the line before the newest still in view counts:
    // a new line has just arrived under it); scrolled up, it stays put and counts what came in.
    val atEnd by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    var seen by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        if (count > 0 && (atEnd || seen == 0)) {
            list.scrollToItem(count) // the last line (the safety note is item 0)
            seen = count
        }
    }
    LaunchedEffect(atEnd) { if (atEnd) seen = count }
    val unseen = if (atEnd) 0 else (count - seen).coerceAtLeast(0)
    val chat = stringResource(R.string.voice_chat)
    // Joins, seats and music changes are read out; chat lines are not (that would never stop talking).
    val lastSystem = lines?.lastOrNull { it is VoiceLine.System } as VoiceLine.System?
    Box(modifier.fillMaxWidth().semantics { paneTitle = chat }) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
            item(key = "safety") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GppGood, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.voice_safety), Modifier.padding(start = 6.dp), fontSize = 11.sp, lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontFamily = Jakarta,
                    )
                }
            }
            if (lines == null) {
                items(3) { Box(Modifier.padding(top = 8.dp).size(200.dp, 40.dp).clip(RoundedCornerShape(8.dp)).shimmer()) }
            } else {
                itemsIndexed(lines, key = { _, l -> l.id }) { i, line ->
                    when (line) {
                        is VoiceLine.System -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                VoiceController.systemText(line).text(), Modifier.clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            )
                        }
                        is VoiceLine.Icebreaker -> IcebreakerCard(line, if (line.id == lastIcebreaker) onIcebreaker else null)
                        is VoiceLine.Text -> {
                            val prev = lines.getOrNull(i - 1)
                            val grouped = prev is VoiceLine.Text && prev.sender.id == line.sender.id
                            Box(Modifier.padding(top = if (grouped) 2.dp else 8.dp)) { ChatLine(line, grouped) { onPerson(line.sender.id) } }
                        }
                        is VoiceLine.Photo -> Box(Modifier.padding(top = 8.dp)) { PhotoLine(line) { onPerson(line.sender.id) } }
                    }
                }
                if (quiet && onIcebreaker != null) item(key = "invite") { IcebreakerInvite(onIcebreaker) }
            }
        }
        Text(
            lastSystem?.let { VoiceController.systemText(it).text() }.orEmpty(),
            Modifier.size(1.dp).graphicsLayer { alpha = 0f }.semantics { liveRegion = LiveRegionMode.Polite },
        )
        AnimatedVisibility(unseen > 0, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut()) {
            Row(
                Modifier.height(36.dp).clip(CircleShape).background(Brand.Pink)
                    .clickable(role = Role.Button) { scope.launch { list.animateScrollToItem(count) } }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp), tint = Color.White)
                Text(stringResource(R.string.voice_new_messages), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A chat line, as in a group chat: mine on the right; others with avatar and coloured name on the first of a run. */
@Composable
private fun ChatLine(line: VoiceLine.Text, grouped: Boolean, onPerson: () -> Unit) {
    val colors = chatColors()
    if (line.fromMe) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.fillMaxWidth(0.88f), contentAlignment = Alignment.CenterEnd) {
                BubbleShell(mine = true, tail = !grouped, padding = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)) {
                    TextWithMeta(line.text, colors.text, meta = { BubbleMeta(line.at, mine = false, read = false) })
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(0.88f), verticalAlignment = Alignment.Top) {
        if (grouped) Spacer(Modifier.width(28.dp))
        else Avatar(line.sender, 28.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onPerson))
        BubbleShell(mine = false, tail = !grouped, modifier = Modifier.padding(start = 2.dp), padding = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 5.dp)) {
            Column {
                if (!grouped) SenderName(line.sender.name, line.sender.id, onPerson)
                TextWithMeta(line.text, colors.text, meta = { BubbleMeta(line.at, mine = false, read = false) })
            }
        }
    }
}

@Composable
private fun SenderName(name: String, id: String, onPerson: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        name, modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onPerson),
        color = chatColors().names[nameColorIndex(id)], fontSize = 13.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold, fontFamily = Jakarta, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** A photo in the chat: someone else's stays blurred behind "Tap to view" until tapped (the chat's own pattern). */
@Composable
private fun PhotoLine(line: VoiceLine.Photo, onPerson: () -> Unit) {
    val message = Message(line.id, line.fromMe, media = Media(line.url, MediaKind.IMAGE), at = line.at)
    val label = stringResource(R.string.photo_from, line.sender.name)
    val drawn: (@Composable () -> Unit)? = if (line.url == FakeVoice.SNAP) ({ DemoSnap(Modifier.size(240.dp, 180.dp)) }) else null
    if (line.fromMe) {
        Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }, contentAlignment = Alignment.CenterEnd) {
            Bubble(message, image = drawn)
        }
        return
    }
    Row(Modifier.fillMaxWidth(0.88f), verticalAlignment = Alignment.Top) {
        Avatar(line.sender, 28.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onPerson))
        Box(Modifier.padding(start = 2.dp).semantics { contentDescription = label }) {
            Bubble(message, header = { SenderName(line.sender.name, line.sender.id, onPerson, Modifier.padding(horizontal = 4.dp)) }, image = drawn)
        }
    }
}

/** The demo's holiday snap: a sunset over the sea, drawn (as the web's SVG), so it needs no network. */
@Composable
fun DemoSnap(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(0f to Color(0xFFFF9A62), 0.55f to Color(0xFFFFD39A), 1f to Color(0xFF8CC3CF)))
        drawCircle(Color(0xFFFFF2C9), radius = w * 70 / 640, center = Offset(w * 430 / 640, h * 250 / 480))
        fun wave(y0: Float, c1: Float, y1: Float, end: Float, color: Color) = drawPath(
            Path().apply {
                moveTo(0f, h * y0 / 480)
                quadraticTo(w * 160 / 640, h * c1 / 480, w * 320 / 640, h * y1 / 480)
                quadraticTo(w * 480 / 640, h * (2 * y1 - c1) / 480, w, h * end / 480)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            color,
        )
        wave(330f, 270f, 320f, 300f, Color(0xFF3F6F7A))
        wave(385f, 345f, 385f, 372f, Color(0xFF2C4F58))
    }
}

/** A conversation starter, shown as a card in the talk colour (not a bubble): the question in my language. */
@Composable
private fun IcebreakerCard(line: VoiceLine.Icebreaker, onAnother: (() -> Unit)?) {
    val title = stringResource(R.string.ib_button)
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer).background(TalkColor.copy(alpha = 0.13f))
                .border(1.dp, TalkColor.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
                .semantics(mergeDescendants = true) { paneTitle = title }
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.ICEBREAKER, size = 16.dp)
                Text(
                    title.uppercase(), Modifier.padding(start = 6.dp), color = TalkColor, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = Jakarta,
                )
            }
            Text(
                stringResource(VoiceController.prompt(line.prompt)), Modifier.padding(top = 6.dp, end = 8.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (line.fromMe) stringResource(R.string.ib_by_you) else stringResource(R.string.ib_by, line.name), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (onAnother != null) {
                    Text(
                        stringResource(R.string.ib_another), Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAnother)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = TalkColor, style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** An empty talk room: one tap posts the first question card. */
@Composable
private fun IcebreakerInvite(onPost: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TalkColor.copy(alpha = 0.09f))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlyphIcon(Glyph.ICEBREAKER, size = 44.dp)
            Text(stringResource(R.string.ib_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.ib_empty_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            SoftButton(stringResource(R.string.ib_post), onPost, accent = true, height = 44.dp)
        }
    }
}

/** Text, a photo (picked, then checked on this phone before it is sent) and a question card. */
@Composable
fun VoiceComposer(modifier: Modifier, photoBusy: Boolean, onPhoto: (String) -> Unit, onIcebreaker: () -> Unit, onSend: (String, () -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val can = text.isNotBlank()
    val send = {
        if (can) {
            val sent = text
            text = ""
            onSend(sent) { if (text.isEmpty()) text = sent }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onPhoto(it.toString()) } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val photoLabel = stringResource(if (photoBusy) R.string.photo_checking else R.string.photo_send)
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = !photoBusy, role = Role.Button) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .semantics { contentDescription = photoLabel },
            contentAlignment = Alignment.Center,
        ) {
            if (photoBusy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        val ib = stringResource(R.string.ib_post)
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onIcebreaker).semantics { contentDescription = ib },
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.ICEBREAKER, size = 24.dp) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val hint = stringResource(if (photoBusy) R.string.photo_checking else R.string.voice_chat_placeholder)
            if (text.isEmpty()) {
                Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                text, { text = it.take(VoiceRoom.MAX_TEXT) }, Modifier.fillMaxWidth().semantics { contentDescription = hint },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Brand.Pink), maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(44.dp).graphicsLayer { alpha = if (can) 1f else 0.4f }.clip(CircleShape).background(Brand.Pink)
                .clickable(enabled = can, role = Role.Button) { send() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.voice_send), Modifier.size(20.dp), tint = Color.White) }
    }
}

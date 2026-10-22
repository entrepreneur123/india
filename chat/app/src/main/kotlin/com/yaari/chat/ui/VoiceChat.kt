package com.yaari.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.yaari.chat.domain.TranslateKind
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import com.yaari.chat.model.Profile
import com.yaari.chat.model.VoicePoll
import com.yaari.chat.model.VoiceReply
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt
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
import androidx.compose.runtime.snapshotFlow
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
import com.yaari.chat.model.VoiceRole
import com.yaari.chat.model.VoiceRoom
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.launch

// ---- A room's chat: bubbles, photos (blurred until tapped), icebreaker cards, system lines; and the composer ------

/** The talk colour (rooms are always dark: its night shade), for icebreaker cards. */
private val TalkColor = Color(Glyph.ICEBREAKER.color)

/** What the room's chat needs besides its lines: my name (mentions), the polls' counts, and what lines can do. */
data class RoomChat(
    /** The room (its lines' translations belong to it). */
    val roomId: String = "",
    val myId: String = "",
    val myName: String = "",
    val polls: Map<Long, VoicePoll> = emptyMap(),
    /** People present (their @mentions are drawn bold). */
    val names: List<String> = emptyList(),
    val onReply: (VoiceLine) -> Unit = {},
    val onVote: (Long, Int) -> Unit = { _, _ -> },
    val onClosePoll: (Long) -> Unit = {},
    val canClose: (VoicePoll) -> Boolean = { false },
    /** The host's and co-hosts' ids, for the badge by their name. */
    val roles: Map<String, VoiceRole> = emptyMap(),
    /** Who is typing right now (names, never mine). */
    val typing: List<String> = emptyList(),
    /** A link tapped in a line (null: the app's viewer as in any chat). The room passes one that offers sharing. */
    val onLink: ((String) -> Unit)? = null,
    /** A YouTube link's "Request this song". */
    val onRequestSong: ((String) -> Unit)? = null,
)

/**
 * The room's chat: compact WhatsApp-style bubbles, photos, polls, icebreaker cards and centred system lines in my
 * language. A line is answered by swiping it right or from its long-press menu; a quoted line, tapped, scrolls to the
 * original (when it is loaded) and lights it up for a moment. Lines that mention me (@ my name) stand out. It follows
 * new lines while the reader is at the bottom; scrolled up, it stays put and offers a "new messages" pill instead.
 * [onIcebreaker]: the room offers question cards (a talk room): an empty one invites to post the first, and the newest
 * card offers another.
 */
@Composable
fun VoiceChat(lines: List<VoiceLine>?, modifier: Modifier, onIcebreaker: (() -> Unit)?, onPerson: (String) -> Unit, chat: RoomChat = RoomChat()) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val count = lines?.size ?: 0
    val quiet = lines != null && lines.all { it is VoiceLine.System }
    val lastIcebreaker = lines?.lastOrNull { it is VoiceLine.Icebreaker }?.id
    // Following the newest line while the reader is at the bottom: also when the last line grows or the chat gets
    // shorter (the quote bar, the keyboard). Scrolled up by hand, it stays put and counts what came in.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        // After each scroll (by hand, or a jump to a quoted line): following again only when it ended at the bottom.
        snapshotFlow { list.isScrollInProgress }.drop(1).collect { moving -> if (!moving) follow = !list.canScrollForward }
    }
    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            Triple(info.totalItemsCount, info.visibleItemsInfo.lastOrNull()?.let { it.offset + it.size }, info.viewportEndOffset)
        }.collect { (total, _, _) ->
            if (follow && total > 1 && !list.isScrollInProgress && list.canScrollForward) list.scrollToItem(total - 1, FAR)
        }
    }
    var seen by remember { mutableIntStateOf(0) }
    LaunchedEffect(follow, count) { if (follow) seen = count }
    val unseen = if (follow) 0 else (count - seen).coerceAtLeast(0)
    val chatName = stringResource(R.string.voice_chat)
    // A quote tapped: the original, when loaded, scrolls into view and lights up for a moment.
    var lit by remember { mutableStateOf<Long?>(null) }
    val jump: (Long) -> Unit = { id ->
        val index = lines?.indexOfFirst { it.id == id } ?: -1
        if (index >= 0) scope.launch {
            list.animateScrollToItem(index + 1)
            lit = id
            delay(LIT_MS)
            if (lit == id) lit = null
        }
    }
    // Joins, seats and music changes are read out; chat lines are not (that would never stop talking).
    val lastSystem = lines?.lastOrNull { it is VoiceLine.System } as VoiceLine.System?
    Box(modifier.fillMaxWidth().semantics { paneTitle = chatName }) {
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
                    val glow by animateColorAsState(if (lit == line.id) Brand.Pink.copy(alpha = 0.22f) else Color.Transparent, tween(300), label = "lit")
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(glow)) {
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
                                val grouped = prev is VoiceLine.Text && prev.sender.id == line.sender.id && line.reply == null
                                val translation = lineTranslation(TranslateKind.ROOM, line.id, line.text, line.fromMe, chat.roomId)
                                Replyable(line, chat.onReply, translation) { menu ->
                                    Box(Modifier.padding(top = if (grouped) 2.dp else 8.dp)) {
                                        ChatLine(line, grouped, chat, menu, onQuote = jump, translation = translation) { onPerson(line.sender.id) }
                                    }
                                }
                            }
                            is VoiceLine.Photo -> Replyable(line, chat.onReply) { _ ->
                                Box(Modifier.padding(top = 8.dp)) { PhotoLine(line) { onPerson(line.sender.id) } }
                            }
                            is VoiceLine.Poll -> Replyable(line, chat.onReply) { _ ->
                                val poll = chat.polls[line.pollId]
                                PollCard(
                                    line, poll, onVote = { choice -> chat.onVote(line.pollId, choice) },
                                    onClose = poll?.takeIf(chat.canClose)?.let { p -> { chat.onClosePoll(p.id) } },
                                )
                            }
                        }
                    }
                }
                if (quiet && onIcebreaker != null) item(key = "invite") { IcebreakerInvite(onIcebreaker) }
                item(key = "typing") {
                    AnimatedVisibility(chat.typing.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        var last by remember { mutableStateOf(chat.typing) }
                        if (chat.typing.isNotEmpty()) last = chat.typing
                        RoomTyping(last)
                    }
                }
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

/**
 * "Asha is typing…" / "3 people are typing…" under the last line: three hopping dots in an incoming bubble (the random
 * chat's own), with who it is.
 */
@Composable
private fun RoomTyping(names: List<String>) {
    val text = if (names.size == 1) stringResource(R.string.room_typing_one, names[0])
    else pluralStringResource(R.plurals.room_typing_many, names.size, names.size)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(30.dp))
        TypingBubble()
        Text(
            text, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Far past the last line's top: scrolling there ends the chat at its bottom. */
private const val FAR = 1_000_000

/** How long a line tapped in a quote stays lit. */
private const val LIT_MS = 1_600L

/** How far a line is swiped right to answer it. */
private val SWIPE_REPLY = 64.dp

/**
 * A line that can be answered: swiped right (the reply arrow grows in behind it, a tick when far enough) or from its
 * long-press menu (Reply, Copy for text), and with TalkBack's "Reply" action. [content] gets the long-press handler to
 * put on its bubble.
 */
@Composable
private fun Replyable(
    line: VoiceLine, onReply: (VoiceLine) -> Unit, translation: LineTranslation? = null, content: @Composable (menu: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val limit = with(density) { SWIPE_REPLY.toPx() }
    // The swipe goes towards the end side (right, or left in a right-to-left layout), as the offset is drawn.
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val tick = rememberTick()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val replyLabel = stringResource(R.string.chat_reply)
    val copied = stringResource(R.string.chat_copied)
    val translateLabel = stringResource(R.string.translate)
    val originalLabel = stringResource(R.string.translate_show_original)
    val canTranslate = translation != null && translation.view.canTranslate
    val translated = translation != null && translation.view.translated
    Box(
        Modifier.fillMaxWidth()
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(replyLabel) { onReply(line); true },
                    if (canTranslate) CustomAccessibilityAction(translateLabel) { translation?.translate?.invoke(); true } else null,
                    if (translated) CustomAccessibilityAction(originalLabel) { translation?.showOriginal?.invoke(); true } else null,
                )
            }
            .pointerInput(line.id, towardsEnd) {
                var passed = false
                detectHorizontalDragGestures(
                    onDragStart = { passed = false },
                    onDragEnd = {
                        if (offset.value >= limit) onReply(line)
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, drag ->
                    change.consume()
                    val next = (offset.value + drag * towardsEnd * 0.6f).coerceIn(0f, limit * 1.4f)
                    if (!passed && next >= limit) {
                        passed = true
                        tick()
                    }
                    scope.launch { offset.snapTo(next) }
                }
            },
    ) {
        val shown = (offset.value / limit).coerceIn(0f, 1f)
        if (shown > 0f) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(32.dp)
                    .graphicsLayer { alpha = shown; scaleX = 0.6f + 0.4f * shown; scaleY = 0.6f + 0.4f * shown }
                    .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) {
            content { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text(replyLabel) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Reply, null) },
                    onClick = { menu = false; onReply(line) },
                )
                if (canTranslate) {
                    DropdownMenuItem(
                        text = { Text(translateLabel) }, leadingIcon = { Icon(Icons.Rounded.Translate, null) },
                        onClick = { menu = false; translation?.translate?.invoke() },
                    )
                }
                if (translated) {
                    DropdownMenuItem(
                        text = { Text(originalLabel) }, leadingIcon = { Icon(Icons.Rounded.Translate, null) },
                        onClick = { menu = false; translation?.showOriginal?.invoke() },
                    )
                }
                if (line is VoiceLine.Text) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_copy)) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = {
                            menu = false
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Yaari", line.text))
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

/** "@Name" of the people present (and mine) in bold and the name colour; the rest as it is. */
@Composable
private fun withMentions(text: String, names: List<String>, color: Color): AnnotatedString {
    if ('@' !in text || names.isEmpty()) return AnnotatedString(text)
    val sorted = remember(names) { names.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length } }
    return remember(text, sorted, color) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                val at = text.indexOf('@', i)
                if (at < 0) {
                    append(text.substring(i))
                    break
                }
                append(text.substring(i, at))
                val name = sorted.firstOrNull { text.regionMatches(at + 1, it, 0, it.length, ignoreCase = true) && text.getOrNull(at + 1 + it.length)?.isLetterOrDigit() != true }
                if (name == null) {
                    append('@')
                    i = at + 1
                } else {
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text.substring(at, at + 1 + name.length)) }
                    i = at + 1 + name.length
                }
            }
        }
    }
}

/**
 * A chat line, as in a group chat: mine on the right; others with avatar and coloured name on the first of a run. A
 * reply carries its quote on top (tapped: the original). A line that mentions me stands out with the brand colour.
 */
@Composable
private fun ChatLine(
    line: VoiceLine.Text, grouped: Boolean, chat: RoomChat, onMenu: () -> Unit, onQuote: (Long) -> Unit,
    translation: LineTranslation? = null, onPerson: () -> Unit,
) {
    val colors = chatColors()
    val mentioned = !line.fromMe && VoiceController.mentions(line.text, chat.myName)
    val shown = translation?.view?.text ?: line.text
    val text = rememberLinked(withMentions(shown, chat.names + chat.myName, Brand.PinkOnDark), colors.link, chat.onLink)
    val card: @Composable () -> Unit = { MessageLinkCard(line.text, Modifier.padding(top = 2.dp, bottom = 4.dp), chat.onRequestSong, chat.onLink) }
    val press = Modifier.combinedClickable(interactionSource = null, indication = null, onLongClick = onMenu, onClick = {})
    val quote: @Composable () -> Unit = { line.reply?.let { r -> Quote(r, mine = r.senderId == chat.myId, onClick = { onQuote(r.id) }) } }
    if (line.fromMe) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.fillMaxWidth(0.88f), contentAlignment = Alignment.CenterEnd) {
                BubbleShell(mine = true, tail = !grouped, modifier = press, padding = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)) {
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        quote()
                        card()
                        TextWithMeta(text, colors.text, meta = { BubbleMeta(line.at, mine = false, read = false) })
                    }
                }
            }
        }
        return
    }
    val mentionsYou = stringResource(R.string.chat_mentions_you)
    Row(
        Modifier.fillMaxWidth(0.88f).then(
            if (mentioned) Modifier.clip(RoundedCornerShape(14.dp)).background(Brand.Pink.copy(alpha = 0.12f))
                .border(1.dp, Brand.Pink.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(4.dp)
                .semantics { stateDescription = mentionsYou }
            else Modifier,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        if (grouped) Spacer(Modifier.width(28.dp))
        else Avatar(line.sender, 28.dp, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onPerson))
        BubbleShell(mine = false, tail = !grouped, modifier = Modifier.padding(start = 2.dp).then(press), padding = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 5.dp)) {
            Column(Modifier.width(IntrinsicSize.Max)) {
                if (!grouped) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SenderName(line.sender.name, line.sender.id, onPerson, Modifier.weight(1f, fill = false))
                        when (chat.roles[line.sender.id]) {
                            VoiceRole.HOST -> RoleBadge(stringResource(R.string.voice_host), Modifier.padding(start = 6.dp, bottom = 2.dp))
                            VoiceRole.COHOST -> RoleBadge(stringResource(R.string.voice_cohost), Modifier.padding(start = 6.dp, bottom = 2.dp))
                            else -> Unit
                        }
                    }
                }
                quote()
                card()
                translation?.let { TranslationNote(it, Modifier.padding(bottom = 2.dp)) }
                TextWithMeta(text, colors.text, meta = { BubbleMeta(line.at, mine = false, read = false) })
            }
        }
    }
}

/** The line a reply answers, inside the bubble: a coloured edge, who wrote it, and the text (a photo, a poll). */
@Composable
private fun Quote(reply: VoiceReply, mine: Boolean, onClick: () -> Unit) {
    val color = chatColors().names[nameColorIndex(reply.senderId)]
    val name = if (mine) stringResource(R.string.chat_quote_you) else reply.name
    val body = when (reply.kind) {
        VoiceReply.Kind.PHOTO -> stringResource(R.string.chat_quote_photo)
        VoiceReply.Kind.POLL -> stringResource(R.string.chat_quote_poll, reply.text.orEmpty())
        VoiceReply.Kind.TEXT -> reply.text.orEmpty()
    }
    val label = stringResource(R.string.chat_quote_of, name, body)
    Row(
        Modifier.padding(top = 2.dp, bottom = 4.dp).fillMaxWidth().widthIn(min = 120.dp).height(IntrinsicSize.Min).clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.18f)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 5.dp)) {
            Text(name, color = color, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Jakarta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (reply.kind) {
                    VoiceReply.Kind.PHOTO -> Icon(Icons.Outlined.Image, null, Modifier.padding(end = 4.dp).size(14.dp), tint = chatColors().meta)
                    VoiceReply.Kind.POLL -> GlyphIcon(Glyph.POLL, Modifier.padding(end = 4.dp), size = 14.dp)
                    VoiceReply.Kind.TEXT -> Unit
                }
                Text(body, color = chatColors().text.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 17.sp, fontFamily = Jakarta, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/**
 * The room's composer: what I answer (the quote bar, with a way to cancel), the attachments ("+": a photo, picked and
 * checked on this phone before it is sent; a poll for hosts and co-hosts), a question card, and the text, where "@"
 * offers the people present to mention ([people]).
 */
@Composable
fun VoiceComposer(
    modifier: Modifier, photoBusy: Boolean, onPhoto: (String) -> Unit, onIcebreaker: () -> Unit,
    replyTo: VoiceReply? = null, myId: String = "", onCancelReply: () -> Unit = {}, people: List<Profile> = emptyList(),
    onPoll: (() -> Unit)? = null, onTyping: () -> Unit = {}, onSend: (String, () -> Unit) -> Unit,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val text = field.text
    val can = text.isNotBlank()
    val focus = remember { FocusRequester() }
    val send = {
        if (can) {
            val sent = text
            field = TextFieldValue("")
            onSend(sent) { if (field.text.isEmpty()) field = TextFieldValue(sent, TextRange(sent.length)) }
        }
    }
    // Answering a line: the keyboard comes up, as in any messenger.
    LaunchedEffect(replyTo?.id) { if (replyTo != null) runCatching { focus.requestFocus() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onPhoto(it.toString()) } }
    // "@" at the end of what I type: the people present whose name starts with what follows (or contains it).
    val query = if (field.selection.collapsed && field.selection.end == text.length) VoiceController.mentionQuery(text) else null
    val matches = remember(query, people) {
        query?.let { q ->
            people.distinctBy { it.id }.filter { it.name.startsWith(q, ignoreCase = true) }
                .plus(people.filter { q.isNotEmpty() && it.name.contains(q, ignoreCase = true) && !it.name.startsWith(q, ignoreCase = true) })
                .distinctBy { it.id }.take(MENTION_ROWS)
        }.orEmpty()
    }
    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(matches.isNotEmpty(), enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(), exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()) {
            MentionPicker(matches) { p ->
                val next = VoiceController.insertMention(text, p.name)
                field = TextFieldValue(next, TextRange(next.length))
            }
        }
        AnimatedVisibility(replyTo != null, enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(), exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()) {
            var last by remember { mutableStateOf(replyTo) }
            replyTo?.let { last = it }
            last?.let { QuoteBar(it, mine = it.senderId == myId, onCancelReply) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val photoLabel = stringResource(if (photoBusy) R.string.photo_checking else R.string.photo_send)
            var attach by remember { mutableStateOf(false) }
            val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Box {
                val label = if (onPoll != null) stringResource(R.string.chat_attach) else photoLabel
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !photoBusy, role = Role.Button) { if (onPoll != null) attach = true else pickPhoto() }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        photoBusy -> CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                        onPoll != null -> Icon(Icons.Rounded.Add, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        else -> Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(attach, onDismissRequest = { attach = false }, shape = RoundedCornerShape(16.dp)) {
                    DropdownMenuItem(
                        text = { Text(photoLabel) }, leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                        onClick = { attach = false; pickPhoto() },
                    )
                    if (onPoll != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.poll_action)) }, leadingIcon = { GlyphIcon(Glyph.POLL, size = 22.dp) },
                            onClick = { attach = false; onPoll() },
                        )
                    }
                }
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
                    field, { v ->
                        // A real edit (not the cursor moving) tells the room I'm typing (at most every 2.5 s).
                        if (v.text != field.text && v.text.isNotBlank()) onTyping()
                        field = if (v.text.length > VoiceRoom.MAX_TEXT) v.copy(text = v.text.take(VoiceRoom.MAX_TEXT)) else v
                    },
                    Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
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
}

/** At most this many people are offered for a mention. */
private const val MENTION_ROWS = 4

/** The line I'm answering, above the composer: who wrote it and what, and the cross to answer nothing. */
@Composable
private fun QuoteBar(reply: VoiceReply, mine: Boolean, onCancel: () -> Unit) {
    val color = chatColors().names[nameColorIndex(reply.senderId)]
    val name = if (mine) stringResource(R.string.chat_quote_you) else reply.name
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.padding(start = 10.dp).size(18.dp), tint = color)
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.chat_replying_to, name), color = color, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (reply.kind) {
                    VoiceReply.Kind.PHOTO -> stringResource(R.string.chat_quote_photo)
                    VoiceReply.Kind.POLL -> stringResource(R.string.chat_quote_poll, reply.text.orEmpty())
                    VoiceReply.Kind.TEXT -> reply.text.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(Icons.Rounded.Close, stringResource(R.string.chat_cancel_reply), onCancel, iconSize = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The people present whose name matches what follows "@"; a tap writes "@Name ". */
@Composable
private fun MentionPicker(people: List<Profile>, onPick: (Profile) -> Unit) {
    val title = stringResource(R.string.chat_mention_pick)
    Column(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)).semantics { paneTitle = title }.padding(vertical = 4.dp),
    ) {
        people.forEach { p ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(p) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p, 32.dp)
                Text(
                    p.name, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("@", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

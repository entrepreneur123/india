package com.yaari.chat.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.yaari.chat.R
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import java.util.Date
import kotlin.math.abs

// ---- Chat look ----------------------------------------------------------------------------------
// The messenger look people everywhere use every day: green (mine) / white (theirs) bubbles on a warm wallpaper, a tail on
// the first bubble of a run, the time and read ticks tucked into the bubble's corner. Same tokens as the web
// (chat/web/app/globals.css).

@Immutable
class ChatColors(
    val wall: Color, val out: Color, val incoming: Color, val text: Color, val meta: Color, val tick: Color,
    val shadow: Color, val names: List<Color>,
    /** Links in messages. */
    val link: Color,
)

private val LightChat = ChatColors(
    wall = Color(0xFFEFEAE2), out = Color(0xFFD9FDD3), incoming = Color.White, text = Color(0xFF111B21),
    meta = Color(0xFF667781), tick = Color(0xFF53BDEB), shadow = Color(0xFF0B141A).copy(alpha = 0.13f),
    names = listOf(0xFF1F7AEC, 0xFFC2410C, 0xFF7C3AED, 0xFF0F766E, 0xFFBE185D, 0xFF15803D, 0xFFA16207, 0xFF4338CA).map { Color(it) },
    link = Color(0xFF027EB5),
)

private val DarkChat = ChatColors(
    wall = Color(0xFF0B141A), out = Color(0xFF005C4B), incoming = Color(0xFF202C33), text = Color(0xFFE9EDEF),
    meta = Color(0xFFE9EDEF).copy(alpha = 0.6f), tick = Color(0xFF53BDEB), shadow = Color.Black.copy(alpha = 0.13f),
    names = listOf(0xFF6BB2FF, 0xFFFF9B6B, 0xFFB69CFF, 0xFF4FD1C5, 0xFFFF7EB6, 0xFF6FDC8C, 0xFFF6C453, 0xFFA5B4FC).map { Color(it) },
    link = Color(0xFF53BDEB),
)

/** Follows the screen's theme: the system setting, or always dark on the random chat ([NightTheme]). */
@Composable
fun chatColors() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkChat else LightChat

/** A steady colour per person, like names in a group chat (the web's nameColor: h = h * 31 + char, wrapping). */
fun nameColorIndex(id: String): Int {
    var h = 0
    for (c in id) h = h * 31 + c.code
    return (abs(h.toLong()) % 8).toInt()
}

private val TailWidth = 8.dp
private val Radius = 8.dp

/**
 * Bubble outline: an 8 dp rounded body, and on the first bubble of a run the tail at the top outer corner (that
 * corner square). The 8 dp tail gutter is always reserved, so grouped bubbles line up with the first one.
 * [mine]: the tail and gutter on the right (my bubbles left to right; theirs in a right-to-left layout).
 */
private fun bubblePath(w: Float, h: Float, mine: Boolean, tail: Boolean, d: Density): Path = with(d) {
    val t = TailWidth.toPx()
    val r = CornerRadius(Radius.toPx())
    val square = CornerRadius.Zero
    val left = if (mine) 0f else t
    val right = if (mine) w - t else w
    Path().apply {
        addRoundRect(
            RoundRect(
                left, 0f, right, h,
                topLeftCornerRadius = if (tail && !mine) square else r,
                topRightCornerRadius = if (tail && mine) square else r,
                bottomRightCornerRadius = r, bottomLeftCornerRadius = r,
            ),
        )
        if (!tail) return@apply
        // The web's 8 x 13 tail (message-bubble.tsx), in dp from the body's outer edge.
        val u = 1.dp.toPx()
        if (mine) {
            fun x(v: Float) = right + v * u
            moveTo(x(5.188f), 0f)
            lineTo(x(0f), 0f)
            lineTo(x(0f), 11.193f * u)
            lineTo(x(6.467f), 2.568f * u)
            cubicTo(x(7.526f), 1.156f * u, x(6.958f), 0f, x(5.188f), 0f)
        } else {
            fun x(v: Float) = v * u // the gutter is x 0..8
            moveTo(x(2.812f), 0f)
            lineTo(x(8f), 0f)
            lineTo(x(8f), 11.193f * u)
            lineTo(x(1.533f), 2.568f * u)
            cubicTo(x(0.474f), 1.156f * u, x(1.042f), 0f, x(2.812f), 0f)
        }
        close()
    }
}

/** The bubble shell shared by chats and rooms; [content] sits in the body (the tail gutter is padding). */
@Composable
fun BubbleShell(mine: Boolean, tail: Boolean, modifier: Modifier = Modifier, padding: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = chatColors()
    val fill = if (mine) colors.out else colors.incoming
    Box(
        modifier
            .drawBehind {
                // The tail sits on the outer side: the end for mine, the start for theirs (mirrored in RTL).
                val tailRight = mine == (layoutDirection == LayoutDirection.Ltr)
                val path = bubblePath(size.width, size.height, tailRight, tail, this)
                translate(top = 1.dp.toPx()) { drawPath(path, colors.shadow) } // the soft 1 dp shadow under the edge
                drawPath(path, fill)
            }
            .padding(start = if (mine) 0.dp else TailWidth, end = if (mine) TailWidth else 0.dp)
            .then(padding),
    ) { content() }
}

/** "10:42" or "10:42 am", following the phone's 12 / 24 hour setting. */
@Composable
private fun clock(at: Long): String {
    val context = LocalContext.current
    return remember(at) { DateFormat.getTimeFormat(context).format(Date(at)) }
}

/** Time, and on my messages one grey tick (sent) or two blue ticks (read). */
@Composable
fun BubbleMeta(at: Long, mine: Boolean, read: Boolean, color: Color = chatColors().meta, sentTick: Color = color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(clock(at), color = color, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = Jakarta)
        if (mine) {
            if (read) Icon(Icons.Rounded.DoneAll, stringResource(R.string.bubble_read), Modifier.size(16.dp), tint = chatColors().tick)
            else Icon(Icons.Rounded.Check, stringResource(R.string.bubble_sent), Modifier.size(16.dp), tint = sentTick)
        }
    }
}

/**
 * The message text with [meta] in the bottom-right corner: on the last line when it fits there, else on a line of
 * its own (measured, so the time never covers the text).
 */
@Composable
fun TextWithMeta(text: String, color: Color, meta: @Composable () -> Unit, modifier: Modifier = Modifier) =
    TextWithMeta(AnnotatedString(text), color, meta, modifier)

/** The same, for text with styled parts (a room line's @mentions). */
@Composable
fun TextWithMeta(text: AnnotatedString, color: Color, meta: @Composable () -> Unit, modifier: Modifier = Modifier) {
    // Written by the Text while it is measured, read right after in the same pass.
    val textLayout = remember { arrayOfNulls<TextLayoutResult>(1) }
    Layout(
        content = {
            Text(
                text, color = color, onTextLayout = { textLayout[0] = it },
                style = TextStyle(fontFamily = Jakarta, fontSize = 15.sp, lineHeight = 20.sp),
            )
            meta()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val textP = measurables[0].measure(loose)
        val metaP = measurables[1].measure(loose)
        val gap = 8.dp.roundToPx()
        val l = textLayout[0]
        val last = l?.let { it.lineCount - 1 } ?: 0
        val lastWidth = l?.let { (it.getLineRight(last) - it.getLineLeft(last)).toInt() } ?: textP.width
        val sameLine = lastWidth + gap + metaP.width <= constraints.maxWidth
        val width = if (sameLine) maxOf(textP.width, lastWidth + gap + metaP.width) else maxOf(textP.width, metaP.width)
        val height = if (sameLine) maxOf(textP.height, metaP.height) else textP.height + metaP.height
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            // Relative: in a right-to-left layout the text starts on the right and the time sits bottom-left.
            textP.placeRelative(0, 0)
            // On the last line it sits a touch low, like the messenger: its bottom on the text's bottom edge.
            metaP.placeRelative(width - metaP.width, height - metaP.height + if (sameLine) 2.dp.roundToPx() else 0)
        }
    }
}

/**
 * One chat message. [tail]: first of a run (the sender differs from the previous message). [read]: mine, and the
 * partner has seen it. Media from the other person stays hidden until tapped, so nobody sees an unwanted photo by
 * surprise; without a caption the time sits over the media on a dark pill. [header]: the sender's name (room chat);
 * [image]: draws the photo itself instead of loading [Message.media] (the demo's drawn snap).
 */
@Composable
fun Bubble(
    m: Message, tail: Boolean = true, read: Boolean = false, header: (@Composable () -> Unit)? = null, image: (@Composable () -> Unit)? = null,
    translation: LineTranslation? = null,
) {
    val mine = m.fromMe
    val media = m.media
    val colors = chatColors()
    var revealed by rememberSaveable(m.id) { mutableStateOf(mine) }
    val context = LocalContext.current
    val original = m.text?.takeIf { it.isNotEmpty() }
    val text = if (original != null) translation?.view?.text ?: original else null
    BubbleShell(
        mine, tail,
        padding = if (media != null) Modifier.padding(4.dp) else Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Column {
            header?.invoke()
            if (media != null) {
                Box(Modifier.clip(RoundedCornerShape(6.dp))) {
                    when {
                        !revealed -> Column(
                            Modifier.size(176.dp, 220.dp)
                                // Nothing of the photo is loaded until it is tapped: a flat neutral panel stands in for it.
                                .background(Color(0xFF2E2E34))
                                .clickable(role = Role.Button) { revealed = true },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Text(
                                stringResource(R.string.tap_to_view), color = Color.White,
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                stringResource(if (media.kind == MediaKind.VIDEO) R.string.video else R.string.photo),
                                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        media.kind == MediaKind.IMAGE && image != null -> image()
                        media.kind == MediaKind.IMAGE -> AsyncImage(
                            media.uri, contentDescription = stringResource(R.string.photo), contentScale = ContentScale.Crop,
                            modifier = Modifier.sizeIn(minWidth = 120.dp, minHeight = 80.dp, maxWidth = 240.dp, maxHeight = 300.dp),
                        )
                        else -> Box(
                            Modifier.size(220.dp, 140.dp).background(Color.Black).clickable { playVideo(context, media.uri) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.video), tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                    if (text == null) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 1.dp),
                        ) { BubbleMeta(m.at, mine, read, color = Color.White) }
                    }
                }
            }
            if (text != null) {
                // A link's card on top (as messengers do), the text with its links tappable under it.
                MessageLinkCard(original!!, Modifier.padding(bottom = 4.dp))
                translation?.let { TranslationNote(it, Modifier.padding(bottom = 2.dp)) }
                TextWithMeta(
                    rememberLinked(AnnotatedString(text), colors.link), colors.text, meta = { BubbleMeta(m.at, mine, read) },
                    modifier = if (media != null) Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 1.dp) else Modifier,
                )
            }
        }
    }
}

/** "partner is typing": three dots in an incoming bubble. */
@Composable
fun TypingBubble() {
    BubbleShell(mine = false, tail = true, padding = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
        TypingDots(chatColors().meta)
    }
}

/** A small centred note on the wallpaper ("You matched with…", "… left the chat"). */
@Composable
fun ChatChip(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    val colors = chatColors()
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text, color = colors.meta, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            modifier = Modifier
                .drawBehind {
                    val r = CornerRadius(8.dp.toPx())
                    translate(top = 1.dp.toPx()) { drawRoundRect(colors.shadow, cornerRadius = r) }
                    drawRoundRect(colors.incoming, cornerRadius = r)
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Above a translated line: "Translated · Show original" (tapping it shows the original), or "Translating…" while a
 * translation I asked for is on its way. Nothing for a line shown as written.
 */
@Composable
fun TranslationNote(t: LineTranslation, modifier: Modifier = Modifier) {
    val color = chatColors().meta
    val style = TextStyle(fontFamily = Jakarta, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    when (t.view.state) {
        LineView.State.TRANSLATED -> Row(
            modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = t.showOriginal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Translate, null, Modifier.padding(end = 4.dp).size(14.dp), tint = color)
            Text(stringResource(R.string.translated_show_original), color = color, style = style)
        }
        LineView.State.LOADING -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Translate, null, Modifier.padding(end = 4.dp).size(14.dp), tint = color)
            Text(stringResource(R.string.translating), color = color, style = style)
        }
        LineView.State.ORIGINAL -> Unit
    }
}

package com.yaari.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceReaction

// Yaari's own glyphs for room topics and reactions (no emoji anywhere in the UI chrome: they render differently on
// every phone). A port of the web's components/ui/topic-icons.tsx with the same 24×24 paths: solid rounded shapes
// in two tones, the main colour (M) and a light tint of it (L, 42 % colour on white). Topics are grey, the rest saffron.

/** A room's glyph: talk or music. */
enum class Topic { CHAT, MUSIC }

/** A room's glyph and colour: talk is the chat bubble, music the note (as the web's kindTopic). */
fun RoomCategory.topic(): Topic = when (this) {
    RoomCategory.TALK -> Topic.CHAT
    RoomCategory.MUSIC -> Topic.MUSIC
}

/** Reaction glyphs (and the raised hand, the icebreaker card): saffron, but the heart is red. */
enum class Glyph(val color: Long = 0xFFFF8A00) {
    HEART(0xFFF2416B), LAUGH, FIRE, CLAP, WOW, HAND, ICEBREAKER,
    /** A room poll: answer bars. */
    POLL,
    /** A web link: two chain links (link cards, the page shared in a room). */
    LINK,
}

fun VoiceReaction.glyph(): Glyph = when (this) {
    VoiceReaction.HEART -> Glyph.HEART
    VoiceReaction.LAUGH -> Glyph.LAUGH
    VoiceReaction.FIRE -> Glyph.FIRE
    VoiceReaction.CLAP -> Glyph.CLAP
    VoiceReaction.WOW -> Glyph.WOW
}


// ---- the drawings ------------------------------------------------------------------------------

private enum class Ink { M, L, FACE, WHITE, BLACK }

private sealed interface Part
private class Fill(val d: String, val ink: Ink) : Part
private class Line(val d: String, val ink: Ink, val width: Float, val roundJoin: Boolean = false) : Part
/** SVG rotate(deg cx cy) and translate(tx ty), applied to [parts]. */
private class Turn(val parts: List<Part>, val deg: Float = 0f, val cx: Float = 0f, val cy: Float = 0f, val tx: Float = 0f, val ty: Float = 0f) : Part

private fun f(n: Float) = "%.4f".format(java.util.Locale.ROOT, n)
private fun circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)
private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
    "M${f(cx - rx)},${f(cy)}a${f(rx)},${f(ry)} 0 1,0 ${f(2 * rx)},0a${f(rx)},${f(ry)} 0 1,0 ${f(-2 * rx)},0z"
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${f(x + r)},${f(y)}h${f(w - 2 * r)}a${f(r)},${f(r)} 0 0,1 ${f(r)},${f(r)}v${f(h - 2 * r)}a${f(r)},${f(r)} 0 0,1 ${f(-r)},${f(r)}" +
        "h${f(-(w - 2 * r))}a${f(r)},${f(r)} 0 0,1 ${f(-r)},${f(-r)}v${f(-(h - 2 * r))}a${f(r)},${f(r)} 0 0,1 ${f(r)},${f(-r)}z"

private const val HAND =
    "M7.5 9.6V5.1a1.75 1.75 0 0 1 3.5 0V3.9a1.75 1.75 0 0 1 3.5 0v1.3a1.75 1.75 0 0 1 3.5 0V15a6 6 0 0 1-6 6h-.6a6 6 0 0 1-4.75-2.33L3.4 14.6a1.6 1.6 0 0 1 2.42-2.1L7.5 14z"

private val LAUGH = listOf(
    Fill(circle(12f, 12f, 9.5f), Ink.M),
    Line("M6.4 9.6c.6-1.1 2.4-1.1 3 0M14.6 9.6c.6-1.1 2.4-1.1 3 0", Ink.FACE, 1.8f),
    Fill("M6.9 12.6h10.2a5.1 5.1 0 0 1-10.2 0z", Ink.FACE),
    Fill("M9.3 16.2a4 4 0 0 1 5.4 0 5.1 5.1 0 0 1-5.4 0z", Ink.L),
)

private val TopicParts: Map<Topic, List<Part>> = mapOf(
    Topic.CHAT to listOf(
        Fill("M2.5 6.5A3.5 3.5 0 0 1 6 3h12a3.5 3.5 0 0 1 3.5 3.5v7A3.5 3.5 0 0 1 18 17h-6.2l-4.3 3.4c-.66.52-1.62.05-1.62-.79V17H6a3.5 3.5 0 0 1-3.5-3.5z", Ink.M),
        Fill(circle(7.8f, 10f, 1.45f), Ink.L), Fill(circle(12f, 10f, 1.45f), Ink.L), Fill(circle(16.2f, 10f, 1.45f), Ink.L),
    ),
    Topic.MUSIC to listOf(
        Fill("M8.6 6.6 20.6 3.4v4.1L8.6 10.7z", Ink.L),
        Fill(rect(8.6f, 6.2f, 2.1f, 11.6f, 1.05f), Ink.M),
        Fill(rect(18.5f, 3.6f, 2.1f, 11.6f, 1.05f), Ink.M),
        Turn(listOf(Fill(ellipse(6.9f, 17.7f, 3.4f, 2.7f), Ink.M)), -18f, 6.9f, 17.7f),
        Turn(listOf(Fill(ellipse(16.8f, 15.1f, 3.4f, 2.7f), Ink.M)), -18f, 16.8f, 15.1f),
    ),
)

private val GlyphParts: Map<Glyph, List<Part>> = mapOf(
    // Three answers, each a light track with its share in the full colour.
    Glyph.POLL to listOf(
        Fill(rect(2.5f, 3.6f, 19f, 4.4f, 2.2f), Ink.L), Fill(rect(2.5f, 3.6f, 13.5f, 4.4f, 2.2f), Ink.M),
        Fill(rect(2.5f, 9.8f, 19f, 4.4f, 2.2f), Ink.L), Fill(rect(2.5f, 9.8f, 7.6f, 4.4f, 2.2f), Ink.M),
        Fill(rect(2.5f, 16f, 19f, 4.4f, 2.2f), Ink.L), Fill(rect(2.5f, 16f, 16.4f, 4.4f, 2.2f), Ink.M),
    ),
    // A speech bubble with a question mark.
    Glyph.ICEBREAKER to listOf(
        Fill("M2.5 6.5A3.5 3.5 0 0 1 6 3h12a3.5 3.5 0 0 1 3.5 3.5v7A3.5 3.5 0 0 1 18 17h-6.2l-4.3 3.4c-.66.52-1.62.05-1.62-.79V17H6a3.5 3.5 0 0 1-3.5-3.5z", Ink.M),
        Line("M9.7 7.9a2.35 2.35 0 1 1 3.2 2.2c-.55.25-.9.75-.9 1.35v.35", Ink.L, 1.9f),
        Fill(circle(12f, 14.3f, 1.15f), Ink.L),
    ),
    Glyph.HEART to listOf(
        Fill("M12 20.8c-.3 0-.6-.1-.85-.3C6.3 16.7 2.8 13.6 2.8 9.6c0-2.75 2.1-4.9 4.8-4.9 1.75 0 3.3.9 4.4 2.35C13.1 5.6 14.65 4.7 16.4 4.7c2.7 0 4.8 2.15 4.8 4.9 0 4-3.5 7.1-8.35 10.9-.25.2-.55.3-.85.3z", Ink.M),
        Turn(listOf(Fill(ellipse(7.4f, 8.9f, 1.9f, 1.25f), Ink.L)), -40f, 7.4f, 8.9f),
    ),
    Glyph.LAUGH to LAUGH,
    Glyph.FIRE to listOf(
        Fill("M12.6 2.4c.6 3.2-1 5-2.7 6.8C8.3 10.9 6 13 6 16c0 3.4 2.7 6 6 6s6-2.6 6-6c0-2.9-1.4-5-2.7-6.4-.3 1.4-1 2.4-2 2.9.5-3.7-.4-7.4-2.7-10.1z", Ink.M),
        Fill("M12 22c-2 0-3.4-1.4-3.4-3.3 0-1.8 1.2-3 2.4-4.1.4 1 1 1.6 1.8 1.9 0-1 .3-2 .9-2.8 1.2 1.2 2 2.7 2 4.6 0 2.1-1.6 3.7-3.7 3.7z", Ink.L),
    ),
    Glyph.CLAP to listOf(
        Turn(
            listOf(
                Turn(listOf(Fill(rect(2.6f, 11.6f, 2.6f, 6.2f, 1.3f), Ink.L)), -32f, 3.9f, 14.7f),
                Fill(rect(5.4f, 5.6f, 7.4f, 15f, 3.7f), Ink.L),
            ),
            18f, 9.4f, 13f,
        ),
        Turn(
            listOf(
                Turn(listOf(Fill(rect(18.8f, 11.6f, 2.6f, 6.2f, 1.3f), Ink.M)), 32f, 20.1f, 14.7f),
                Fill(rect(11.2f, 5.6f, 7.4f, 15f, 3.7f), Ink.M),
            ),
            -18f, 14.6f, 13f,
        ),
        Fill(rect(11.15f, 0.8f, 1.7f, 3.2f, 0.85f), Ink.M),
        Turn(listOf(Fill(rect(6.5f, 2.1f, 1.7f, 2.8f, 0.85f), Ink.M)), -35f, 7.35f, 3.5f),
        Turn(listOf(Fill(rect(15.8f, 2.1f, 1.7f, 2.8f, 0.85f), Ink.M)), 35f, 16.65f, 3.5f),
    ),
    Glyph.WOW to listOf(
        Fill("M10 3.4c.6 5.6 2.5 7.6 8.1 8.4-5.6.8-7.5 2.8-8.1 8.4-.6-5.6-2.5-7.6-8.1-8.4 5.6-.8 7.5-2.8 8.1-8.4z", Ink.M),
        Fill("M18.6 2.2c.28 1.75.95 2.5 2.7 2.8-1.75.3-2.42 1.05-2.7 2.8-.28-1.75-.95-2.5-2.7-2.8 1.75-.3 2.42-1.05 2.7-2.8z", Ink.L),
        Fill(circle(19.2f, 18.6f, 1.6f), Ink.L),
    ),
    // Two chain links at 45°: the near one in the full colour, the far one in the light tone.
    Glyph.LINK to listOf(
        Turn(
            listOf(
                Line(rect(10.6f, 8.4f, 11f, 7.2f, 3.6f), Ink.L, 2.5f, roundJoin = true),
                Line(rect(2.4f, 8.4f, 11f, 7.2f, 3.6f), Ink.M, 2.5f, roundJoin = true),
            ),
            -45f, 12f, 12f,
        ),
    ),
    Glyph.HAND to listOf(
        Turn(listOf(Fill(HAND, Ink.M)), tx = 1.6f),
        Turn(listOf(Fill(rect(1.4f, 4.2f, 1.8f, 4f, 0.9f), Ink.L)), -28f, 2.3f, 6.2f),
        Turn(listOf(Fill(rect(20.9f, 4.2f, 1.8f, 4f, 0.9f), Ink.L)), 28f, 21.8f, 6.2f),
    ),
)

/** The light second tone: 42 % of the colour on white (CSS color-mix in sRGB). */
internal fun lightTone(c: Color) = lerp(Color.White, c, 0.42f)

/** Eyes and mouth on a face: 30 % of the colour on a dark brown. */
private fun faceInk(c: Color) = lerp(Color(0xFF3D2400), c, 0.30f)

private fun build(name: String, parts: List<Part>, color: Color): ImageVector {
    val inks = mapOf(Ink.M to color, Ink.L to lightTone(color), Ink.FACE to faceInk(color), Ink.WHITE to Color.White, Ink.BLACK to Brand.Black)
    fun ImageVector.Builder.add(list: List<Part>) {
        for (p in list) when (p) {
            is Fill -> addPath(addPathNodes(p.d), fill = SolidColor(inks.getValue(p.ink)))
            is Line -> addPath(
                addPathNodes(p.d), stroke = SolidColor(inks.getValue(p.ink)), strokeLineWidth = p.width, strokeLineCap = StrokeCap.Round,
                strokeLineJoin = if (p.roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
            )
            is Turn -> group(rotate = p.deg, pivotX = p.cx, pivotY = p.cy, translationX = p.tx, translationY = p.ty) { add(p.parts) }
        }
    }
    return ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply { add(parts) }.build()
}

@Composable
fun TopicIcon(topic: Topic, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val vector = remember(topic, color) { build("topic_${topic.name}", TopicParts.getValue(topic), color) }
    Image(rememberVectorPainter(vector), null, modifier.size(size))
}

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val vector = remember(glyph) { build("glyph_${glyph.name}", GlyphParts.getValue(glyph), Color(glyph.color)) }
    Image(rememberVectorPainter(vector), null, modifier.size(size))
}

/** A rounded chip with the grey glyph and its label, outlined (Naver Map style category chips). */
@Composable
fun TopicChip(topic: Topic, label: String, modifier: Modifier = Modifier, height: Dp = 28.dp) {
    Row(
        modifier.height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TopicIcon(topic, size = 16.dp)
        Text(
            label, style = MaterialTheme.typography.labelMedium.copy(fontSize = if (height < 28.dp) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- the app's own marks for posts and profiles: an outline (off) and a two-tone fill (on), in any colour -------------

/** Chrome marks drawn like the topic glyphs (24×24, two tones), coloured by the caller (nav colour, text colour). */
enum class Mark {
    HEART, COMMENT, GLOBE, FRIENDS, PALETTE, SEND,
    // Identity and manners (035): the verified seal, the check's steps, the rating faces and the status marks.
    VERIFIED, ID_CARD, SELFIE, LOCK, SHIELD, SMILE, FROWN, CLOCK, ALERT,
}

/** A rosette: a disc with eight scallops around it (the verified seal). */
private fun seal(): String {
    val sb = StringBuilder(circle(12f, 12f, 7.4f))
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0 + 22.5)
        sb.append(circle(12f + 6.9f * Math.cos(a).toFloat(), 12f + 6.9f * Math.sin(a).toFloat(), 3.3f))
    }
    return sb.toString()
}
private val SEAL_PATH = seal()
private const val SHIELD_PATH =
    "M12 2.4l7.5 2.8c.6.2 1 .8 1 1.4v4.7c0 4.9-3.3 8.7-8.1 10.2-.3.1-.6.1-.8 0C6.8 20 3.5 16.2 3.5 11.3V6.6c0-.6.4-1.2 1-1.4z"

private const val HEART_PATH =
    "M12 20.8c-.3 0-.6-.1-.85-.3C6.3 16.7 2.8 13.6 2.8 9.6c0-2.75 2.1-4.9 4.8-4.9 1.75 0 3.3.9 4.4 2.35C13.1 5.6 14.65 4.7 16.4 4.7c2.7 0 4.8 2.15 4.8 4.9 0 4-3.5 7.1-8.35 10.9-.25.2-.55.3-.85.3z"
private const val BUBBLE_PATH =
    "M12 3.4c4.9 0 8.6 3.4 8.6 7.7s-3.7 7.7-8.6 7.7c-1.1 0-2.2-.2-3.2-.5l-3.9 1.6c-.7.3-1.4-.4-1.1-1.1l1.1-2.9C3.9 14.5 3.4 12.9 3.4 11.1c0-4.3 3.7-7.7 8.6-7.7z"
private const val GLOBE_LINES = "M2.9 12h18.2M12 2.6c2.5 2.6 3.8 5.7 3.8 9.4s-1.3 6.8-3.8 9.4M12 2.6C9.5 5.2 8.2 8.3 8.2 12s1.3 6.8 3.8 9.4"
private const val PALETTE_PATH =
    "M12 2.8c-5.2 0-9.2 3.9-9.2 8.9 0 4.9 4 8.9 8.9 8.9 1.3 0 2-.8 2-1.8 0-.5-.2-.9-.5-1.3-.3-.3-.5-.8-.5-1.2 0-1 .8-1.8 1.8-1.8h2.1c2.8 0 5-2.2 5-5 0-4.3-4.1-7.7-9.6-7.7z"
private const val PLANE_PATH = "M3.4 11.1 19.6 3.6c.9-.4 1.8.5 1.4 1.4l-7.5 16.2c-.4.9-1.7.8-2-.1l-1.7-5.1-5.1-1.7c-.9-.3-1-1.6-.1-2z"

private val MarkFilled: Map<Mark, List<Part>> = mapOf(
    Mark.HEART to listOf(
        Fill(HEART_PATH, Ink.M),
        Turn(listOf(Fill(ellipse(7.4f, 8.9f, 1.9f, 1.25f), Ink.L)), -40f, 7.4f, 8.9f),
    ),
    Mark.COMMENT to listOf(
        Fill(BUBBLE_PATH, Ink.M),
        Fill(circle(8.2f, 11.1f, 1.25f), Ink.L), Fill(circle(12f, 11.1f, 1.25f), Ink.L), Fill(circle(15.8f, 11.1f, 1.25f), Ink.L),
    ),
    Mark.GLOBE to listOf(Fill(circle(12f, 12f, 9.4f), Ink.M), Line(GLOBE_LINES, Ink.L, 1.5f)),
    // Two people, the friend behind in the light tone.
    Mark.FRIENDS to listOf(
        Fill(circle(16.4f, 8.4f, 3f), Ink.L),
        Fill("M16.2 13.2c3 0 5.3 2.3 5.3 5.4 0 .7-.5 1.1-1.1 1.1h-3.6c.1-.4.2-.9.2-1.4 0-2-.8-3.8-2.2-5 .4-.07.9-.1 1.4-.1z", Ink.L),
        Fill(circle(9f, 7.9f, 3.6f), Ink.M),
        Fill("M2.8 19.2c0-3.5 2.8-6 6.2-6s6.2 2.5 6.2 6c0 .7-.5 1.2-1.2 1.2H4c-.7 0-1.2-.5-1.2-1.2z", Ink.M),
    ),
    Mark.PALETTE to listOf(
        Fill(PALETTE_PATH, Ink.M),
        Fill(circle(7.4f, 11.4f, 1.5f), Ink.L), Fill(circle(9.6f, 7.3f, 1.5f), Ink.L),
        Fill(circle(14.4f, 7.1f, 1.5f), Ink.L), Fill(circle(17.2f, 10.6f, 1.5f), Ink.L),
    ),
    Mark.SEND to listOf(Fill(PLANE_PATH, Ink.M), Line("M10.3 13.7 15 9", Ink.L, 1.6f)),
    // The flat seal with a black tick (8:1 on saffron).
    Mark.VERIFIED to listOf(
        Fill(SEAL_PATH, Ink.M),
        Line("M8.1 12.3l2.7 2.7 5.1-5.5", Ink.BLACK, 2.3f, roundJoin = true),
    ),
    // An ID card: the photo and three lines of text.
    Mark.ID_CARD to listOf(
        Fill(rect(2.2f, 4.6f, 19.6f, 14.8f, 3.2f), Ink.M),
        Fill(rect(4.8f, 8.1f, 6.2f, 7.6f, 1.8f), Ink.L),
        Fill(circle(7.9f, 10.6f, 1.4f), Ink.M),
        Fill("M5.6 15.1c.3-1.5 1.2-2.3 2.3-2.3s2 .8 2.3 2.3z", Ink.M),
        Fill(rect(12.8f, 8.6f, 6.6f, 1.8f, 0.9f), Ink.L),
        Fill(rect(12.8f, 11.6f, 4.6f, 1.8f, 0.9f), Ink.L),
        Fill(rect(12.8f, 14.6f, 5.6f, 1.8f, 0.9f), Ink.L),
    ),
    // A face in a camera's corner brackets: the liveness selfie.
    Mark.SELFIE to listOf(
        Line("M3.4 8.2V6.1a2.7 2.7 0 0 1 2.7-2.7h2.1M15.8 3.4h2.1a2.7 2.7 0 0 1 2.7 2.7v2.1M20.6 15.8v2.1a2.7 2.7 0 0 1-2.7 2.7h-2.1M8.2 20.6H6.1a2.7 2.7 0 0 1-2.7-2.7v-2.1", Ink.M, 2f, roundJoin = true),
        Fill(circle(12f, 12f, 5.4f), Ink.L),
        Fill(circle(10.1f, 11f, 0.85f), Ink.M), Fill(circle(13.9f, 11f, 0.85f), Ink.M),
        Line("M9.9 13.5c1.2 1.1 3 1.1 4.2 0", Ink.M, 1.5f),
    ),
    Mark.LOCK to listOf(
        Line("M8 10.6V8.1a4 4 0 0 1 8 0v2.5", Ink.L, 2.2f),
        Fill(rect(4.6f, 10f, 14.8f, 11f, 3.2f), Ink.M),
        Fill(circle(12f, 14.6f, 1.7f), Ink.L),
        Fill(rect(11.25f, 15f, 1.5f, 3.2f, 0.75f), Ink.L),
    ),
    Mark.SHIELD to listOf(
        Fill(SHIELD_PATH, Ink.M),
        Turn(listOf(Fill(ellipse(8.3f, 6.9f, 2.3f, 1.1f), Ink.L)), -22f, 8.3f, 6.9f),
        Line("M8.4 11.9l2.5 2.5 4.8-5", Ink.WHITE, 2.1f, roundJoin = true),
    ),
    Mark.SMILE to listOf(
        Fill(circle(12f, 12f, 9.4f), Ink.M),
        Turn(listOf(Fill(ellipse(7.6f, 6.9f, 2.2f, 1.2f), Ink.L)), -35f, 7.6f, 6.9f),
        Fill(circle(8.9f, 10.2f, 1.3f), Ink.FACE), Fill(circle(15.1f, 10.2f, 1.3f), Ink.FACE),
        Line("M7.9 13.8c2.3 2.8 5.9 2.8 8.2 0", Ink.FACE, 1.9f),
    ),
    Mark.FROWN to listOf(
        Fill(circle(12f, 12f, 9.4f), Ink.M),
        Turn(listOf(Fill(ellipse(7.6f, 6.9f, 2.2f, 1.2f), Ink.L)), -35f, 7.6f, 6.9f),
        Fill(circle(8.9f, 10.2f, 1.3f), Ink.FACE), Fill(circle(15.1f, 10.2f, 1.3f), Ink.FACE),
        Line("M8.4 16.8c2-2.3 5.2-2.3 7.2 0", Ink.FACE, 1.9f),
    ),
    Mark.CLOCK to listOf(
        Fill(circle(12f, 12f, 9.4f), Ink.M),
        Fill(circle(12f, 12f, 6.6f), Ink.L),
        Line("M12 8v4.4l3 1.8", Ink.M, 2f, roundJoin = true),
    ),
    Mark.ALERT to listOf(
        Fill(circle(12f, 12f, 9.4f), Ink.M),
        Turn(listOf(Fill(ellipse(7.6f, 6.9f, 2.2f, 1.2f), Ink.L)), -35f, 7.6f, 6.9f),
        Fill(rect(10.85f, 6.4f, 2.3f, 7.8f, 1.15f), Ink.WHITE),
        Fill(circle(12f, 17.2f, 1.35f), Ink.WHITE),
    ),
)

private val MarkOutline: Map<Mark, List<Part>> = mapOf(
    Mark.HEART to listOf(Line(HEART_PATH, Ink.M, 1.9f, roundJoin = true)),
    Mark.COMMENT to listOf(Line(BUBBLE_PATH, Ink.M, 1.9f, roundJoin = true)),
    Mark.GLOBE to listOf(Line(circle(12f, 12f, 9.1f), Ink.M, 1.7f), Line(GLOBE_LINES, Ink.M, 1.5f)),
)

/** A [Mark] in [color]: [filled] is the two-tone drawing (selected, on), otherwise its outline (where it has one). */
@Composable
fun MarkIcon(mark: Mark, color: Color, modifier: Modifier = Modifier, filled: Boolean = true, size: Dp = 24.dp) {
    val parts = if (filled) MarkFilled.getValue(mark) else MarkOutline[mark] ?: MarkFilled.getValue(mark)
    val vector = remember(mark, color, filled) { build("mark_${mark.name}_$filled", parts, color) }
    Image(rememberVectorPainter(vector), null, modifier.size(size))
}

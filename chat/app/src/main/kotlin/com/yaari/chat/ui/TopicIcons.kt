package com.yaari.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.model.Room
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.VoiceReaction

// Yaari's own glyphs for room topics and reactions (no emoji anywhere in the UI chrome: they render differently on
// every phone). A port of the web's components/ui/topic-icons.tsx with the same 24×24 paths: solid rounded shapes
// in two tones, the topic colour (M) and a light tint of it (L, 42 % colour on white). Colours are lighter at night.

/** A room's glyph: the ten categories, plus the built-in rooms that have none of their own. */
enum class Topic { CHAT, GLOBE, FRIENDS, LANGUAGE, MUSIC, MOVIES, GAMING, SPORTS, STUDY, TRAVEL, NIGHT, MEMES }

fun RoomCategory.topic(): Topic = when (this) {
    RoomCategory.CHAT -> Topic.CHAT
    RoomCategory.FRIENDS -> Topic.FRIENDS
    RoomCategory.LANGUAGE -> Topic.LANGUAGE
    RoomCategory.MUSIC -> Topic.MUSIC
    RoomCategory.MOVIES -> Topic.MOVIES
    RoomCategory.GAMING -> Topic.GAMING
    RoomCategory.SPORTS -> Topic.SPORTS
    RoomCategory.STUDY -> Topic.STUDY
    RoomCategory.TRAVEL -> Topic.TRAVEL
    RoomCategory.NIGHT -> Topic.NIGHT
}

private val OfficialTopics = mapOf(
    "lounge" to Topic.GLOBE, "language" to Topic.LANGUAGE, "music" to Topic.MUSIC, "movies" to Topic.MOVIES,
    "gaming" to Topic.GAMING, "sports" to Topic.SPORTS, "night" to Topic.NIGHT, "memes" to Topic.MEMES,
    "study" to Topic.STUDY, "travel" to Topic.TRAVEL,
)

/** Which glyph a chat room shows (the server's emoji is not used in the app). */
fun Room.topic(): Topic = if (official) OfficialTopics[id] ?: Topic.CHAT else category.topic()

// The web's --topic-* colours (app/globals.css), day and night.
private val TopicDay = mapOf(
    Topic.CHAT to 0xFF4C7CF0, Topic.GLOBE to 0xFF4C7CF0, Topic.FRIENDS to 0xFFF2994A, Topic.LANGUAGE to 0xFF22A06B,
    Topic.MUSIC to 0xFFE2558A, Topic.MOVIES to 0xFF7B61FF, Topic.GAMING to 0xFF0FA3B1, Topic.SPORTS to 0xFFF5A623,
    Topic.STUDY to 0xFF8E5CF7, Topic.TRAVEL to 0xFF0EA5E9, Topic.NIGHT to 0xFF5B6CFF, Topic.MEMES to 0xFFF7B500,
)
private val TopicNight = mapOf(
    Topic.CHAT to 0xFF6E97FF, Topic.GLOBE to 0xFF6E97FF, Topic.FRIENDS to 0xFFF6AE6B, Topic.LANGUAGE to 0xFF3DBE86,
    Topic.MUSIC to 0xFFEE77A4, Topic.MOVIES to 0xFF9683FF, Topic.GAMING to 0xFF2EC0CC, Topic.SPORTS to 0xFFF8B94A,
    Topic.STUDY to 0xFFA47CFA, Topic.TRAVEL to 0xFF38BDF8, Topic.NIGHT to 0xFF7D8BFF, Topic.MEMES to 0xFFF9C43A,
)

/** Reaction glyphs (and the raised hand) have one colour in both themes. */
enum class Glyph(val color: Long) { HEART(0xFFF2416B), LAUGH(0xFFF7B500), FIRE(0xFFFF6A2B), CLAP(0xFFF59E0B), WOW(0xFF8E5CF7), HAND(0xFFF59E0B) }

fun VoiceReaction.glyph(): Glyph = when (this) {
    VoiceReaction.HEART -> Glyph.HEART
    VoiceReaction.LAUGH -> Glyph.LAUGH
    VoiceReaction.FIRE -> Glyph.FIRE
    VoiceReaction.CLAP -> Glyph.CLAP
    VoiceReaction.WOW -> Glyph.WOW
}

@Composable
private fun isNight() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The topic's colour, for tinted tiles and chips. */
@Composable
fun topicColor(topic: Topic): Color = Color((if (isNight()) TopicNight else TopicDay).getValue(topic))

// ---- the drawings ------------------------------------------------------------------------------

private enum class Ink { M, L, FACE, WHITE }

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
    Topic.GLOBE to listOf(
        Fill(circle(12f, 12f, 9.5f), Ink.M),
        Line("M2.9 12h18.2M12 2.6c2.5 2.6 3.8 5.7 3.8 9.4s-1.3 6.8-3.8 9.4c-2.5-2.6-3.8-5.7-3.8-9.4S9.5 5.2 12 2.6zM4.6 7.2h14.8M4.6 16.8h14.8", Ink.L, 1.6f),
    ),
    Topic.FRIENDS to listOf(
        Fill(circle(16.4f, 7.4f, 2.9f), Ink.L),
        Fill("M11 18.6a5.4 5.4 0 0 1 10.8 0c0 .55-.45 1-1 1h-8.8c-.55 0-1-.45-1-1z", Ink.L),
        Fill(circle(8.6f, 8.2f, 3.4f), Ink.M),
        Fill("M2.2 19.4a6.4 6.4 0 0 1 12.8 0c0 .6-.5 1.1-1.1 1.1H3.3c-.6 0-1.1-.5-1.1-1.1z", Ink.M),
    ),
    Topic.LANGUAGE to listOf(
        Fill("M10 5.8A3.3 3.3 0 0 1 13.3 2.5h5.4A3.3 3.3 0 0 1 22 5.8v3.4a3.3 3.3 0 0 1-2 3.03v1.86c0 .5-.6.77-.98.44L16.6 12.5h-3.3A3.3 3.3 0 0 1 10 9.2z", Ink.L),
        Fill("M2 11.3A3.3 3.3 0 0 1 5.3 8h7.4a3.3 3.3 0 0 1 3.3 3.3v4.4a3.3 3.3 0 0 1-3.3 3.3H8.6l-3.62 2.88c-.38.3-.98.03-.98-.46v-2.73a3.3 3.3 0 0 1-2-3.03z", Ink.M),
        Line("M6.1 16.6 9 10.8l2.9 5.8M7.15 14.6h3.7", Ink.WHITE, 1.7f, roundJoin = true),
    ),
    Topic.MUSIC to listOf(
        Fill("M8.6 6.6 20.6 3.4v4.1L8.6 10.7z", Ink.L),
        Fill(rect(8.6f, 6.2f, 2.1f, 11.6f, 1.05f), Ink.M),
        Fill(rect(18.5f, 3.6f, 2.1f, 11.6f, 1.05f), Ink.M),
        Turn(listOf(Fill(ellipse(6.9f, 17.7f, 3.4f, 2.7f), Ink.M)), -18f, 6.9f, 17.7f),
        Turn(listOf(Fill(ellipse(16.8f, 15.1f, 3.4f, 2.7f), Ink.M)), -18f, 16.8f, 15.1f),
    ),
    Topic.MOVIES to listOf(
        Fill(rect(2.5f, 10f, 19f, 11f, 2.6f), Ink.M),
        Turn(
            listOf(
                Fill(rect(2.6f, 4.9f, 19f, 4.3f, 1.4f), Ink.L),
                Fill("M7.2 4.9h2.9l-2.2 4.3H5zM13.3 4.9h2.9L14 9.2h-2.9z", Ink.M),
            ),
            -13f, 3f, 9.6f,
        ),
        Fill("M10.3 13.2v4.6c0 .45.5.72.87.47l3.4-2.3a.57.57 0 0 0 0-.94l-3.4-2.3a.56.56 0 0 0-.87.47z", Ink.L),
    ),
    Topic.GAMING to listOf(
        Fill("M7.2 6h9.6c2.6 0 4.6 1.9 5 4.5l.85 5.2c.36 2.3-1.36 4.3-3.6 4.3-1.38 0-2.62-.8-3.2-2.05L15.2 16.6H8.8l-.65 1.35C7.57 19.2 6.33 20 4.95 20c-2.24 0-3.96-2-3.6-4.3l.85-5.2c.4-2.6 2.4-4.5 5-4.5z", Ink.M),
        Fill(rect(5.4f, 10.4f, 5.2f, 1.8f, 0.9f), Ink.L),
        Fill(rect(7.1f, 8.7f, 1.8f, 5.2f, 0.9f), Ink.L),
        Fill(circle(16.1f, 10f, 1.2f), Ink.L), Fill(circle(18.1f, 12.6f, 1.2f), Ink.L),
    ),
    Topic.SPORTS to listOf(
        Turn(
            listOf(
                Fill(rect(9f, 1.4f, 2f, 6.6f, 1f), Ink.L),
                Fill("M7.3 8.6c0-.6.45-1.05 1-1.05h3.4c.55 0 1 .45 1 1.05v11c0 1-.8 1.8-1.8 1.8H9.1c-1 0-1.8-.8-1.8-1.8z", Ink.M),
            ),
            40f, 10f, 12f,
        ),
        Fill(circle(18.4f, 18.2f, 3.3f), Ink.M),
        Line("M16.2 16a3.3 3.3 0 0 1 4.4 4.4", Ink.L, 1.3f),
    ),
    Topic.STUDY to listOf(
        Fill("M12 6.4C10 4.9 7.4 4.2 4.2 4.4c-.7.05-1.2.6-1.2 1.3v11.5c0 .75.63 1.33 1.38 1.3 2.9-.15 5.4.55 7.62 2.1z", Ink.M),
        Fill("M12 6.4c2-1.5 4.6-2.2 7.8-2 .7.05 1.2.6 1.2 1.3v11.5c0 .75-.63 1.33-1.38 1.3-2.9-.15-5.4.55-7.62 2.1z", Ink.L),
    ),
    Topic.TRAVEL to listOf(
        Turn(
            listOf(
                Fill("M10.6 9.4 2.4 14.2v2.3l8.2-2.3zM13.4 9.4l8.2 4.8v2.3l-8.2-2.3z", Ink.M),
                Fill("M10.6 17.4 7.3 19.6v1.7l3.3-.8zM13.4 17.4l3.3 2.2v1.7l-3.3-.8z", Ink.L),
                Fill(rect(10.4f, 1.8f, 3.2f, 19.6f, 1.6f), Ink.M),
            ),
            45f, 12f, 12f,
        ),
    ),
    Topic.NIGHT to listOf(
        Fill("M10.93 4.2A8.6 8.6 0 1 0 19.63 14.52 7.4 7.4 0 0 1 10.93 4.2z", Ink.M),
        Fill("M17.6 3.2c.3 1.9 1 2.7 2.9 3-1.9.3-2.6 1.1-2.9 3-.3-1.9-1-2.7-2.9-3 1.9-.3 2.6-1.1 2.9-3z", Ink.L),
    ),
    Topic.MEMES to LAUGH,
)

private val GlyphParts: Map<Glyph, List<Part>> = mapOf(
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
    val inks = mapOf(Ink.M to color, Ink.L to lightTone(color), Ink.FACE to faceInk(color), Ink.WHITE to Color.White)
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
    val color = topicColor(topic)
    val vector = remember(topic, color) { build("topic_${topic.name}", TopicParts.getValue(topic), color) }
    Image(rememberVectorPainter(vector), null, modifier.size(size))
}

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val vector = remember(glyph) { build("glyph_${glyph.name}", GlyphParts.getValue(glyph), Color(glyph.color)) }
    Image(rememberVectorPainter(vector), null, modifier.size(size))
}

/** The leading tile of a room: the glyph in full colour on a light wash of it (never a loud colour block). */
@Composable
fun TopicTile(topic: Topic, modifier: Modifier = Modifier, size: Dp = 44.dp, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    val wash = topicColor(topic).copy(alpha = if (isNight()) 0.18f else 0.12f)
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surface).background(wash), contentAlignment = Alignment.Center) {
        TopicIcon(topic, size = size * 0.52f)
    }
}

/** A white rounded chip with the glyph and its label (Naver Map style category chips). */
@Composable
fun TopicChip(topic: Topic, label: String, modifier: Modifier = Modifier, height: Dp = 28.dp) {
    val night = isNight()
    Row(
        modifier.height(height)
            .then(if (night) Modifier else Modifier.shadow(1.5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f)))
            .clip(CircleShape).background(if (night) Brand.Ink3 else Color.White).padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TopicIcon(topic, size = 16.dp)
        Text(
            label, style = MaterialTheme.typography.labelMedium.copy(fontSize = if (height < 28.dp) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

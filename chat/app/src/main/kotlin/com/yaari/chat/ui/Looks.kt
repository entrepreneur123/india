package com.yaari.chat.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.yaari.chat.R
import com.yaari.chat.model.Cosmetic
import com.yaari.chat.model.Look
import com.yaari.chat.model.Profile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// ---- Looks (040): the decorated @ID (colour, font, badge) and the profile frames. Every design is drawn here, the
// same shapes and colours as the web's SVGs (docs/LOOKS_SPEC.md); no image files. ----------------------------------

/** The app's [LooksController], so any @ID on screen shows its look (one batched looks() lookup for all of them). */
val LocalLooks = staticCompositionLocalOf<LooksController?> { null }

/** [userId]'s ID and look, asked for while shown; null until known, or for someone I may not see. */
@Composable
fun lookOf(userId: String): Look? {
    val c = LocalLooks.current ?: return null
    val people by c.people.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { c.want(listOf(userId)) }
    return people[userId]
}

// ---- the @ID ----------------------------------------------------------------------------------------------------

/** An ID colour: one colour per theme, or a horizontal gradient for both. */
private class IdColor(val light: Color, val dark: Color, val gradient: List<Color>? = null)

private fun gradient(vararg c: Long) = c.map(::Color).let { IdColor(it.first(), it.first(), it) }

private val ID_COLORS = mapOf(
    Cosmetic.COLOR_GOLD to IdColor(Color(0xFFA87400), Color(0xFFFFD54A)),
    Cosmetic.COLOR_ROSE to IdColor(Color(0xFFC2185B), Color(0xFFFF8AB3)),
    Cosmetic.COLOR_OCEAN to IdColor(Color(0xFF0277BD), Color(0xFF4FC3F7)),
    Cosmetic.COLOR_EMERALD to IdColor(Color(0xFF00796B), Color(0xFF4DD0A8)),
    Cosmetic.COLOR_VIOLET to IdColor(Color(0xFF6A1B9A), Color(0xFFCE93D8)),
    Cosmetic.COLOR_SUNSET to gradient(0xFFFF6A3D, 0xFFFF2E8B, 0xFFA63DFF),
    Cosmetic.COLOR_AURORA to gradient(0xFF00C9A7, 0xFF3D8BFF, 0xFFB44DFF),
    Cosmetic.COLOR_RAINBOW to gradient(0xFFFF4D4D, 0xFFFFA53D, 0xFFF5D300, 0xFF34C759, 0xFF3DB8FF, 0xFF9B5CFF),
)

/** The bundled ID fonts (Latin letters only; other scripts fall back to the system's). */
private val ID_FONTS = mapOf(
    Cosmetic.FONT_SCRIPT to FontFamily(Font(R.font.look_script)),
    Cosmetic.FONT_RETRO to FontFamily(Font(R.font.look_retro)),
    Cosmetic.FONT_BOLD to FontFamily(Font(R.font.look_bold)),
    Cosmetic.FONT_PIXEL to FontFamily(Font(R.font.look_pixel)),
    Cosmetic.FONT_ROUND to FontFamily(Font(R.font.look_round)),
    Cosmetic.FONT_FUTURE to FontFamily(Font(R.font.look_future)),
)

/** The rainbow slides across the text, one loop every 6 s; the colours repeat seamlessly. */
private class SlidingGradient(private val colors: List<Color>, private val phase: Float) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val w = size.width.coerceAtLeast(1f)
        return LinearGradientShader(Offset(-w * phase, 0f), Offset(w * (1f - phase), 0f), colors + colors.first(), tileMode = TileMode.Repeated)
    }

    override fun equals(other: Any?) = other is SlidingGradient && other.colors == colors && other.phase == phase
    override fun hashCode() = colors.hashCode() * 31 + phase.hashCode()
}

/**
 * "@handle" in someone's look: their colour and font, their badge after it (as tall as the text, 2 dp apart). People
 * without a look show a plain @handle in the secondary text colour. Until the batched lookup knows [userId] (or for
 * someone I may not see), [fallback] shows instead, if any.
 */
@Composable
fun LookName(userId: String, modifier: Modifier = Modifier, size: TextUnit = 14.sp, fallback: (@Composable () -> Unit)? = null) {
    val look = lookOf(userId)
    if (look == null || look.handle.isEmpty()) fallback?.invoke() else LookName(look, modifier, size)
}

/** [look]'s "@handle", as above. */
@Composable
fun LookName(look: Look, modifier: Modifier = Modifier, size: TextUnit = 14.sp) {
    val color = Cosmetic.of(look.color)?.let(ID_COLORS::get)
    val font = Cosmetic.of(look.font)
    val badge = Cosmetic.of(look.badge)
    // The pixel font is wide: it is drawn smaller to sit in the same space.
    val textSize = if (font == Cosmetic.FONT_PIXEL) size * 0.8f else size
    val base = TextStyle(
        fontFamily = font?.let(ID_FONTS::get) ?: Jakarta, fontSize = textSize, lineHeight = size * 1.3f,
        fontWeight = if (font == null) FontWeight.SemiBold else FontWeight.Normal,
    )
    val night = isNightTheme()
    val style = when {
        color == null -> base.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        look.color == Cosmetic.COLOR_RAINBOW.id -> {
            val phase by rememberLoop(RAINBOW_MS, still = 0f)
            base.copy(brush = SlidingGradient(color.gradient!!, phase))
        }
        color.gradient != null -> base.copy(brush = Brush.horizontalGradient(color.gradient))
        else -> base.copy(color = if (night) color.dark else color.light)
    }
    val text = "@" + look.handle
    val described = badge?.let { "$text, " + stringResource(it.label) } ?: text
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = described }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f, fill = false), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) {
            Spacer(Modifier.width(2.dp))
            BadgeMark(badge, Modifier.size(with(LocalDensity.current) { size.toDp() }))
        }
    }
}

private const val RAINBOW_MS = 6_000

// ---- badges (a 24 × 24 design, scaled to the size given) -----------------------------------------------------------

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val CROWN = path("M3 18L2 7.5 7.5 12 12 4.5 16.5 12 22 7.5 21 18Z")
private val CROWN_BAND = path("M3 18H21V21.2H3Z")
private val DIAMOND = path("M7 4H17L22 9.5 12 21.5 2 9.5Z")
private val DIAMOND_FACETS = path("M2 9.5H22M7 4 9.5 9.5 12 21.5M17 4 14.5 9.5 12 21.5M12 4 9.5 9.5M12 4 14.5 9.5")
private val FLAME = path("M12 2C13.6 6 19 9.4 19 15 19 19 15.9 22 12 22 8.1 22 5 19 5 15 5 11.5 7.4 9.1 8.5 6.5 9.5 8.5 10.5 9.5 11 10.6 11.6 7.6 12.3 5 12 2Z")
private val FLAME_CORE = path("M12 11.2C13.2 13.1 15.4 14.7 15.4 17.3 15.4 19.4 13.9 21 12 21 10.1 21 8.6 19.4 8.6 17.3 8.6 15 10.6 13.4 12 11.2Z")
private val HEART = path("M12 21C12 21 3 15.2 3 8.9 3 6.1 5.2 4 7.8 4 9.6 4 11.2 5 12 6.6 12.8 5 14.4 4 16.2 4 18.8 4 21 6.1 21 8.9 21 15.2 12 21 12 21Z")
private val BOLT = path("M13.5 2 4 13.5H11L9.5 22 20 9.5H13Z")

/** A five-point star around ([cx], [cy]). */
private fun star(cx: Float, cy: Float, outer: Float, inner: Float) = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val a = -PI / 2 + k * PI / 5
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val STAR = star(11f, 13.2f, 9.6f, 4.1f)

/** A four-point sparkle (concave sides) around ([cx], [cy]). */
private fun sparkle(cx: Float, cy: Float, r: Float) = Path().apply {
    moveTo(cx, cy - r)
    quadraticTo(cx, cy, cx + r, cy)
    quadraticTo(cx, cy, cx, cy + r)
    quadraticTo(cx, cy, cx - r, cy)
    quadraticTo(cx, cy, cx, cy - r)
    close()
}

private val STAR_SPARKLE = sparkle(19.8f, 4.2f, 3.4f)
private val DIAMOND_GLINT = sparkle(6.6f, 6.9f, 1.9f)

private fun vertical(top: Long, bottom: Long, from: Float = 2f, to: Float = 22f) = Brush.verticalGradient(listOf(Color(top), Color(bottom)), from, to)

/** A badge, drawn exactly as the web's (see the spec's table). */
@Composable
fun BadgeMark(badge: Cosmetic, modifier: Modifier = Modifier) {
    val name = stringResource(badge.label)
    Canvas(modifier.semantics { contentDescription = name }) {
        val u = size.minDimension / 24f
        // 1 px lines in the 24-unit design, never thinner than a device pixel.
        val line = maxOf(1f, u)
        translate((size.width - 24f * u) / 2, (size.height - 24f * u) / 2) {
            scale(u, u, Offset.Zero) { drawBadge(badge, line / u) }
        }
    }
}

private fun DrawScope.drawBadge(badge: Cosmetic, line: Float) {
    when (badge) {
        Cosmetic.BADGE_CROWN -> {
            val gold = vertical(0xFFFFE27A, 0xFFF2A900, 4.5f, 21.2f)
            val edge = Stroke(line, join = StrokeJoin.Round)
            drawPath(CROWN, gold)
            drawPath(CROWN, Color(0xFFB8860B), style = edge)
            drawPath(CROWN_BAND, gold)
            drawPath(CROWN_BAND, Color(0xFFB8860B), style = edge)
            drawCircle(Color(0xFF1E88E5), 1.8f, Offset(2.2f, 7.5f))
            drawCircle(Color(0xFFE53935), 2f, Offset(12f, 4.5f))
            drawCircle(Color(0xFF1E88E5), 1.8f, Offset(21.8f, 7.5f))
        }
        Cosmetic.BADGE_DIAMOND -> {
            drawPath(DIAMOND, vertical(0xFFB9F3FF, 0xFF2BA8E0, 4f, 21.5f))
            drawPath(DIAMOND_FACETS, Color.White.copy(alpha = 0.55f), style = Stroke(line, join = StrokeJoin.Round))
            drawPath(DIAMOND_GLINT, Color.White)
        }
        Cosmetic.BADGE_FLAME -> {
            // #FF3D00 at the bottom to #FFB300 at the top.
            drawPath(FLAME, vertical(0xFFFFB300, 0xFFFF3D00))
            drawPath(FLAME_CORE, Color(0xFFFFE57F))
        }
        Cosmetic.BADGE_STAR -> {
            drawPath(STAR, vertical(0xFFFFE082, 0xFFFFB300, 3.6f, 21f))
            drawPath(STAR_SPARKLE, Color.White)
        }
        Cosmetic.BADGE_HEART -> {
            drawPath(HEART, vertical(0xFFFF8AB3, 0xFFE91E63, 4f, 21f))
            drawArc(
                Color.White.copy(alpha = 0.4f), 190f, 70f, false, Offset(5.2f, 6.4f), Size(5.2f, 5.2f),
                style = Stroke(1.6f, cap = StrokeCap.Round),
            )
        }
        Cosmetic.BADGE_BOLT -> {
            drawPath(BOLT, vertical(0xFFFFF176, 0xFFFBC02D))
            drawPath(BOLT, Color(0xFF8D6E00), style = Stroke(line, join = StrokeJoin.Round))
        }
        else -> Unit
    }
}

// ---- frames ------------------------------------------------------------------------------------------------------

/** A frame's ring gradient and the wash at the top of the card (light theme / dark theme). */
private class FrameDesign(val ring: List<Color>, val washLight: Color, val washDark: Color)

private val FRAMES = mapOf(
    Cosmetic.FRAME_FROST to FrameDesign(listOf(Color(0xFFE3F6FF), Color(0xFF8FD3FF), Color(0xFF4AA8FF)), Color(0xFFDFF4FF), Color(0xFF0B2A44)),
    Cosmetic.FRAME_FIRE to FrameDesign(listOf(Color(0xFFFFD54A), Color(0xFFFF6A00), Color(0xFFD50000)), Color(0xFFFFF1E6), Color(0xFF2A0D05)),
    Cosmetic.FRAME_JEWEL to FrameDesign(listOf(Color(0xFFFFE8A3), Color(0xFFD4A017), Color(0xFFFFE8A3)), Color(0xFFFFF8E1), Color(0xFF2A2208)),
    Cosmetic.FRAME_SAND to FrameDesign(listOf(Color(0xFFF4D9A6), Color(0xFFC99A5B), Color(0xFF8B5E34)), Color(0xFFFFF6E6), Color(0xFF2B1E0E)),
    Cosmetic.FRAME_NEON to FrameDesign(listOf(Color(0xFF00F0FF), Color(0xFFFF00E5)), Color(0xFFE9FDFF), Color(0xFF100A24)),
    Cosmetic.FRAME_SAKURA to FrameDesign(listOf(Color(0xFFFFD1E1), Color(0xFFFF8AB3)), Color(0xFFFFF0F5), Color(0xFF2A1220)),
)

private val GEMS = listOf(Color(0xFFE0115F), Color(0xFF0F52BA), Color(0xFF50C878))
private val RING = 3.dp

/**
 * Seconds of a frame's animation, ticking at most 30 times a second while [running] and the app is in the foreground
 * (it holds still otherwise, and stays where it was).
 */
@Composable
private fun rememberFrameClock(running: Boolean): State<Float> {
    val t = remember { mutableFloatStateOf(STILL_AT) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val from = t.floatValue
            val start = withInfiniteAnimationFrameNanos { it }
            var shown = start
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    if (now - shown >= FRAME_NS) {
                        shown = now
                        t.floatValue = from + (now - start) / 1e9f
                    }
                }
            }
        }
    }
    return t
}

/** 30 fps at most. */
private const val FRAME_NS = 33_333_333L
/** Where particles stand when they don't move (reduced motion, before the first tick): spread over the card. */
private const val STILL_AT = 7.3f

/**
 * A card wearing [frame] (none: a plain card): the soft wash at its top, the particles behind [content], and the
 * 3 dp gradient ring around it. Particles move only while the card is on screen and the app in the foreground; with
 * animations off they are drawn still.
 */
@Composable
fun FramedCard(frame: Cosmetic?, modifier: Modifier = Modifier, radius: Dp = 28.dp, content: @Composable BoxScope.() -> Unit) {
    val design = frame?.let(FRAMES::get)
    val night = isNightTheme()
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (design != null) {
            val wash = if (night) design.washDark else design.washLight
            var onScreen by remember { mutableStateOf(false) }
            val reduced = LocalReducedMotion.current
            val clock = rememberFrameClock(onScreen && !reduced)
            Canvas(
                Modifier.matchParentSize().onGloballyPositioned { c -> onScreen = c.boundsInWindow().let { it.width > 0f && it.height > 0f } },
            ) {
                drawRect(Brush.verticalGradient(0f to wash, 0.6f to Color.Transparent))
                drawParticles(frame, clock.value, night)
            }
            content()
            Canvas(Modifier.matchParentSize()) { drawRing(frame, design, clock.value, radius.toPx()) }
        } else {
            content()
        }
    }
}

/** The 3 dp ring around a card (fire flickers, neon glows and pulses, jewels carry 8 gems). */
private fun DrawScope.drawRing(frame: Cosmetic, design: FrameDesign, t: Float, radius: Float) {
    val w = RING.toPx()
    val brush = Brush.linearGradient(design.ring, Offset.Zero, Offset(size.width, size.height))
    val corner = CornerRadius(radius - w / 2)
    val inset = Offset(w / 2, w / 2)
    val box = Size(size.width - w, size.height - w)
    when (frame) {
        Cosmetic.FRAME_FIRE -> drawRoundRect(brush, inset, box, corner, alpha = 0.9f + 0.1f * sin(t * 2f * PI.toFloat() / 0.6f), style = Stroke(w))
        Cosmetic.FRAME_NEON -> {
            // The glow (an 8 px blur on the web): wider, fainter strokes under the ring, pulsing every 2 s.
            val pulse = 0.7f + 0.3f * sin(t * PI.toFloat())
            for (k in 1..4) drawRoundRect(brush, inset, box, corner, alpha = pulse * 0.16f, style = Stroke(w + k * 4.dp.toPx()))
            drawRoundRect(brush, inset, box, corner, style = Stroke(w))
        }
        else -> drawRoundRect(brush, inset, box, corner, style = Stroke(w))
    }
    if (frame == Cosmetic.FRAME_JEWEL) {
        val ring = Path().apply { addRoundRect(RoundRect(Rect(inset, box), corner)) }
        val measure = PathMeasure().apply { setPath(ring, false) }
        val r = 3.5.dp.toPx()
        repeat(8) { i ->
            val p = measure.getPosition(measure.length * (i + 0.5f) / 8f)
            drawCircle(Color.White, r + 1.dp.toPx(), p)
            drawCircle(GEMS[i % GEMS.size], r, p)
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.35f, p + Offset(-r * 0.35f, -r * 0.35f))
        }
    }
}

/** A repeatable 0..1 number for particle [i] and property [k]. */
private fun rnd(i: Int, k: Int): Float {
    val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** [x] wrapped into 0..[m]. */
private fun wrap(x: Float, m: Float) = ((x % m) + m) % m

private fun DrawScope.drawParticles(frame: Cosmetic, t: Float, night: Boolean) {
    val w = size.width
    val h = size.height
    val dp = 1.dp.toPx()
    when (frame) {
        // 18 six-arm snowflakes (4–9 px), white with a #8FD3FF edge, falling slowly with a slight sway.
        Cosmetic.FRAME_FROST -> repeat(18) { i ->
            val s = (4f + 5f * rnd(i, 1)) * dp
            val y = wrap(rnd(i, 2) * h + t * (10f + 14f * rnd(i, 3)) * dp, h + 2 * s) - s
            val x = rnd(i, 4) * w + sin(t * 0.9f + i) * 6f * dp
            rotate(t * 20f + i * 17f, Offset(x, y)) {
                for (a in 0 until 3) {
                    rotate(a * 60f, Offset(x, y)) {
                        drawLine(Color(0xFF8FD3FF), Offset(x - s / 2, y), Offset(x + s / 2, y), 1.8f * dp, StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.9f), Offset(x - s / 2, y), Offset(x + s / 2, y), 0.9f * dp, StrokeCap.Round)
                    }
                }
            }
        }
        // 22 embers (2–4 px) rising and fading.
        Cosmetic.FRAME_FIRE -> repeat(22) { i ->
            val r = (1f + rnd(i, 1)) * dp
            val rise = wrap(rnd(i, 2) * h + t * (18f + 22f * rnd(i, 3)) * dp, h)
            val y = h - rise
            val x = rnd(i, 4) * w + sin(t * 2f + i) * 4f * dp
            drawCircle(if (i % 2 == 0) Color(0xFFFFB300) else Color(0xFFFF6A00), r, Offset(x, y), alpha = (1f - rise / h) * 0.9f)
        }
        // 12 four-point sparkles twinkling in place.
        Cosmetic.FRAME_JEWEL -> repeat(12) { i ->
            val twinkle = abs(sin(t * 1.7f + i * 1.3f))
            val c = Offset(rnd(i, 1) * w, rnd(i, 2) * h)
            drawPath(
                sparkle(c.x, c.y, (2f + 4f * rnd(i, 3)) * dp * (0.3f + 0.7f * twinkle)),
                if (night) Color(0xFFFFE8A3) else Color(0xFFD4A017), alpha = 0.3f + 0.6f * twinkle,
            )
        }
        // 30 grains (1–3 px) blowing left to right with a wave, and 3 faint streaks.
        Cosmetic.FRAME_SAND -> {
            repeat(3) { i ->
                val len = (40f + 40f * rnd(i, 9)) * dp
                val x = wrap(rnd(i, 8) * w + t * 90f * dp, w + len) - len
                val y = (0.2f + 0.6f * rnd(i, 7)) * h
                drawLine(Color(0xFFC99A5B), Offset(x, y), Offset(x + len, y), dp, StrokeCap.Round, alpha = 0.18f)
            }
            repeat(30) { i ->
                val x = wrap(rnd(i, 1) * w + t * (30f + 40f * rnd(i, 2)) * dp, w + 6 * dp) - 3 * dp
                val y = rnd(i, 3) * h + sin(x / w * 3f * PI.toFloat() + i) * 6f * dp
                drawCircle(if (i % 2 == 0) Color(0xFFC99A5B) else Color(0xFFE6C48F), (0.5f + rnd(i, 4)) * dp, Offset(x, y))
            }
        }
        // 14 petals (#FFB7CF teardrops) falling with rotation.
        Cosmetic.FRAME_SAKURA -> repeat(14) { i ->
            val s = (6f + 4f * rnd(i, 1)) * dp
            val y = wrap(rnd(i, 2) * h + t * (12f + 12f * rnd(i, 3)) * dp, h + 2 * s) - s
            val x = rnd(i, 4) * w + sin(t * 0.7f + i) * 10f * dp
            rotate(t * (40f + 50f * rnd(i, 5)) + i * 40f, Offset(x, y)) { drawPath(petal(x, y, s), Color(0xFFFFB7CF)) }
        }
        else -> Unit
    }
}

/** A teardrop petal of length [s], its point up, centred on ([x], [y]). */
private fun petal(x: Float, y: Float, s: Float) = Path().apply {
    moveTo(x, y - s / 2)
    cubicTo(x + s * 0.45f, y - s * 0.1f, x + s * 0.35f, y + s / 2, x, y + s / 2)
    cubicTo(x - s * 0.35f, y + s / 2, x - s * 0.45f, y - s * 0.1f, x, y - s / 2)
    close()
}

/** A big profile photo in [frame]'s 3 dp ring, a small gap inside it (none: the plain photo). */
@Composable
fun FramedAvatar(p: Profile, size: Dp, frame: Cosmetic?, modifier: Modifier = Modifier) {
    val design = frame?.let(FRAMES::get)
    if (design == null) {
        Avatar(p, size, modifier)
        return
    }
    Box(
        modifier.size(size).drawBehind {
            val w = RING.toPx()
            drawCircle(Brush.linearGradient(design.ring), radius = this.size.minDimension / 2 - w / 2, style = Stroke(w))
        },
        contentAlignment = Alignment.Center,
    ) { Avatar(p, size - (RING + 3.dp) * 2) }
}

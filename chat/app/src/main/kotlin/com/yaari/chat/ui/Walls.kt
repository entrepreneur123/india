package com.yaari.chat.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.IntSize
import com.yaari.chat.model.Cosmetic
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---- Chat walls (046): my background behind the messages of my chats (only I see it). Each is a gradient, most with
// a faint pattern tile over it: the web's designs (lib/walls.ts), the same colours, drawn here. Kept quiet enough that
// the bubbles stay easy to read. --------------------------------------------------------------------------------------

/** A soft glow: CSS radial-gradient([rx] [ry] at [x] [y], [color] 0%, transparent [stop]), all as fractions of the box. */
class Glow(val color: Color, val rx: Float, val ry: Float, val x: Float, val y: Float, val stop: Float)

/** A wall: a linear gradient at [angle] (CSS degrees) from [top] to [bottom], [glow] over it, then a pattern tile. */
class WallDesign(
    val top: Color, val bottom: Color, val angle: Float = 180f, val glow: Glow? = null,
    /** The tile's size in dp and its drawing, in dp units. */
    val tile: Size? = null, val pattern: (DrawScope.() -> Unit)? = null,
)

private fun svg(d: String) = PathParser().parsePathString(d).toPath()

private val PAISLEY = listOf(
    "M30 10c12 4 18 16 14 28s-18 18-26 10c-5-5-3-13 3-14 5-1 8 4 5 7", "M30 16c8 4 11 12 8 20",
    "M78 52c-10 4-14 15-10 25s15 13 21 6c4-5 2-11-3-11-4 0-6 4-3 6",
).map(::svg)
private val WAVES = listOf("M0 14q15-10 30 0t30 0 30 0 30 0", "M-30 38q15-10 30 0t30 0 30 0 30 0 30 0").map(::svg)
private val STARS = listOf("M84 92l1.5 4.5 4.5 1.5-4.5 1.5-1.5 4.5-1.5-4.5-4.5-1.5 4.5-1.5z", "M40 34l1 3 3 1-3 1-1 3-1-3-3-1 3-1z").map(::svg)

private fun DrawScope.dots(color: Color, vararg d: Float) {
    for (i in d.indices step 3) drawCircle(color, d[i + 2], Offset(d[i], d[i + 1]))
}

internal val WALLS: Map<Cosmetic, WallDesign> = mapOf(
    Cosmetic.WALL_SAFFRON to WallDesign(Color(0xFFFFF1DE), Color(0xFFFFD9A6), 170f, Glow(Color(0xFFFFD08A), 1.2f, 0.7f, 0.85f, 0f, 0.6f)),
    Cosmetic.WALL_MIDNIGHT to WallDesign(Color(0xFF1B2140), Color(0xFF343A66), glow = Glow(Brand.Saffron.copy(alpha = 0.28f), 0.6f, 0.4f, 0.82f, 0.08f, 0.7f)),
    Cosmetic.WALL_PAISLEY to WallDesign(Color(0xFFFFF8EE), Color(0xFFFBE9D2), tile = Size(96f, 96f)) {
        val c = Color(0xFFC2620A).copy(alpha = 0.3f)
        val line = Stroke(1.4f)
        PAISLEY.forEach { drawPath(it, c, style = line) }
        drawCircle(c, 2f, Offset(70f, 18f), style = line)
        drawCircle(c, 2f, Offset(20f, 76f), style = line)
        dots(c, 48f, 54f, 1.6f, 86f, 30f, 1.2f, 8f, 40f, 1.2f)
    },
    Cosmetic.WALL_MEHNDI to WallDesign(Color(0xFFF7EAD7), Color(0xFFEFD9BC), tile = Size(72f, 72f)) {
        val c = Color(0xFF8A4B1F).copy(alpha = 0.26f)
        val line = Stroke(1.2f)
        val mid = Offset(36f, 36f)
        drawCircle(c, 5f, mid, style = line)
        drawCircle(c, 14f, mid, style = line)
        repeat(8) { i -> rotate(i * 45f, mid) { drawOval(c, Offset(32f, 10f), Size(8f, 16f), style = line) } }
        dots(c, 0f, 0f, 2f, 72f, 0f, 2f, 0f, 72f, 2f, 72f, 72f, 2f, 36f, 36f, 1.6f)
    },
    Cosmetic.WALL_STARS to WallDesign(
        Color(0xFF1A1B44), Color(0xFF2C2559), glow = Glow(Color(0xFF3E3A7A), 0.9f, 0.6f, 0.5f, 0f, 0.7f), tile = Size(140f, 140f),
    ) {
        dots(
            Color.White.copy(alpha = 0.75f), 12f, 20f, 1f, 54f, 8f, 0.8f, 98f, 30f, 1.3f, 128f, 12f, 0.8f, 30f, 70f, 0.9f,
            76f, 60f, 0.7f, 118f, 84f, 1f, 18f, 118f, 1.2f, 64f, 104f, 0.8f, 104f, 128f, 0.9f,
        )
        STARS.forEach { drawPath(it, Color(0xFFFFB24D).copy(alpha = 0.75f)) }
    },
    Cosmetic.WALL_WAVES to WallDesign(Color(0xFFEEF8F8), Color(0xFFD5ECEE), tile = Size(120f, 48f)) {
        WAVES.forEach { drawPath(it, Color(0xFF3D97A3).copy(alpha = 0.28f), style = Stroke(1.6f, cap = StrokeCap.Round)) }
    },
)

/** A wall this app can draw, or null (none, or one a newer server sells). */
fun wallOf(id: String?): WallDesign? = Cosmetic.of(id)?.let(WALLS::get)

/** CSS linear-gradient(angle): the line through the centre, as long as the box seen along it. */
private fun linear(angle: Float, size: Size, from: Color, to: Color): Brush {
    val a = Math.toRadians(angle.toDouble())
    val d = Offset(sin(a).toFloat(), -cos(a).toFloat())
    val half = (abs(size.width * d.x) + abs(size.height * d.y)) / 2
    val c = Offset(size.width / 2, size.height / 2)
    return Brush.linearGradient(listOf(from, to), c - d * half, c + d * half)
}

/** [wall] drawn behind the content (null: nothing; the screen's own background shows). The tile is drawn once. */
fun Modifier.chatWall(wall: WallDesign?): Modifier = if (wall == null) this else drawWithCache {
    val base = linear(wall.angle, size, wall.top, wall.bottom)
    val tile = wall.tile?.let { t ->
        val px = IntSize((t.width * density).toInt().coerceAtLeast(1), (t.height * density).toInt().coerceAtLeast(1))
        val bitmap = ImageBitmap(px.width, px.height)
        CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), Size(px.width.toFloat(), px.height.toFloat())) {
            scale(density, Offset.Zero) { wall.pattern?.invoke(this) }
        }
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
    onDrawBehind {
        drawRect(base)
        wall.glow?.let { g ->
            // An ellipse: a circle of radius rx, squeezed to ry about its centre.
            val c = Offset(g.x * size.width, g.y * size.height)
            val k = (g.ry * size.height) / (g.rx * size.width)
            clipRect {
                scale(1f, k, c) {
                    drawRect(
                        Brush.radialGradient(0f to g.color, g.stop to g.color.copy(alpha = 0f), center = c, radius = g.rx * size.width),
                        Offset(0f, c.y - c.y / k), Size(size.width, size.height / k),
                    )
                }
            }
        }
        tile?.let { drawRect(it) }
    }
}

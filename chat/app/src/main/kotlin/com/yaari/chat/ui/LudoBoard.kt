package com.yaari.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.yaari.chat.domain.Ludo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- The Ludo board, drawn (the web's components/screens/voice/ludo/ludo-board.tsx) ----------------------------
// One Canvas for the board in a 15 x 15 grid of units; the tokens are small composables on top, placed by their
// position in board units (an Animatable drives the hop square by square). Nothing is ever scaled through a parent
// layer: a pawn's size is the radius it is drawn with, so it can't come out several times too big.

/** Colours of the board, muted rather than toy-bright: warm ivory by day, deep slate at night. Seats: red, green, yellow, blue. */
data class LudoPalette(
    val seats: List<Color>, val frame: Color, val tile: Color, val inner: Color, val star: Color, val halo: Color, val wash: Float,
) {
    companion object {
        val Light = LudoPalette(
            listOf(Color(0xFFD8463E), Color(0xFF1F9862), Color(0xFFE2A31C), Color(0xFF2E70D0)),
            frame = Color(0xFFEBE4D8), tile = Color(0xFFFFFDF8), inner = Color(0xFFFFFDF8), star = Color(60, 45, 20).copy(alpha = 0.3f),
            halo = Color.White, wash = 0.10f,
        )
        val Night = LudoPalette(
            listOf(Color(0xFFE0574E), Color(0xFF2BA673), Color(0xFFE7B03A), Color(0xFF4A86E2)),
            frame = Color(0xFF121216), tile = Color(0xFF262630), inner = Color(0xFF18181E), star = Color.White.copy(alpha = 0.32f),
            halo = Color(0xFFF4F2EE), wash = 0.16f,
        )
    }
}

/** CSS color-mix in sRGB: [p] of [a], the rest [b]. */
internal fun mix(a: Color, p: Float, b: Color) = Color(
    red = a.red * p + b.red * (1 - p), green = a.green * p + b.green * (1 - p), blue = a.blue * p + b.blue * (1 - p),
    alpha = a.alpha * p + b.alpha * (1 - p),
)

/** How a token got where it is: one square at a time, or sliding home after a capture. */
enum class LudoMotion { HOP, SLIDE }

/** What the board needs to know about one token. */
data class LudoTokenView(val idx: Int, val pos: Int, val label: String, val movable: Boolean, val motion: LudoMotion?)

/**
 * The board with its tokens. [seats]: colours in play (the others are drawn without tokens); [turn]: whose base
 * glows. Movable tokens pulse and are buttons; every token is read out by TalkBack.
 */
@Composable
fun LudoBoard(
    tokens: List<LudoTokenView>, seats: List<Boolean>, turn: Int?, palette: LudoPalette, label: String,
    modifier: Modifier = Modifier, onToken: (Int) -> Unit = {},
) {
    val reduce = LocalReducedMotion.current
    val glow by rememberLoop(1600, from = 0f, to = 1f, still = 0.5f)
    BoxWithConstraints(modifier.aspectRatio(1f).semantics { contentDescription = label }) {
        val side = constraints.maxWidth.toFloat()
        val unit = side / Ludo.GRID
        Canvas(Modifier.fillMaxSize()) {
            val pulse = if (reduce) 0.7f else 0.15f + 0.6f * (0.5f - 0.5f * cos(glow * 2 * PI.toFloat()))
            drawBoard(palette, turn, pulse)
        }
        val placed = Ludo.layout(tokens.map { it.pos })
        val density = LocalDensity.current
        val box = with(density) { (unit * TOKEN_BOX).toDp() }
        tokens.forEach { t ->
            if (!seats[t.idx / 4]) return@forEach
            key(t.idx) {
                LudoToken(
                    t, placed[t.idx], unit, box, palette.seats[t.idx / 4], palette.halo, reduce,
                    z = if (t.motion != null) 2f else if (t.idx / 4 == turn) 1f else 0f, onToken = onToken,
                )
            }
        }
    }
}

/** A token's box is a bit bigger than a cell: an easier tap, and room for the pulse ring. */
private const val TOKEN_BOX = 1.4f

/** Where a token is drawn right now, mid-move too: board units, its size, and how high it is lifted (units). */
private class TokenTrack(start: Ludo.Placed) {
    // Snapshot state: a jump (no animation) must redraw the token too.
    var from by mutableStateOf(start)
    var to by mutableStateOf(start)
    var slide by mutableStateOf(false)
    val t = Animatable(1f)

    fun x() = from.x + (to.x - from.x) * eased()
    fun y() = from.y + (to.y - from.y) * eased()
    fun scale() = from.scale + (to.scale - from.scale) * eased()
    fun lift() = if (slide) 0f else 0.3f * sin(PI.toFloat() * t.value)
    private fun eased(): Float {
        val k = t.value
        return if (slide) 1 - (1 - k) * (1 - k) * (1 - k) else k
    }

    /** A hop with a little lift for each square, an eased slide back to the base after a capture, else straight there. */
    suspend fun go(target: Ludo.Placed, motion: LudoMotion?, reduce: Boolean) {
        val now = Ludo.Placed(x(), y(), scale())
        if (reduce || motion == null || (now.x == target.x && now.y == target.y)) {
            from = target
            to = target
            t.snapTo(1f)
            return
        }
        from = now
        to = target
        slide = motion == LudoMotion.SLIDE
        t.snapTo(0f)
        t.animateTo(1f, tween(if (slide) 420 else 125, easing = LinearEasing))
    }
}

@Composable
private fun LudoToken(
    t: LudoTokenView, placed: Ludo.Placed, unit: Float, box: Dp, colour: Color, halo: Color, reduce: Boolean, z: Float,
    onToken: (Int) -> Unit,
) {
    val track = remember { TokenTrack(placed) }
    LaunchedEffect(placed) { track.go(placed, t.motion, reduce) }
    val ripple by rememberLoop(1100, still = 0f)
    val bob by rememberLoop(900, still = 0f)
    val half = unit * TOKEN_BOX / 2
    Box(
        Modifier.zIndex(z)
            .offset { IntOffset((track.x() * unit - half).roundToInt(), ((track.y() - track.lift()) * unit - half).roundToInt()) }
            .size(box)
            .semantics { contentDescription = t.label }
            .then(
                if (t.movable) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onToken(t.idx) }
                else Modifier,
            )
            .drawBehind {
                val c = Offset(size.width / 2, size.height / 2)
                val k = unit * track.scale()
                if (t.movable) {
                    // A steady ring (it reads in a still frame and with animations off) and a ripple going out of it.
                    drawCircle(colour.copy(alpha = 0.28f), 0.47f * unit, c)
                    drawCircle(halo, 0.47f * unit, c, style = Stroke(0.07f * unit))
                    if (!reduce) drawCircle(halo.copy(alpha = 0.9f * (1 - ripple)), (0.47f + 0.19f * ripple) * unit, c, style = Stroke(0.05f * unit))
                }
                val rise = if (t.movable && !reduce) 0.07f * sin(PI.toFloat() * bob) else 0f
                drawPawn(c, k, colour, halo, rise, track.lift() * unit)
            },
    )
}

/**
 * A glossy pawn centred on [c], [k] px per unit (1 unit = a cell). [rise] lifts the pawn (units of [k]) above its
 * shadow; [hopPx] is how far the hop already lifted the whole token, so the shadow stays on the board.
 */
internal fun DrawScope.drawPawn(c: Offset, k: Float, colour: Color, halo: Color, rise: Float = 0f, hopPx: Float = 0f) {
    fun p(x: Float, y: Float) = Offset(c.x + x * k, c.y + (y - rise) * k)
    // Shadow on the board (it stays down while the pawn hops).
    val sc = Offset(c.x, c.y + 0.31f * k + hopPx)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.4f * (1 - hopPx / (k * 0.6f)).coerceIn(0.35f, 1f)), Color.Transparent), sc, 0.34f * k),
        topLeft = Offset(sc.x - 0.34f * k, sc.y - 0.11f * k), size = Size(0.68f * k, 0.22f * k),
    )
    val body = Path().apply {
        moveTo(p(-.3f, .25f))
        cubicTo(p(-.3f, .09f), p(-.17f, -.03f), p(-.11f, -.09f))
        lineTo(p(.11f, -.09f))
        cubicTo(p(.17f, -.03f), p(.3f, .09f), p(.3f, .25f))
        cubicTo(p(.3f, .34f), p(-.3f, .34f), p(-.3f, .25f))
        close()
    }
    val head = p(0f, -.25f)
    // A white rim keeps the pawn readable on its own colour.
    drawPath(body, halo)
    drawPath(body, halo, style = Stroke(0.1f * k, join = StrokeJoin.Round))
    drawCircle(halo, 0.17f * k + 0.05f * k, head)
    val stops = arrayOf(0f to mix(colour, 0.45f, Color.White), 0.45f to colour, 1f to mix(colour, 0.62f, Color.Black))
    drawPath(body, Brush.radialGradient(*stops, center = p(-.3f + .36f * .6f, -.09f + .3f * .41f), radius = 0.8f * 0.5f * k))
    drawOval(mix(colour, 0.6f, Color.Black).copy(alpha = 0.55f), topLeft = p(-.15f, -.135f), size = Size(0.3f * k, 0.09f * k))
    drawCircle(Brush.radialGradient(*stops, center = p(-.17f + .36f * .34f, -.42f + .3f * .34f), radius = 0.8f * 0.34f * k), 0.17f * k, head)
    drawOval(Color.White.copy(alpha = 0.75f), topLeft = p(-.115f, -.348f), size = Size(0.12f * k, 0.076f * k))
    rotate(25f, pivot = p(-.13f, .12f)) {
        drawOval(Color.White.copy(alpha = 0.28f), topLeft = p(-.17f, .04f), size = Size(0.08f * k, 0.16f * k))
    }
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
private fun Path.cubicTo(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)

/** A five-point star centred on ([cx], [cy]) in units. */
private fun starPath(cx: Float, cy: Float, r: Float, u: Float) = Path().apply {
    for (i in 0 until 10) {
        val a = (PI / 5 * i - PI / 2).toFloat()
        val rr = if (i % 2 == 1) r * 0.45f else r
        val x = (cx + rr * cos(a)) * u
        val y = (cy + rr * sin(a)) * u
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The board: frame, track, the four quarters (base, start, home column, arrow, star, centre triangle). [pulse]: the glow of [turn]'s base. */
internal fun DrawScope.drawBoard(p: LudoPalette, turn: Int?, pulse: Float) {
    val u = size.width / Ludo.GRID
    fun tile(c: Float, r: Float, fill: Color) =
        drawRoundRect(fill, Offset((c + 0.05f) * u, (r + 0.05f) * u), Size(0.9f * u, 0.9f * u), CornerRadius(0.14f * u))
    drawRoundRect(p.frame, Offset.Zero, size, CornerRadius(0.5f * u))
    Ludo.TRACK.forEachIndexed { i, cell -> if (i !in Ludo.STARTS) tile(cell.x, cell.y, p.tile) }
    drawRoundRect(p.tile, Offset(6.05f * u, 6.05f * u), Size(2.9f * u, 2.9f * u), CornerRadius(0.14f * u))
    for (seat in 0..3) {
        val c = p.seats[seat]
        // The base, with its light from the top left whatever the corner.
        val a = Ludo.turn(Ludo.Pt(0.14f, 9.14f), seat)
        val b = Ludo.turn(Ludo.Pt(5.86f, 14.86f), seat)
        val base = Rect(minOf(a.x, b.x) * u, minOf(a.y, b.y) * u, maxOf(a.x, b.x) * u, maxOf(a.y, b.y) * u)
        drawRoundRect(
            Brush.linearGradient(listOf(mix(c, 0.82f, Color.White), mix(c, 0.86f, Color.Black)), base.topLeft, base.bottomRight),
            base.topLeft, base.size, CornerRadius(0.42f * u),
        )
        if (turn == seat) drawRoundRect(p.halo.copy(alpha = pulse), base.topLeft, base.size, CornerRadius(0.42f * u), style = Stroke(0.08f * u))
        withTransform({ rotate(seat * 90f, pivot = Offset(7.5f * u, 7.5f * u)) }) {
            drawRoundRect(p.inner, Offset(0.9f * u, 9.9f * u), Size(4.2f * u, 4.2f * u), CornerRadius(0.5f * u))
            drawRoundRect(c.copy(alpha = 0.3f), Offset(0.9f * u, 9.9f * u), Size(4.2f * u, 4.2f * u), CornerRadius(0.5f * u), style = Stroke(0.04f * u))
            for (w in Ludo.BASE_WELLS) {
                drawCircle(mix(c, 0.16f, p.inner), 0.64f * u, Offset(w.x * u, w.y * u))
                drawCircle(c.copy(alpha = 0.45f), 0.64f * u, Offset(w.x * u, w.y * u), style = Stroke(0.05f * u))
            }
            // Start square (safe) and the home column.
            tile(6f, 13f, c)
            drawPath(starPath(6.5f, 13.5f, 0.3f, u), Color.White.copy(alpha = 0.9f), style = Stroke(0.06f * u, join = StrokeJoin.Round))
            for (r in 9..13) tile(7f, r.toFloat(), mix(c, 0.88f, p.tile))
            // The way in: an arrow on the square before the home column.
            drawPath(
                Path().apply { moveTo(7.22f * u, 14.72f * u); lineTo(7.5f * u, 14.36f * u); lineTo(7.78f * u, 14.72f * u) }, c,
                style = Stroke(0.11f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            // Star square (safe).
            drawPath(starPath(2.5f, 8.5f, 0.3f, u), p.star)
            // Centre triangle.
            drawPath(
                Path().apply { moveTo(6.04f * u, 8.96f * u); lineTo(8.96f * u, 8.96f * u); lineTo(7.5f * u, 7.5f * u); close() },
                Brush.verticalGradient(listOf(mix(c, 0.7f, Color.Black), c), startY = 7.5f * u, endY = 8.96f * u),
            )
        }
    }
    drawCircle(p.tile, 0.2f * u, Offset(7.5f * u, 7.5f * u))
}

// ---- the die ----

/** Pips on a 3 x 3 grid (0-8), per face. */
private val PIPS = mapOf(1 to listOf(4), 2 to listOf(2, 6), 3 to listOf(2, 4, 6), 4 to listOf(0, 2, 6, 8), 5 to listOf(0, 2, 4, 6, 8), 6 to listOf(0, 2, 3, 5, 6, 8))

/**
 * A die you can almost pick up: ivory, rounded, with a little depth. It tumbles while the server rolls (faces
 * flicking past, turning in 3D), then lands on the server's number with a small settle. Animations off: the number
 * just changes.
 */
@Composable
fun LudoDice(value: Int?, rolling: Boolean, size: Dp, colour: Color, enabled: Boolean, label: String, onRoll: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReducedMotion.current
    var face by remember { mutableIntStateOf(value ?: 6) }
    val spin = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(rolling, value, reduce) {
        if (reduce) {
            face = value ?: face
            return@LaunchedEffect
        }
        if (rolling) {
            // Tumble until the number arrives: a new face every few frames, turning all the while.
            var last = face
            while (true) {
                spin.animateTo(spin.value + 90f, tween(105, easing = LinearEasing))
                var next = 1 + (Math.random() * 6).toInt()
                if (next == last) next = next % 6 + 1
                last = next
                face = next
                tilt.snapTo(if (tilt.value > 0) -1f else 1f)
            }
        } else {
            face = value ?: face
            // Land: one more turn that slows onto the face, and a small settle.
            val target = (Math.ceil(spin.value / 360.0) * 360).toFloat()
            tilt.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            if (spin.value != target) spin.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val glow by rememberLoop(1400, still = 0.5f)
    Box(
        modifier.size(size * 1.3f)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onRoll) else Modifier)
            .semantics { contentDescription = label }
            .drawBehind {
                if (enabled) {
                    val a = 0.45f + 0.4f * (0.5f - 0.5f * cos(glow * 2 * PI.toFloat()))
                    drawRoundRect(colour.copy(alpha = a * 0.35f), cornerRadius = CornerRadius(this.size.width * 0.3f))
                    drawRoundRect(colour.copy(alpha = a), cornerRadius = CornerRadius(this.size.width * 0.3f), style = Stroke(2f * density))
                }
                // Its shadow on the table.
                val w = this.size.width * 0.62f
                drawOval(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), Offset(this.size.width / 2, this.size.height * 0.86f), w / 2),
                    topLeft = Offset((this.size.width - w) / 2, this.size.height * 0.86f - w * 0.12f), size = Size(w, w * 0.24f),
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Canvas(
            Modifier.size(size).graphicsLayer {
                // The die alone turns (it holds nothing else), in 3D while it tumbles.
                rotationZ = spin.value
                rotationX = 22f * tilt.value
                rotationY = -18f * tilt.value
                cameraDistance = 12f * density
                alpha = if (reduce && rolling) 0.6f else 1f
            },
        ) { drawDie(face, colour) }
    }
}

private fun DrawScope.drawDie(n: Int, colour: Color) {
    val s = size.width
    val r = CornerRadius(s * 0.22f)
    // Depth: the die's side shows under its face.
    drawRoundRect(Color(0xFFBDB6A8), Offset(0f, s * 0.05f), Size(s, s * 0.97f), r)
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White, Color(0xFFF1EEE8), Color(0xFFDCD7CD)), Offset(s * 0.1f, 0f), Offset(s * 0.9f, s)),
        Offset.Zero, Size(s, s * 0.95f), r,
    )
    drawRoundRect(Color.Black.copy(alpha = 0.06f), Offset.Zero, Size(s, s * 0.95f), r, style = Stroke(s * 0.02f))
    val cell = s * 0.74f / 3
    val pip = s * 0.085f
    PIPS.getValue(n).forEach { i ->
        val c = Offset(s * 0.13f + cell * (i % 3 + 0.5f), s * 0.13f * 0.95f + cell * 0.95f * (i / 3 + 0.5f))
        val ink = if (n == 1) colour else Color(0xFF26232B)
        drawCircle(Brush.radialGradient(listOf(mix(ink, 0.7f, Color.Black), ink), Offset(c.x, c.y - pip * 0.4f), pip * 1.4f), pip, c)
    }
}

// ---- the turn clock ----

/**
 * The turn clock around the player's avatar: a ring that empties over the turn. [deadline] is on this phone's wall
 * clock; the ring restarts whenever it changes.
 */
@Composable
fun TurnRing(deadline: Long, colour: Color, track: Color, modifier: Modifier = Modifier) {
    // Read from the wall clock every frame (not a tween): it stays right when frames drop or the app comes back.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        do {
            withInfiniteAnimationFrameMillis { }
            now = System.currentTimeMillis()
        } while (now < deadline)
    }
    Canvas(modifier) {
        val left = ((deadline - now).toFloat() / Ludo.TURN_MS).coerceIn(0f, 1f)
        val w = 3f * density
        val inset = w / 2 + density
        val arc = Size(size.width - inset * 2, size.height - inset * 2)
        drawCircle(track, arc.width / 2, style = Stroke(w))
        drawArc(colour, -90f, 360f * left, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
    }
}

// ---- the result's cup ----

private val TROPHY = listOf(
    "M5.2 4.6H3.4a1 1 0 0 0-1 1.1c.3 3 1.9 5 4.6 5.6l.4-2C5.6 8.8 4.8 7.7 4.6 6.6h.8zM18.8 4.6h1.8a1 1 0 0 1 1 1.1c-.3 3-1.9 5-4.6 5.6l-.4-2c1.8-.5 2.6-1.6 2.8-2.7h-.8z" to true,
    "M6 3h12v5.2a6 6 0 0 1-12 0z" to false,
    "M10.7 13.6h2.6v3.6h-2.6z" to false,
    "M8.6 17.2h6.8a1.4 1.4 0 0 1 1.4 1.4v.8a1.4 1.4 0 0 1-1.4 1.4H8.6a1.4 1.4 0 0 1-1.4-1.4v-.8a1.4 1.4 0 0 1 1.4-1.4z" to false,
    "M9.8 18.4h4.4a.6 .6 0 0 1 0 1.2H9.8a.6 .6 0 0 1 0-1.2z" to true,
)

/** A two-tone cup, drawn like the topic glyphs. */
@Composable
fun Trophy(size: Dp, modifier: Modifier = Modifier) {
    val paths = remember { TROPHY.map { (d, light) -> PathParser().parsePathString(d).toPath() to light } }
    Canvas(modifier.size(size)) {
        val gold = Color(0xFFE9B23A)
        val light = mix(gold, 0.45f, Color.White)
        withTransform({ scale(this@Canvas.size.width / 24f, this@Canvas.size.width / 24f, Offset.Zero) }) {
            paths.forEach { (path, isLight) -> drawPath(path, if (isLight) light else gold) }
            drawPath(
                Path().apply { moveTo(9.4f, 5.2f); cubicTo(9.4f, 7.8f, 9.9f, 9.6f, 11f, 10.8f) }, light,
                style = Stroke(1.3f, cap = StrokeCap.Round),
            )
        }
    }
}

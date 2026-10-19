package com.yaari.chat.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Motion language: springs for anything the finger touches, slow loops for ambient life, nothing linear on taps.
// Ambient loops stop when the phone's "Remove animations" setting is on (animator duration scale 0).

/** True when the user turned animations off; ambient loops then hold still. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** The mockup's ease-out curve (cubic-bezier(0.23, 1, 0.32, 1)). */
val EaseOutQuint: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * An endless 0..1 (or [from]..[to]) loop for ambient motion. Holds [still] when motion is reduced.
 * Read the value in a draw or layout lambda so the loop never recomposes the screen.
 */
@Composable
fun rememberLoop(
    durationMs: Int, from: Float = 0f, to: Float = 1f, reverse: Boolean = false, easing: Easing = LinearEasing,
    delayMs: Int = 0, offsetMs: Int = 0, still: Float = from,
): State<Float> {
    if (LocalReducedMotion.current) return remember(still) { mutableFloatStateOf(still) }
    val t = rememberInfiniteTransition(label = "loop")
    return t.animateFloat(
        from, to,
        infiniteRepeatable(tween(durationMs, delayMillis = delayMs, easing = easing), if (reverse) RepeatMode.Reverse else RepeatMode.Restart, StartOffset(offsetMs)),
        label = "loop",
    )
}

/** Shrinks a little while pressed and springs back, like iOS / Instagram buttons. Pair with the same [interaction] in clickable. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        // stiffness 600 / damping 30 / mass 0.6 in the mockup's framer-motion terms
        spring(dampingRatio = 0.6f, stiffness = 900f), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A tap that also gives a light haptic tick (send, like, tab change). */
@Composable
fun rememberTick(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** Skeleton loading block with a light sweeping across it. */
fun Modifier.shimmer(): Modifier = composed {
    val x by rememberLoop(1300, from = -1f, to = 2f, still = 0.5f)
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val shine = MaterialTheme.colorScheme.surfaceContainerLowest
    drawBehind {
        drawRect(
            Brush.linearGradient(
                listOf(base, shine, base),
                start = Offset(size.width * x - size.width, 0f), end = Offset(size.width * x, size.height),
            ),
        )
    }
}

/** Three dots hopping one after another: "typing…". */
@Composable
fun TypingDots(color: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    val phase by rememberLoop(1200, still = 0.25f)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .graphicsLayer {
                        val p = ((phase - i * 0.15f) % 1f + 1f) % 1f
                        // up at 30 %, rest from 60 % (the mockup's `typing` keyframes)
                        val lift = when {
                            p < 0.3f -> p / 0.3f
                            p < 0.6f -> 1f - (p - 0.3f) / 0.3f
                            else -> 0f
                        }
                        translationY = -3.dp.toPx() * lift
                        alpha = 0.35f + 0.65f * lift
                    }
                    .size(7.dp).clip(CircleShape).background(color),
            )
        }
    }
}

/** Numbers roll up or down when they change (online counts, stats). */
@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color = Color.Unspecified, suffix: String = "") {
    AnimatedContent(
        value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically { h -> if (up) h else -h } + fadeIn()) togetherWith (slideOutVertically { h -> if (up) -h else h } + fadeOut())
        },
        label = "number",
    ) { n -> Text("$n$suffix", style = style, color = color) }
}

/**
 * Three slow drifting colour blobs over ink, like the mockup's lobby aurora (pink, orange, raspberry),
 * with a light ink veil on top so text stays readable.
 */
fun Modifier.aurora(intensity: Float = 1f): Modifier = composed {
    val a by rememberLoop(14_000, to = (2 * PI).toFloat(), still = 0.8f)
    drawBehind {
        drawRect(Brand.Night)
        val w = size.width
        val h = size.height
        fun blob(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) =
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha * intensity), color.copy(alpha = alpha * intensity * 0.35f), Color.Transparent), center = Offset(cx, cy), radius = r),
                r, Offset(cx, cy),
            )
        blob(Color(0xFFFF3D68), 0.42f, w * (0.10f + 0.16f * sin(a)), h * (0.24f + 0.08f * sin(a)), w * 0.78f)
        blob(Color(0xFFFF7A45), 0.34f, w * (0.95f - 0.18f * sin(a * 1.0f + 1f)), h * (0.42f - 0.05f * sin(a + 1f)), w * 0.80f)
        blob(Color(0xFFD9346E), 0.28f, w * (0.40f + 0.10f * sin(a + 2f)), h * (0.70f - 0.08f * sin(a + 2f)), w * 0.62f)
        drawRect(Brand.Night.copy(alpha = 0.30f))
    }
}

/** A burst of confetti from the centre, played once when [key] changes (a new match). */
@Composable
fun ConfettiBurst(key: Any, modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) return
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(1400)) }
    val pieces = remember(key) {
        val r = Random(key.hashCode())
        List(48) { Triple(r.nextFloat() * 2 * PI.toFloat(), 0.4f + r.nextFloat() * 0.6f, Confetti[r.nextInt(Confetti.size)]) }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val c = Offset(size.width / 2, size.height * 0.42f)
        pieces.forEach { (angle, speed, color) ->
            val dist = size.minDimension * 0.55f * speed * p
            val fall = size.height * 0.25f * p * p // gravity
            val pos = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist + fall)
            drawRect(color.copy(alpha = 1f - p), topLeft = pos, size = androidx.compose.ui.geometry.Size(14f, 8f))
        }
    }
}

private val Confetti = listOf(Color(0xFFFF3D68), Color(0xFFFF7A45), Color(0xFFFFC94D), Color(0xFF4ADE80), Color(0xFF60A5FA), Color.White)

/**
 * Green dot with a halo that swells and fades every 2 s: someone or something is live.
 * The halo draws outside the dot's own [size], so it never shifts the layout.
 */
@Composable
fun PresenceDot(size: Dp = 7.dp, color: Color = Brand.Online) {
    val p by rememberLoop(2000, easing = FastOutSlowInEasing, still = 1f)
    Box(
        Modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2
            // box-shadow 0 0 0 0..6px, alpha .55 -> 0 by 70 %
            val k = (p / 0.7f).coerceAtMost(1f)
            drawCircle(color.copy(alpha = 0.55f * (1f - k)), radius = r + 6.dp.toPx() * k)
            drawCircle(color, radius = r)
        },
    )
}

/**
 * Rings that grow from 35 % to 155 % of [diameter] and fade (the matching radar). [ringColor] strokes, [fill] tints.
 */
@Composable
fun Radar(diameter: Dp, modifier: Modifier = Modifier, rings: Int = 3, periodMs: Int = 3000, ringColor: Color = Brand.Pink.copy(alpha = 0.5f), fill: Color = Brand.Pink.copy(alpha = 0.05f), stroke: Dp = 1.dp) {
    val phase by rememberLoop(periodMs, still = 0.4f)
    Canvas(modifier.requiredSize(diameter)) {
        val base = diameter.toPx() / 2
        for (i in 0 until rings) {
            val p = (phase + i / rings.toFloat()) % 1f
            val e = EaseOutQuint.transform(p)
            val r = base * (0.35f + 1.2f * e)
            val alpha = 0.8f * (1f - p)
            drawCircle(fill.copy(alpha = fill.alpha * alpha / 0.8f), radius = r)
            drawCircle(ringColor.copy(alpha = ringColor.alpha * alpha / 0.8f), radius = r, style = Stroke(stroke.toPx()))
        }
    }
}

/**
 * The lobby's avatar ring: a conic brand gradient that fades out halfway round, turning slowly,
 * with a soft glow of the same colours behind it.
 */
fun Modifier.spinningRing(width: Dp = 3.dp, glow: Boolean = true): Modifier = composed {
    val turn by rememberLoop(6000, to = 360f, still = 30f)
    drawBehind {
        val conic = Brush.sweepGradient(
            0f to Brand.Pink, 0.12f to Brand.Orange, 0.55f to Brand.Orange.copy(alpha = 0f), 0.8f to Brand.Pink.copy(alpha = 0f), 1f to Brand.Pink,
            center = center,
        )
        rotate(turn) {
            if (glow) {
                // A wide translucent band stands in for CSS blur(18px).
                for (k in 1..6) drawCircle(conic, radius = size.minDimension / 2 + k * 3.dp.toPx(), alpha = 0.09f, style = Stroke(6.dp.toPx()))
            }
            drawCircle(conic, radius = size.minDimension / 2 - width.toPx() / 2, style = Stroke(width.toPx()))
        }
    }
}

/** Places children on a circle that turns once per [turnMs]; each child stays upright. */
@Composable
fun Orbiting(count: Int, radius: Dp, turnMs: Int, modifier: Modifier = Modifier, item: @Composable (Int) -> Unit) {
    val turn by rememberLoop(turnMs, to = 360f, still = 0f)
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        repeat(count) { i ->
            Box(
                Modifier.offset {
                    val angle = Math.toRadians(i * 360.0 / count + turn)
                    val r = radius.toPx()
                    IntOffset((r * cos(angle)).toInt(), (r * sin(angle)).toInt())
                },
            ) { item(i) }
        }
    }
}

/** A quick springy pop when something becomes selected (tab icons). */
fun Modifier.bounceOn(selected: Boolean): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(0.78f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * Fades and lifts content in once, [index] steps after the screen appears (the mockup's staggered pop-in).
 */
fun Modifier.enterStagger(index: Int, stepMs: Int = 60): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(10) * stepMs).toLong())
        p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f))
    }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 10.dp.toPx()
    }
}

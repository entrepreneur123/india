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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Motion language (Material 3): short, subtle and critically damped; no bounce. A handful of small ambient loops
// (typing dots, presence dot, the matching radar) stop when the phone's "Remove animations" setting is on.

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

/** Settles a touch smaller while pressed (no bounce). Pair with the same [interaction] in clickable. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.98f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) maxOf(pressed, 0.96f) else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "press",
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
 * The dark screens' backdrop: flat ink with one very faint, static brand tint at the top.
 * Static on purpose: no per-frame drawing, so it costs nothing on low-end GPUs and batteries.
 */
fun Modifier.aurora(): Modifier = drawWithCache {
    val tint = Brush.radialGradient(
        listOf(Brand.LogoPink.copy(alpha = 0.10f), Color.Transparent),
        center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f,
    )
    onDrawBehind {
        drawRect(Brand.Night)
        drawRect(tint)
    }
}

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
fun Radar(diameter: Dp, modifier: Modifier = Modifier, rings: Int = 3, periodMs: Int = 3000, ringColor: Color = Brand.PinkOnDark.copy(alpha = 0.35f), fill: Color = Color.Transparent, stroke: Dp = 1.dp) {
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

/** A thin, flat brand ring around an avatar (the lobby, the match reveal). */
fun Modifier.brandRing(width: Dp = 2.dp, color: Color = Brand.PinkOnDark): Modifier = drawBehind {
    drawCircle(color, radius = size.minDimension / 2 - width.toPx() / 2, style = Stroke(width.toPx()))
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

/**
 * Fades and lifts content in once, [index] steps after the screen appears (the mockup's staggered pop-in).
 */
fun Modifier.enterStagger(index: Int, stepMs: Int = 30): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(6) * stepMs).toLong())
        p.animateTo(1f, tween(220, easing = EaseOutQuint))
    }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 8.dp.toPx()
    }
}

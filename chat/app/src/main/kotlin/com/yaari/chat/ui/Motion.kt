package com.yaari.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Motion language: springs for anything the finger touches, slow loops for ambient life, nothing linear on taps.

/** Shrinks a little while pressed and springs back, like iOS / Instagram buttons. Pair with the same [interaction] in clickable. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
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
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
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
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val p = ((phase - i * 0.18f) % 1f + 1f) % 1f
            val lift = if (p < 0.5f) sin(p * 2 * PI).toFloat() else 0f
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, (-4 * lift * density).toInt()) }.size(6.dp).clip(CircleShape).background(color.copy(alpha = 0.5f + 0.5f * lift)))
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

/** Slow drifting colour blobs behind dark screens: alive, never distracting. */
fun Modifier.aurora(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "a")
    drawBehind {
        drawRect(Brand.Night)
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
        blob(Color(0x66FF3D68), w * (0.30f + 0.12f * cos(a)), h * (0.30f + 0.06f * sin(a)), w * 0.75f)
        blob(Color(0x55FF7A45), w * (0.75f + 0.10f * sin(a * 1.3f)), h * (0.55f + 0.08f * cos(a)), w * 0.70f)
        blob(Color(0x447C3AED), w * (0.45f + 0.15f * sin(a * 0.7f)), h * (0.85f + 0.04f * cos(a * 1.1f)), w * 0.80f)
    }
}

/** A burst of confetti from the centre, played once when [key] changes (a new match). */
@Composable
fun ConfettiBurst(key: Any, modifier: Modifier = Modifier) {
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

/** Little dot that breathes: something is live. */
@Composable
fun LiveDot(color: Color = Brand.Online, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "live")
    val s by t.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "s")
    Box(Modifier.size(size * 2.2f).padding(0.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color.copy(alpha = (2.2f - s) / 1.2f * 0.35f), radius = this.size.minDimension / 2 / 2.2f * s)
            drawCircle(color, radius = this.size.minDimension / 2 / 2.2f)
        }
    }
}

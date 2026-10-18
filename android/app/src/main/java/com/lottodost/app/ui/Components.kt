package com.lottodost.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SlipShape = RoundedCornerShape(10.dp)

/** A white result slip on the paper-coloured ground: thin border, no shadow, no tint. */
@Composable
fun Slip(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = SlipShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { Column(content = content) }
}

/**
 * Slip with a ticket stub on the left (draw time and its common name), separated by a
 * perforated line like the tear-off edge of a paper ticket.
 */
@Composable
fun StubSlip(
    time: String,
    tag: String?,
    modifier: Modifier = Modifier,
    // Dark theme: the light-red primary would glare as a big block, so use the deep red container.
    stubColor: Color = if (isDark()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
    onStubColor: Color = if (isDark()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Slip(modifier, onClick) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Surface(color = stubColor, modifier = Modifier.fillMaxHeight().widthIn(min = 76.dp)) {
                Column(Modifier.fillMaxHeight().padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(time, color = onStubColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                    tag?.let { Text(it, color = onStubColor, fontSize = 12.sp, textAlign = TextAlign.Center) }
                }
            }
            Perforation(Modifier.fillMaxHeight().width(1.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), content = content)
        }
    }
}

@Composable
private fun Perforation(modifier: Modifier) {
    val c = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        drawLine(c, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = size.width * 2,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

/** Small uppercase label above a value ("1st Prize · ₹1 Crore"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color, letterSpacing = 0.4.sp)
}

/**
 * One-line number that shrinks to fit its width instead of wrapping: a ticket number split over
 * two lines is unreadable (happens at 200 % system font on small phones).
 */
@Composable
fun FitNumber(
    text: String,
    maxSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicText(
        text,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(color = color, fontSize = maxSize, fontWeight = FontWeight.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        maxLines = 1,
        softWrap = false,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = maxSize),
    )
}

@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

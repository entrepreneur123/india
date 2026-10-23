package com.yaari.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.yaari.chat.R
import com.yaari.chat.domain.flagOf
import com.yaari.chat.model.Profile
import com.yaari.chat.model.VIP_NAME
import com.yaari.chat.model.isVip
import com.yaari.chat.model.nameAge

// ---- Design tokens ------------------------------------------------------------------------------
// White and orange, like the web (app/globals.css): white surfaces everywhere, orange as the one brand colour, on
// one warm-grey (stone) ramp for ink and lines. Orange is a fill with white on it, or a mark; as small text it is
// [Brand.SaffronDeep]. Only live video stays dark. Light only. No gradients. 8 dp grid (4 dp fine steps).

object Brand {
    /** The brand orange: fills (bold [OnSaffron] text on it, 3.2:1, large-text AA) and marks. */
    val Saffron = Color(0xFFEA6A00)

    /** Orange as text or small icons on white (5:1). */
    val SaffronDeep = Color(0xFFB45309)

    /** Soft orange for tonal containers (selected tab pill, feature cards, my chat bubbles). */
    val SaffronSoft = Color(0xFFFFF1E0)

    /** Text and icons on an orange fill. */
    val OnSaffron = Color.White

    /** Flat fill for primary actions, as a brush for the places that paint with one. */
    val fill: Brush = SolidColor(Saffron)

    val Online = Color(0xFF1E9E54)
    val Danger = Color(0xFFD93025)

    /** Ink and neutrals (stone): text 17.5:1 on white, muted text 7.6:1. */
    val Text = Color(0xFF1C1917)
    val Muted = Color(0xFF57534E)
    val Line = Color(0xFFE7E5E4)
    val Surface = Color(0xFFF5F5F4)

    /** Live video fills the screen behind the call controls. */
    val Video = Color(0xFF0C0A09)
}

/** Orange as text or a small mark (like the web's --primary-text). */
val ColorScheme.primaryText get() = Brand.SaffronDeep

private val Light = lightColorScheme(
    primary = Brand.Saffron, onPrimary = Brand.OnSaffron,
    primaryContainer = Brand.SaffronSoft, onPrimaryContainer = Brand.SaffronDeep,
    secondary = Brand.Text, onSecondary = Color.White,
    secondaryContainer = Brand.SaffronSoft, onSecondaryContainer = Brand.SaffronDeep,
    background = Color.White, onBackground = Brand.Text,
    surface = Color.White, onSurface = Brand.Text,
    surfaceVariant = Brand.Surface, onSurfaceVariant = Brand.Muted,
    surfaceContainer = Color(0xFFFAFAF9), surfaceContainerHigh = Brand.Surface, surfaceContainerLow = Color(0xFFFAFAF9),
    surfaceContainerLowest = Color.White, surfaceContainerHighest = Brand.Line,
    outline = Color(0xFFD6D3D1), outlineVariant = Brand.Line,
    error = Brand.Danger,
)

/**
 * Plus Jakarta Sans (SIL OFL 1.1, docs/FONT_LICENSE_OFL.txt), subset to Latin + Vietnamese and unhinted to keep
 * the APK small. Hindi and Bengali fall back to the system Devanagari / Bengali fonts.
 */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
)

// Material 3 type scale (sizes / line heights), set in Jakarta with calm weights and no negative tracking.
private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

private val AppType = Typography(
    displayLarge = style(57, 64),
    displayMedium = style(45, 52),
    displaySmall = style(36, 44),
    headlineLarge = style(32, 40, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.1f),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1f),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20),
    bodySmall = style(12, 16, spacing = 0.2f),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = style(12, 16, FontWeight.Medium, 0.3f),
    labelSmall = style(11, 16, FontWeight.Medium, 0.4f),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun YaariTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(colorScheme = Light, typography = AppType, shapes = AppShapes, content = content)
    }
}

/** The lowercase brand wordmark: orange (the deep shade below 24 sp, where it is small text), or plain [color]. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Int = 26, color: Color? = null) {
    val ink = color ?: if (size < 24) Brand.SaffronDeep else Brand.Saffron
    Text("yaari", modifier, style = TextStyle(color = ink, fontSize = size.sp, fontFamily = Jakarta, fontWeight = FontWeight.Bold, letterSpacing = (-0.03 * size).sp))
}

/** The main call to action: a full-width, flat brand-colour pill with a Material ripple. */
@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 56.dp,
    leading: (@Composable () -> Unit)? = null, shape: Shape = CircleShape,
) {
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberTick()
    val bg by animateColorAsState(if (enabled) Brand.Saffron else MaterialTheme.colorScheme.surfaceContainerHighest, label = "bg")
    Box(
        modifier.fillMaxWidth().height(height).pressScale(interaction).clip(shape).background(bg)
            .clickable(interaction, indication = ripple(color = Brand.OnSaffron), enabled = enabled, role = Role.Button) { tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(
                text, color = if (enabled) Brand.OnSaffron else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Secondary button ("Edit profile", "Message"): tonal grey, outlined, or [accent] = flat brand colour. */
@Composable
fun SoftButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true,
    height: Dp = 40.dp, shape: Shape = CircleShape, outlined: Boolean = false, leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        when {
            accent -> Brand.Saffron
            outlined -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "bg",
    )
    Row(
        // Visually [height], but never a touch target under 48 dp.
        modifier.height(height).pressScale(interaction).clip(shape)
            .background(bg)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
            .clickable(interaction, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (accent) Brand.OnSaffron else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A small pill: room counts, plan badges, filters. */
@Composable
fun Pill(
    text: String, modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    color: Color = MaterialTheme.colorScheme.onSurface, leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.clip(CircleShape).background(background).padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Material icon button: a 48 dp touch target, optionally on a tinted disc of [size]. */
@Composable
fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    background: Color = Color.Transparent, tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp, iconSize: Dp = 24.dp, enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .clickable(interaction, indication = ripple(bounded = false, radius = 24.dp), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(iconSize), tint = tint)
        }
    }
}

/**
 * Photo, or the first letter on one neutral grey (no colour per person); someone in VIP incognito (046): "VIP" in white
 * on orange. [ring] draws a thin saffron ring with a gap (own profile).
 */
@Composable
fun Avatar(p: Profile, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false, gap: Color = MaterialTheme.colorScheme.background) {
    val inner = if (ring) size - 8.dp else size
    Box(
        modifier.size(size).then(if (ring) Modifier.clip(CircleShape).background(Brand.Saffron).padding(2.dp).clip(CircleShape).background(gap) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            if (p.photoUri != null) {
                // Coil decodes to the avatar's own size (it reads the bounded constraints), never the full photo.
                AsyncImage(p.photoUri, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (p.isVip) {
                Box(Modifier.fillMaxSize().background(Brand.Saffron), contentAlignment = Alignment.Center) {
                    Text(VIP_NAME, color = Brand.OnSaffron, fontSize = maxOf(9f, inner.value * 0.3f).sp, fontWeight = FontWeight.ExtraBold, fontFamily = Jakarta)
                }
            } else {
                Text(p.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = (inner.value * 0.42f).sp, fontWeight = FontWeight.SemiBold, fontFamily = Jakarta)
            }
        }
    }
}

/** "Priya 🇮🇳" */
fun Profile.nameWithFlag() = flagOf(country).let { if (it.isEmpty()) name else "$name $it" }

/** "Priya, 22 🇮🇳", the mockup's way of naming someone you meet (no age for someone in VIP incognito). */
fun Profile.nameAgeFlag() = flagOf(country).let { if (it.isEmpty()) nameAge(name, age) else "${nameAge(name, age)} $it" }

@Composable
fun Profile.subtitle() = listOf(if (age > 0) age.toString() else "", genderText(), city).filter { it.isNotBlank() }.joinToString(" · ")

/** Nothing for someone in VIP incognito: strangers learn nothing about them. */
@Composable
fun Profile.genderText() = if (isVip) "" else stringResource(gender.label)

/** "Female · Mumbai" */
@Composable
fun Profile.genderCity() = listOf(genderText(), city).filter { it.isNotBlank() }.joinToString(" · ")

/** Opens the phone's video player (signed https URL for received videos, content:// for our own). */
internal fun playVideo(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri.toUri(), "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_video_player), Toast.LENGTH_SHORT).show()
    }
}

/** Centered icon + text for empty lists. */
@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** Blocks screenshots and screen recording while this screen is shown (chat, video, other people's profiles). */
@Composable
fun SecureScreen() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        // Counted, so closing a profile shown over a chat does not unlock the chat underneath.
        if (secureHolders++ == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (--secureHolders == 0) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private var secureHolders = 0

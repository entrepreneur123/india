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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.Profile

// ---- Design tokens ------------------------------------------------------------------------------
// Clean white for the social screens, near-black "ink" for anything camera-like (random chat, video),
// one warm gradient for actions and brand moments. Values match the design mockup's Tailwind tokens.

object Brand {
    val Pink = Color(0xFFFF3D68)
    val Orange = Color(0xFFFF7A45)
    val gradient = Brush.linearGradient(listOf(Pink, Orange))
    val Online = Color(0xFF22C55E)
    val Danger = Color(0xFFE5303F)

    /** Ink: the random-chat and video screens are always this dark, like a camera view. */
    val Night = Color(0xFF0B0B10)
    val Ink2 = Color(0xFF15151C)
    val Ink3 = Color(0xFF1E1E27)

    /** Light neutrals. */
    val Muted = Color(0xFF6E6E7A)
    val Line = Color(0xFFECECF1)
    val Surface = Color(0xFFF5F5F8)
}

private val Light = lightColorScheme(
    primary = Brand.Pink, onPrimary = Color.White,
    secondary = Brand.Night, onSecondary = Color.White,
    background = Color.White, onBackground = Brand.Night,
    surface = Color.White, onSurface = Brand.Night,
    surfaceVariant = Brand.Surface, onSurfaceVariant = Brand.Muted,
    surfaceContainer = Brand.Surface, surfaceContainerHigh = Brand.Surface, surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainerLowest = Color.White, surfaceContainerHighest = Color(0xFFEBEBF0),
    outline = Color(0xFFDADAE0), outlineVariant = Brand.Line,
    error = Brand.Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF5C82), onPrimary = Color.White,
    secondary = Color(0xFFF5F5F7), onSecondary = Brand.Night,
    background = Brand.Night, onBackground = Color(0xFFF5F5F7),
    surface = Brand.Night, onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Brand.Ink3, onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = Brand.Ink2, surfaceContainerHigh = Color(0xFF1F1F27), surfaceContainerLow = Color(0xFF111117),
    surfaceContainerLowest = Brand.Night, surfaceContainerHighest = Color(0xFF282831),
    outline = Color(0xFF3A3A44), outlineVariant = Color(0xFF22222A),
    error = Color(0xFFFF5A5F),
)

/** Plus Jakarta Sans (SIL OFL 1.1, docs/FONT_LICENSE_OFL.txt). Hindi falls back to the system Devanagari font. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

private val Base = Typography()
private fun TextStyle.j(weight: FontWeight? = null, spacing: Float? = null) =
    copy(fontFamily = Jakarta, fontWeight = weight ?: fontWeight, letterSpacing = spacing?.sp ?: letterSpacing)

private val AppType = Typography(
    displayLarge = Base.displayLarge.j(FontWeight.ExtraBold, -1f),
    displayMedium = Base.displayMedium.j(FontWeight.ExtraBold, -0.8f),
    displaySmall = Base.displaySmall.j(FontWeight.ExtraBold, -0.6f),
    headlineLarge = Base.headlineLarge.j(FontWeight.ExtraBold, -0.8f).copy(fontSize = 30.sp, lineHeight = 34.sp),
    headlineMedium = Base.headlineMedium.j(FontWeight.ExtraBold, -0.8f).copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = Base.headlineSmall.j(FontWeight.Bold, -0.5f).copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = Base.titleLarge.j(FontWeight.Bold, -0.3f).copy(fontSize = 20.sp),
    titleMedium = Base.titleMedium.j(FontWeight.Bold, -0.1f),
    titleSmall = Base.titleSmall.j(FontWeight.Bold, 0f).copy(fontSize = 15.sp),
    bodyLarge = Base.bodyLarge.j(spacing = 0f).copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = Base.bodyMedium.j(spacing = 0f).copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Base.bodySmall.j(spacing = 0f).copy(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = Base.labelLarge.j(FontWeight.Bold, 0f).copy(fontSize = 15.sp),
    labelMedium = Base.labelMedium.j(FontWeight.SemiBold, 0f).copy(fontSize = 12.5.sp),
    labelSmall = Base.labelSmall.j(FontWeight.SemiBold, 0.1f).copy(fontSize = 11.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun YaariTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AppType, shapes = AppShapes, content = content)
    }
}

/** The camera-like screens (random chat, video, incoming call) are dark whatever the system theme is. */
@Composable
fun NightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = AppType, shapes = AppShapes, content = content)
}

/** The lowercase brand wordmark, in the accent gradient or plain [color]. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Int = 26, color: Color? = null) {
    val base = TextStyle(fontSize = size.sp, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04 * size).sp)
    Text("yaari", modifier, style = if (color == null) base.copy(brush = Brand.gradient) else base.copy(color = color))
}

/**
 * A soft coloured glow under a button or card (the mockup's `shadow-[0_18px_40px_-14px_brand]`).
 * Drawn with a shadow layer, which hardware rendering supports from Android 9; older phones simply skip it.
 */
fun Modifier.glow(
    color: Color = Brand.Pink, radius: Dp = 22.dp, offsetY: Dp = 12.dp, corner: Dp = 999.dp, inset: Dp = 10.dp, alpha: Float = 0.55f,
): Modifier = drawBehind {
    val paint = Paint().asFrameworkPaint().apply {
        this.color = android.graphics.Color.TRANSPARENT
        setShadowLayer(radius.toPx(), 0f, offsetY.toPx(), color.copy(alpha = alpha).toArgb())
    }
    val i = inset.toPx()
    val r = minOf(corner.toPx(), size.minDimension / 2)
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(i, i, size.width - i, size.height - i, r, r, paint) }
}

/** The main call to action: a full-width gradient pill, optionally glowing and with a light sweep. */
@Composable
fun GradientButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 56.dp,
    leading: (@Composable () -> Unit)? = null, shine: Boolean = false, glow: Boolean = true, shape: Shape = CircleShape,
    corner: Dp = 999.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberTick()
    // Optional light sweep every few seconds on the one button a screen is about (Start, Get Plus).
    val sweep = if (shine && enabled) rememberLoop(2600, from = -0.6f, to = 1.6f, delayMs = 1400, still = -1f).value else null
    Box(
        modifier.fillMaxWidth().height(height).pressScale(interaction, 0.97f)
            .then(if (glow && enabled) Modifier.glow(corner = corner) else Modifier)
            .clip(shape)
            .background(if (enabled) Brand.gradient else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .then(
                if (sweep == null) Modifier else Modifier.drawWithContent {
                    drawContent()
                    val x = size.width * sweep
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent),
                            start = Offset(x - size.height, 0f), end = Offset(x + size.height, size.height),
                        ),
                    )
                },
            )
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) { tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(
                text, color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/** Instagram-style flat button ("Edit profile", "Message"); [accent] fills it with the brand gradient. */
@Composable
fun SoftButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true,
    height: Dp = 40.dp, shape: Shape = RoundedCornerShape(12.dp), outlined: Boolean = false, leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        when {
            accent -> Color.Transparent
            outlined -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "bg",
    )
    Row(
        modifier.height(height).pressScale(interaction).clip(shape)
            .background(bg)
            .then(if (accent) Modifier.background(Brand.gradient) else Modifier)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface,
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
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Round 40 dp icon button on a tinted disc (settings gear, close), with a 48 dp touch target. */
@Composable
fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh, tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp, iconSize: Dp = 20.dp, enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(48.dp).pressScale(interaction, 0.9f)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(iconSize), tint = tint)
        }
    }
}

private val AvatarColors = listOf(0xFFFF8A80, 0xFF82B1FF, 0xFF69D2A0, 0xFFFFC56E, 0xFFC3A6FF, 0xFF5FD0D8, 0xFFFF9EC7)

fun seedColor(seed: Int) = Color(AvatarColors[Math.floorMod(seed, AvatarColors.size)])

/** Photo, or the first letter on a soft colour. [ring] draws the brand gradient ring with a gap (own profile, stories). */
@Composable
fun Avatar(p: Profile, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false, gap: Color = MaterialTheme.colorScheme.background) {
    val inner = if (ring) size - 10.dp else size
    Box(
        modifier.size(size).then(if (ring) Modifier.clip(CircleShape).background(Brand.gradient).padding(2.5.dp).clip(CircleShape).background(gap) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).clip(CircleShape).background(seedColor(p.colorSeed)), contentAlignment = Alignment.Center) {
            if (p.photoUri != null) {
                AsyncImage(p.photoUri, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(p.name.take(1).uppercase(), color = Color.White, fontSize = (inner.value * 0.42f).sp, fontWeight = FontWeight.Bold, fontFamily = Jakarta)
            }
        }
    }
}

/** "Priya 🇮🇳" */
fun Profile.nameWithFlag() = flagOf(country).let { if (it.isEmpty()) name else "$name $it" }

/** "Priya, 22 🇮🇳", the mockup's way of naming someone you meet. */
fun Profile.nameAgeFlag() = flagOf(country).let { if (it.isEmpty()) "$name, $age" else "$name, $age $it" }

@Composable
fun Profile.subtitle() = listOf(age.toString(), stringResource(gender.label), city).filter { it.isNotBlank() }.joinToString(" · ")

/** "Female · Mumbai" */
@Composable
fun Profile.genderCity() = listOf(stringResource(gender.label), city).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
fun Bubble(m: Message) {
    val mine = m.fromMe
    val media = m.media
    // Media from the other person stays hidden until tapped, so nobody sees an unwanted photo by surprise.
    var revealed by rememberSaveable(m.id) { mutableStateOf(mine) }
    val context = LocalContext.current
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (mine) 22.dp else 6.dp, bottomEnd = if (mine) 6.dp else 22.dp)
    val bg = if (mine) Modifier.background(Brand.gradient) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    Box(Modifier.sizeIn(maxWidth = 270.dp).clip(shape).then(bg)) {
        when {
            media != null && !revealed -> Column(
                Modifier.size(176.dp, 220.dp)
                    // Nothing of the photo is loaded until it is tapped: a warm blur-like wash stands in for it.
                    .background(Brush.linearGradient(listOf(Color(0xFF5A3A3C), Color(0xFF2A1E22), Color(0xFF3C2A24))))
                    .clickable(role = Role.Button) { revealed = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    stringResource(R.string.tap_to_view), color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    stringResource(if (media.kind == MediaKind.VIDEO) R.string.video else R.string.photo),
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                )
            }
            media?.kind == MediaKind.IMAGE -> AsyncImage(
                media.uri, contentDescription = stringResource(R.string.photo), contentScale = ContentScale.Crop,
                modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 300.dp),
            )
            media?.kind == MediaKind.VIDEO -> Box(
                Modifier.size(200.dp, 130.dp).background(Color.Black).clickable { playVideo(context, media.uri) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.video), tint = Color.White, modifier = Modifier.size(48.dp))
            }
            else -> Text(
                m.text.orEmpty(), color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Opens the phone's video player (signed https URL for received videos, content:// for our own). */
private fun playVideo(context: Context, uri: String) {
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
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Brand.gradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(26.dp), tint = Color.White)
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
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

package com.yaari.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
// Neutral canvas (white / near-black) like the big social apps, one warm accent for actions and brand moments.

object Brand {
    val Pink = Color(0xFFFF3D68)
    val Orange = Color(0xFFFF7A45)
    val gradient = Brush.linearGradient(listOf(Pink, Orange))
    val Online = Color(0xFF22C55E)
    /** The random-chat screens are always dark, like a camera view. */
    val Night = Color(0xFF0B0B0F)
    val NightCard = Color(0xFF1A1A21)
}

private val Light = lightColorScheme(
    primary = Brand.Pink, onPrimary = Color.White,
    secondary = Color(0xFF111114), onSecondary = Color.White,
    background = Color.White, onBackground = Color(0xFF111114),
    surface = Color.White, onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFF2F2F5), onSurfaceVariant = Color(0xFF6B6B76),
    surfaceContainer = Color(0xFFF7F7F9), surfaceContainerHigh = Color(0xFFEFEFF2), surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainerLowest = Color.White, surfaceContainerHighest = Color(0xFFE9E9ED),
    outline = Color(0xFFD9D9DF), outlineVariant = Color(0xFFEAEAEE),
    error = Color(0xFFE5383B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF5C82), onPrimary = Color.White,
    secondary = Color(0xFFF5F5F7), onSecondary = Color(0xFF111114),
    background = Brand.Night, onBackground = Color(0xFFF5F5F7),
    surface = Brand.Night, onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF1E1E24), onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = Color(0xFF16161B), surfaceContainerHigh = Color(0xFF1E1E24), surfaceContainerLow = Color(0xFF121216),
    surfaceContainerLowest = Brand.Night, surfaceContainerHighest = Color(0xFF26262D),
    outline = Color(0xFF3A3A42), outlineVariant = Color(0xFF2A2A31),
    error = Color(0xFFFF5A5F),
)

private val Base = Typography()
private val AppType = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun YaariTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AppType, shapes = AppShapes, content = content)
}

/** The lowercase brand wordmark in the accent gradient. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Int = 26) {
    Text(
        "yaari", modifier,
        style = TextStyle(brush = Brand.gradient, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    )
}

/** The main call to action: a full-width gradient pill. */
@Composable
fun GradientButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 54.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier.fillMaxWidth().height(height).clip(CircleShape)
            .background(if (enabled) Brand.gradient else Brush.linearGradient(listOf(Color(0xFFBDBDC4), Color(0xFFBDBDC4))))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Instagram-style grey button used on profiles ("Edit profile", "Message"). */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A small pill: room counts, plan badges, filters. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.surfaceContainerHigh, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text, modifier.clip(CircleShape).background(background).padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
        color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}

private val AvatarColors = listOf(0xFFFF8A80, 0xFF82B1FF, 0xFF69D2A0, 0xFFFFC56E, 0xFFC3A6FF, 0xFF5FD0D8, 0xFFFF9EC7)

fun seedColor(seed: Int) = Color(AvatarColors[Math.floorMod(seed, AvatarColors.size)])

/** Photo, or the first letter on a soft color. [ring] draws the brand gradient ring (own profile, stories). */
@Composable
fun Avatar(p: Profile, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false) {
    val inner = if (ring) size - 8.dp else size
    Box(
        modifier.size(size).then(if (ring) Modifier.border(2.5.dp, Brand.gradient, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).clip(CircleShape).background(seedColor(p.colorSeed)), contentAlignment = Alignment.Center) {
            if (p.photoUri != null) {
                AsyncImage(p.photoUri, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(p.name.take(1).uppercase(), color = Color.White, fontSize = (inner.value * 0.42f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "Priya 🇮🇳" */
fun Profile.nameWithFlag() = flagOf(country).let { if (it.isEmpty()) name else "$name $it" }

@Composable
fun Profile.subtitle() = listOf(age.toString(), stringResource(gender.label), city).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
fun Bubble(m: Message) {
    val mine = m.fromMe
    val media = m.media
    // Media from the other person stays hidden until tapped, so nobody sees an unwanted photo by surprise.
    var revealed by rememberSaveable(m.id) { mutableStateOf(mine) }
    val context = LocalContext.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp)
    val bg = if (mine) Modifier.background(Brand.gradient) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    Box(Modifier.sizeIn(maxWidth = 280.dp).clip(shape).then(bg)) {
        when {
            media != null && !revealed -> Column(
                Modifier.size(200.dp, 130.dp).background(Color(0xFF26262D)).clickable { revealed = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.VisibilityOff, null, tint = Color.White.copy(alpha = 0.8f))
                Text(stringResource(if (media.kind == MediaKind.VIDEO) R.string.video else R.string.photo), color = Color.White, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tap_to_view), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            media?.kind == MediaKind.IMAGE -> AsyncImage(
                media.uri, contentDescription = stringResource(R.string.photo), contentScale = ContentScale.Crop,
                modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 300.dp),
            )
            media?.kind == MediaKind.VIDEO -> Box(
                Modifier.size(200.dp, 130.dp).background(Color.Black).clickable { playVideo(context, media.uri) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.video), tint = Color.White, modifier = Modifier.size(48.dp))
            }
            else -> Text(
                m.text.orEmpty(), color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyLarge,
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
            Modifier.size(72.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(34.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

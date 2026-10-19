package com.yaari.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yaari.chat.R
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.model.Profile

private val Brand = Color(0xFFFF5A36)

@Composable
fun YaariTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFFFF8A65), onPrimary = Color(0xFF3B0A00), secondary = Color(0xFFFFB59E))
    } else {
        lightColorScheme(primary = Brand, onPrimary = Color.White, secondary = Color(0xFF8D4E3B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val AvatarColors = listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8, 0xFF4DB6AC, 0xFFF06292)

fun seedColor(seed: Int) = Color(AvatarColors[Math.floorMod(seed, AvatarColors.size)])

@Composable
fun Avatar(p: Profile, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(seedColor(p.colorSeed)),
        contentAlignment = Alignment.Center,
    ) {
        if (p.photoUri != null) {
            AsyncImage(p.photoUri, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(p.name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Profile.subtitle() = "$age · ${stringResource(gender.label)} · $city"

@Composable
fun Bubble(m: Message) {
    val mine = m.fromMe
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val media = m.media
    // Media from the other person stays hidden until tapped, so nobody sees an unwanted photo by surprise.
    var revealed by rememberSaveable(m.id) { mutableStateOf(mine) }
    val context = LocalContext.current
    Box(Modifier.sizeIn(maxWidth = 280.dp).clip(RoundedCornerShape(18.dp)).background(bg)) {
        when {
            media != null && !revealed -> Column(
                Modifier.size(200.dp, 130.dp).background(Color(0xFF2B2B2B)).clickable { revealed = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
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
            else -> Text(m.text.orEmpty(), color = fg, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
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

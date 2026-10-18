package com.yaari.chat.ui

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

fun Profile.subtitle() = "$age · ${gender.label} · $city"

@Composable
fun Bubble(m: Message) {
    val mine = m.fromMe
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.sizeIn(maxWidth = 280.dp).clip(RoundedCornerShape(18.dp)).background(bg)) {
        val media = m.media
        when {
            media?.kind == MediaKind.IMAGE -> AsyncImage(
                media.uri, contentDescription = "Photo", contentScale = ContentScale.Crop,
                modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 300.dp),
            )
            media?.kind == MediaKind.VIDEO -> Column(
                Modifier.size(200.dp, 130.dp).background(Color.Black),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            else -> Text(m.text.orEmpty(), color = fg, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
        }
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

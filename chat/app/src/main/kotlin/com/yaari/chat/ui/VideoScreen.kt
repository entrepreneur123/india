package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yaari.chat.Flavor
import com.yaari.chat.R
import com.yaari.chat.data.VideoToken
import com.yaari.chat.model.Profile
import kotlinx.coroutines.delay

/**
 * Full-bleed video with soft top and bottom scrims, name and timer up top, three round controls below. The one dark
 * screen: white controls on dark scrims, over the video.
 */
@Composable
fun VideoCallScreen(vm: AppViewModel, partner: Profile, me: Profile) {
    SecureScreen()
    LightSystemIcons()
    BackHandler(onBack = vm::endVideo)
    var seconds by remember { mutableIntStateOf(0) }
    var mic by remember { mutableStateOf(true) }
    var cam by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf<VideoToken?>(null) }
    LaunchedEffect(Unit) { token = vm.videoToken() }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    Box(Modifier.fillMaxSize().background(Brand.Video)) {
        Flavor.VideoStage(token, mic, cam, partner, me, vm::flagNudity, Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
        )
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 20.dp, top = 12.dp, end = 140.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(partner, 36.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(partner.nameAgeFlag(), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PresenceDot(6.dp, Brand.Danger)
                    Spacer(Modifier.size(6.dp))
                    Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            // Off states turn the button white, like the mockup's muted mic.
            RoundButton(
                if (mic) Icons.Rounded.Mic else Icons.Rounded.MicOff, stringResource(if (mic) R.string.mute else R.string.unmute),
                if (mic) Color.White.copy(alpha = 0.2f) else Color.White, if (mic) Color.White else Brand.Video,
            ) { mic = !mic }
            RoundButton(Icons.Rounded.CallEnd, stringResource(R.string.end), Brand.Danger, Color.White, size = 72.dp, onClick = vm::endVideo)
            RoundButton(
                if (cam) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, stringResource(if (cam) R.string.camera_off else R.string.camera_on),
                if (cam) Color.White.copy(alpha = 0.2f) else Color.White, if (cam) Color.White else Brand.Video,
            ) { cam = !cam }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, bg: Color, fg: Color, size: Dp = 56.dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size).pressScale(interaction)
            .clip(CircleShape).background(bg)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = fg, modifier = Modifier.size(if (size > 64.dp) 30.dp else 25.dp)) }
}

/** White status and navigation icons while the video is on screen (the rest of the app is white, with dark icons). */
@Composable
private fun LightSystemIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window) {
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = true
            bars.isAppearanceLightNavigationBars = true
        }
    }
}

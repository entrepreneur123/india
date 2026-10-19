package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yaari.chat.Flavor
import com.yaari.chat.R
import com.yaari.chat.data.VideoToken
import com.yaari.chat.model.Profile
import kotlinx.coroutines.delay

@Composable
fun VideoCallScreen(vm: AppViewModel, partner: Profile, me: Profile) {
    SecureScreen()
    BackHandler(onBack = vm::endVideo)
    var seconds by remember { mutableIntStateOf(0) }
    var mic by remember { mutableStateOf(true) }
    var cam by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf<VideoToken?>(null) }
    LaunchedEffect(Unit) { token = vm.videoToken() }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        Flavor.VideoStage(token, mic, cam, partner, me, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.align(Alignment.TopStart).padding(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("${partner.nameWithFlag()} · %d:%02d".format(seconds / 60, seconds % 60), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RoundButton(if (mic) Icons.Filled.Mic else Icons.Filled.MicOff, stringResource(if (mic) R.string.mute else R.string.unmute), Color.White.copy(alpha = if (mic) 0.18f else 0.9f), if (mic) Color.White else Color.Black) { mic = !mic }
                RoundButton(if (cam) Icons.Filled.Videocam else Icons.Filled.VideocamOff, stringResource(if (cam) R.string.camera_off else R.string.camera_on), Color.White.copy(alpha = if (cam) 0.18f else 0.9f), if (cam) Color.White else Color.Black) { cam = !cam }
                RoundButton(Icons.Filled.CallEnd, stringResource(R.string.end), Color(0xFFE5383B), Color.White, onClick = vm::endVideo)
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = fg, modifier = Modifier.size(28.dp)) }
}

package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yaari.chat.Flavor
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

    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        Flavor.VideoStage(token, mic, cam, partner, me, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text(
                "${partner.name} · %d:%02d".format(seconds / 60, seconds % 60), color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
            Row(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton({ mic = !mic }) { Text(if (mic) "Mute" else "Unmute") }
                FilledTonalButton({ cam = !cam }) { Text(if (cam) "Camera off" else "Camera on") }
                Button(vm::endVideo, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("End") }
            }
        }
    }
}

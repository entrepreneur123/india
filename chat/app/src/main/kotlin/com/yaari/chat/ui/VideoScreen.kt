package com.yaari.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yaari.chat.model.Profile
import kotlinx.coroutines.delay

/** Call UI with placeholders where the LiveKit video tracks will render in v2. */
@Composable
fun VideoCallScreen(partner: Profile, me: Profile, onEnd: () -> Unit) {
    SecureScreen()
    BackHandler(onBack = onEnd)
    var seconds by remember { mutableIntStateOf(0) }
    var mic by remember { mutableStateOf(true) }
    var cam by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    Box(Modifier.fillMaxSize().background(Color(0xFF111111)).safeDrawingPadding()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(partner, 160.dp)
            Text(partner.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("%d:%02d".format(seconds / 60, seconds % 60), color = Color.White.copy(alpha = 0.7f))
            Text("Prototype · live video arrives with LiveKit (India region)", color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(96.dp, 128.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
            if (cam) Avatar(me, 56.dp) else Text("Camera off", color = Color.White)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton({ mic = !mic }) { Text(if (mic) "Mute" else "Unmute") }
            FilledTonalButton({ cam = !cam }) { Text(if (cam) "Camera off" else "Camera on") }
            Button(onEnd, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("End") }
        }
    }
}

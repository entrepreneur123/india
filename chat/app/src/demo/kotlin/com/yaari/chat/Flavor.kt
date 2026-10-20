package com.yaari.chat

import androidx.compose.ui.res.stringResource
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yaari.chat.data.Backend
import com.yaari.chat.data.Billing
import com.yaari.chat.data.DemoBilling
import com.yaari.chat.data.FakeBackend
import com.yaari.chat.data.VideoToken
import com.yaari.chat.model.Profile
import com.yaari.chat.ui.Avatar

/** Demo build: everything runs on the phone with scripted partners. */
object Flavor {
    fun backend(app: Application): Backend = FakeBackend()
    /** The demo has no real audio, so it never asks for the microphone. */
    const val voiceMicPermission = false

    fun billing(app: Application): Billing = DemoBilling()

    /** Third-party licenses beyond the font (title to raw text resource): none in the demo. */
    val licenses: List<Pair<String, Int>> = emptyList()

    /** The preview has no partner video, so no nudity check either ([onNudity] is never called). */
    @Composable
    fun VideoStage(token: VideoToken?, mic: Boolean, cam: Boolean, partner: Profile, me: Profile, onNudity: () -> Unit, modifier: Modifier) {
        Box(modifier) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(partner, 160.dp)
                Text(stringResource(R.string.demo_video), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 16.dp).size(108.dp, 152.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1E27))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (cam) Avatar(me, 56.dp) else Text(stringResource(R.string.camera_off), color = Color.White)
            }
        }
    }
}

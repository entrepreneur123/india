package com.yaari.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.DeepLink
import com.yaari.chat.data.FirstRun
import com.yaari.chat.data.LinkBus
import com.yaari.chat.data.TranslatePrefs
import com.yaari.chat.data.MatchPrefs
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme

class MainActivity : ComponentActivity() {
    // Android 12 and older: the language chosen in Settings (Android 13+ handles it in the system).
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            VoiceService.handle(intent)
            LinkBus.app.send(link(intent))
        }
        setContent {
            YaariTheme {
                val vm = viewModel {
                    AppViewModel(
                        Flavor.backend(application), Flavor.billing(application), photos = Flavor.photoCheck(application),
                        firstRun = FirstRun.Prefs(application), push = Flavor.push(application),
                        translatePrefs = TranslatePrefs.Prefs(application), matchPrefs = MatchPrefs.Prefs(application),
                    )
                }
                // Back in the foreground: payments that completed meanwhile (e.g. UPI), refunds, unread counts.
                LifecycleResumeEffect(vm) {
                    vm.resumed()
                    onPauseOrDispose { }
                }
                YaariApp(vm)
            }
        }
    }

    // The voice room notification: open the room, or the host's "end it?" confirm. A room link or a tapped push.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        VoiceService.handle(intent)
        LinkBus.app.send(link(intent))
    }

    /** A room link (https://.../r/<id or code>) or a push's in-app link. */
    private fun link(intent: Intent?): DeepLink? = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.let(DeepLink::parse)

    // Polling (matching, re-syncs, room heartbeat) runs only while the app is visible.
    override fun onStart() {
        super.onStart()
        AppForeground.set(true)
    }

    override fun onStop() {
        AppForeground.set(false)
        super.onStop()
    }
}

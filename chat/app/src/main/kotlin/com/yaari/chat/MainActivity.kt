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
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme

class MainActivity : ComponentActivity() {
    // Android 12 and older: the language chosen in Settings (Android 13+ handles it in the system).
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) VoiceService.handle(intent)
        setContent {
            YaariTheme {
                val vm = viewModel { AppViewModel(Flavor.backend(application), Flavor.billing(application)) }
                // Back in the foreground: pick up payments that completed meanwhile (e.g. UPI).
                LifecycleResumeEffect(vm) {
                    vm.restorePurchases()
                    onPauseOrDispose { }
                }
                YaariApp(vm)
            }
        }
    }

    // The voice room notification: open the room, or the host's "end it?" confirm.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        VoiceService.handle(intent)
    }

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

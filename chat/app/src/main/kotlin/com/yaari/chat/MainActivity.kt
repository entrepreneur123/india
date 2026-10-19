package com.yaari.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yaari.chat.ui.AppViewModel
import com.yaari.chat.ui.YaariApp
import com.yaari.chat.ui.YaariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
}

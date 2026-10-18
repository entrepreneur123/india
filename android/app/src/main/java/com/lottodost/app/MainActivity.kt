package com.lottodost.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.lottodost.app.ui.AppRoot
import com.lottodost.app.ui.LottoTheme

// AppCompatActivity (not ComponentActivity) so per-app language switching works on Android 7-12.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LottoTheme { AppRoot() } }
    }
}

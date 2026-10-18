package com.lottodost.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.lottodost.app.ui.AppRoot
import com.lottodost.app.ui.LottoTheme
import java.util.Locale

// AppCompatActivity (not ComponentActivity) so per-app language switching works on Android 7-12.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep java.util default locale in step with the in-app language (dates, weekday names).
        Locale.setDefault(resources.configuration.locales[0])
        enableEdgeToEdge()
        setContent { LottoTheme { AppRoot() } }
    }
}

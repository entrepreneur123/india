package com.lottodost.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.lottodost.app.R

/** Language tag -> name in its own script (users recognise their script, not the English name). */
val LANGUAGES = listOf(
    "bn" to "বাংলা",
    "hi" to "हिन्दी",
    "en" to "English",
    "ne" to "नेपाली",
    "pa" to "ਪੰਜਾਬੀ",
    "mr" to "मराठी",
)

fun currentLanguage(): String =
    AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')

fun setLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

/**
 * Step 1: pick a language (shown in every script, because the user may not read English).
 * The prompt is written in the three most common languages of Dear lottery players.
 */
@Composable
fun LanguageStep(onChosen: (String) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ভাষা বেছে নিন · भाषा चुनें · Choose language", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            LANGUAGES.forEach { (tag, name) ->
                OutlinedButton(onClick = { onChosen(tag) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(name, fontSize = 22.sp)
                }
            }
        }
    }
}

/** Step 2: what this app is and is not (Play "government information" + misrepresentation policies). */
@Composable
fun DisclaimerStep(onAccept: () -> Unit) {
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAccept() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.disclaimer_body), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.safety_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) else onAccept()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.disclaimer_ok), fontSize = 18.sp)
            }
        }
    }
}

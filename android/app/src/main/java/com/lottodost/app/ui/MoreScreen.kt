package com.lottodost.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.lottodost.app.BuildConfig
import com.lottodost.app.R

private val LANGUAGES = listOf("en" to "English", "hi" to "हिन्दी", "bn" to "বাংলা")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').ifEmpty { "" }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_more), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section(stringResource(R.string.language)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGES.forEach { (tag, name) ->
                    FilterChip(selected = current == tag, onClick = {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }, label = { Text(name) })
                }
            }
        }
        Section(stringResource(R.string.disclaimer_title)) { Text(stringResource(R.string.disclaimer_body)) }
        Section(stringResource(R.string.sources_title)) {
            Text(stringResource(R.string.sources_body))
            TextButton(onClick = { openUrl(ctx, "https://www.nagalandlotteries.com/results.php") }) { Text("nagalandlotteries.com") }
            TextButton(onClick = { openUrl(ctx, "https://www.lotterysambad.com/") }) { Text("lotterysambad.com") }
        }
        Section(stringResource(R.string.claim_title)) { Text(stringResource(R.string.claim_info)) }
        Section(stringResource(R.string.privacy_title)) { Text(stringResource(R.string.privacy_body)) }
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

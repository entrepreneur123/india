package com.yaari.chat.ui

import androidx.compose.ui.res.stringResource
import com.yaari.chat.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Contact details the IT Rules 2021 require an intermediary to publish. Fill these in before launch.
 * (The name must be a real person resident in India once the app is a significant intermediary.)
 */
object Support {
    const val GRIEVANCE_OFFICER = "Grievance Officer, Yaari"
    const val GRIEVANCE_EMAIL = "grievance@yaari.app"
    const val SUPPORT_EMAIL = "support@yaari.app"
    const val PRIVACY_URL = "https://entrepreneur123.github.io/india/yaari-privacy.html"
}

private val Rules = listOf(R.string.rule_1, R.string.rule_2, R.string.rule_3, R.string.rule_4, R.string.rule_5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_safety)) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.community_rules), fontWeight = FontWeight.Bold)
            Rules.forEach { Text(stringResource(it)) }
            Text(
                stringResource(R.string.report_info),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text(stringResource(R.string.grievance_officer), fontWeight = FontWeight.Bold)
            Text("${Support.GRIEVANCE_OFFICER}\n${Support.GRIEVANCE_EMAIL}")
            val uri = LocalUriHandler.current
            TextButton({ uri.openUri(Support.PRIVACY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
            Text(stringResource(R.string.help_email, Support.SUPPORT_EMAIL), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.account), fontWeight = FontWeight.Bold)
            OutlinedButton(vm::signOut, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_out)) }
            OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton({ confirmDelete = false; vm.deleteAccount() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

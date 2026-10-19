package com.yaari.chat.ui

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
}

private val Rules = listOf(
    "Adults only (18+). Anyone who looks under 18 is removed.",
    "No nudity or sexual content, in chat or on video.",
    "No harassment, hate, threats or scams.",
    "Don't share anyone's private photos or details.",
    "Breaking the rules means a permanent ban.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & safety") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Community rules", fontWeight = FontWeight.Bold)
            Rules.forEach { Text("• $it") }
            Text(
                "Use Report in any chat or profile: the person is blocked at once and our team reviews it. " +
                    "Complaints about intimate images or impersonation are acted on within 2 hours.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text("Grievance Officer", fontWeight = FontWeight.Bold)
            Text("${Support.GRIEVANCE_OFFICER}\n${Support.GRIEVANCE_EMAIL}")
            Text("Help: ${Support.SUPPORT_EMAIL}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Account", fontWeight = FontWeight.Bold)
            OutlinedButton(vm::signOut, Modifier.fillMaxWidth()) { Text("Sign out") }
            OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("Your profile, posts, chats and photos are deleted permanently. Premium does not carry over; cancel the subscription in Google Play.") },
            confirmButton = {
                TextButton({ confirmDelete = false; vm.deleteAccount() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

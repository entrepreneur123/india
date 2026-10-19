package com.yaari.chat.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.yaari.chat.R

/**
 * Contact details the IT Rules 2021 (India) require an intermediary to publish. Fill these in before launch.
 * (The name must be a real person resident in India once the app is a significant intermediary.)
 */
object Support {
    const val GRIEVANCE_OFFICER = "Grievance Officer, Yaari"
    const val GRIEVANCE_EMAIL = "grievance@yaari.app"
    const val SUPPORT_EMAIL = "support@yaari.app"
    const val PRIVACY_URL = "https://entrepreneur123.github.io/india/yaari-privacy.html"
}

private val Rules = listOf(R.string.rule_1, R.string.rule_2, R.string.rule_3, R.string.rule_4, R.string.rule_5)

@Composable
fun SettingsScreen(vm: AppViewModel, s: UiState, onBack: () -> Unit, onEditProfile: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    var grievance by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(stringResource(R.string.account))
            Item(
                Icons.Filled.WorkspacePremium, stringResource(R.string.premium_title),
                value = stringResource(
                    when {
                        s.premium -> R.string.plan_premium
                        s.entitlements.genderPass -> R.string.plan_pass
                        else -> R.string.plan_free
                    },
                ),
                tint = Brand.Orange,
            ) { if (!s.premium) vm.showPaywall(Paywall.GENERAL) }
            Item(Icons.Outlined.Person, stringResource(R.string.edit_profile), onClick = onEditProfile)
            // Android 13+ has a per-app language screen (English / हिन्दी).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Item(Icons.Outlined.Language, stringResource(R.string.language)) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()))
                    }
                }
            }

            Group(stringResource(R.string.safety))
            Item(Icons.Outlined.Shield, stringResource(R.string.community_rules), expanded = rules) { rules = !rules }
            AnimatedVisibility(rules) {
                Column(Modifier.padding(start = 56.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Rules.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                    Text(stringResource(R.string.report_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Item(Icons.Outlined.Gavel, stringResource(R.string.grievance_officer), expanded = grievance) { grievance = !grievance }
            AnimatedVisibility(grievance) {
                Text(
                    "${Support.GRIEVANCE_OFFICER}\n${Support.GRIEVANCE_EMAIL}", Modifier.padding(start = 56.dp, end = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Group(stringResource(R.string.about))
            Item(Icons.Outlined.PrivacyTip, stringResource(R.string.privacy_policy)) { uri.openUri(Support.PRIVACY_URL) }
            Item(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help), value = Support.SUPPORT_EMAIL) {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:${Support.SUPPORT_EMAIL}".toUri())) }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Item(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.sign_out), chevron = false, onClick = vm::signOut)
            Item(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_account), chevron = false, color = MaterialTheme.colorScheme.error) { confirmDelete = true }
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

@Composable
private fun Group(title: String) {
    Text(
        title, Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Item(
    icon: ImageVector, label: String, value: String? = null, tint: Color? = null, color: Color? = null,
    chevron: Boolean = true, expanded: Boolean? = null, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = tint ?: color ?: MaterialTheme.colorScheme.onSurface)
        Text(label, Modifier.padding(start = 16.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).then(if (expanded == true) Modifier.rotate(90f) else Modifier),
            )
        }
    }
}

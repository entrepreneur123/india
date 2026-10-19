package com.yaari.chat.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import com.yaari.chat.AppLocale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.yaari.chat.R
import com.yaari.chat.domain.ExitPolicy

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

/** Grouped settings: white rounded groups on a light grey page, with the Plus card on top. */
@Composable
fun SettingsScreen(vm: AppViewModel, s: UiState, onBack: () -> Unit, onEditProfile: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    var grievance by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PlusCard(s) { vm.showPaywall(Paywall.GENERAL) }

            Group(stringResource(R.string.account)) {
                Item(stringResource(R.string.edit_profile), onClick = onEditProfile)
                Line()
                // In-app language choice on every Android version (Android 13+ also shows it in the phone's settings).
                val langTag = remember { AppLocale.current(context) }
                Item(stringResource(R.string.language), value = AppLocale.nameOf(langTag) ?: stringResource(R.string.language_system)) {
                    language = true
                }
            }

            Group(stringResource(R.string.safety)) {
                Item(stringResource(R.string.community_rules), expanded = rules) { rules = !rules }
                AnimatedVisibility(rules) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Rules.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.report_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Line()
                Item(stringResource(R.string.grievance_officer), expanded = grievance) { grievance = !grievance }
                AnimatedVisibility(grievance) {
                    Text(
                        "${Support.GRIEVANCE_OFFICER}\n${Support.GRIEVANCE_EMAIL}", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Group(stringResource(R.string.about)) {
                Item(stringResource(R.string.privacy_policy)) { uri.openUri(Support.PRIVACY_URL) }
                Line()
                Item(stringResource(R.string.help), value = Support.SUPPORT_EMAIL) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:${Support.SUPPORT_EMAIL}".toUri())) }
                }
                Line()
                Item(stringResource(R.string.licenses)) { licenses = true }
            }

            Group(null) {
                IconItem(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.sign_out), onClick = vm::signOut)
                Line()
                IconItem(
                    Icons.Outlined.DeleteOutline, stringResource(R.string.delete_account), sub = stringResource(R.string.delete_sub),
                    color = MaterialTheme.colorScheme.error,
                ) { confirmDelete = true }
            }
        }
    }
    if (language) {
        LanguageDialog(AppLocale.current(context), onDismiss = { language = false }) { tag ->
            language = false
            activity?.let { AppLocale.set(it, tag) }
        }
    }
    if (licenses) {
        val text = remember { context.resources.openRawResource(R.raw.font_license).bufferedReader().use { it.readText() } }
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Plus Jakarta Sans", style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ licenses = false }) { Text(stringResource(R.string.close)) } },
        )
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

/** Tonal brand card: "Get Plus" for free users (opens the paywall), the active plan otherwise. */
@Composable
private fun PlusCard(s: UiState, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val premium = s.premium
    Row(
        Modifier.fillMaxWidth().pressScale(interaction).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interaction, indication = ripple(), enabled = !premium, role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(stringResource(if (premium) R.string.premium_title else R.string.get_plus), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    premium -> stringResource(R.string.plan_premium)
                    s.entitlements.genderPass -> stringResource(R.string.plan_pass)
                    else -> stringResource(R.string.premium_summary, ExitPolicy.PREMIUM_LOCK_MS / 1000.0)
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!premium) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column {
        title?.let {
            Text(
                it, Modifier.padding(start = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface), content = content)
    }
}

@Composable
private fun Line() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun Item(label: String, value: String? = null, expanded: Boolean? = null, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (expanded == true) 90f else 0f, label = "chevron")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        value?.let {
            Text(
                it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(turn),
        )
    }
}

@Composable
private fun IconItem(icon: ImageVector, label: String, sub: String? = null, color: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Phone's language, then each shipped language in its own script. */
@Composable
private fun LanguageDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val options = listOf("" to stringResource(R.string.language_system)) + AppLocale.languages
    val selected = options.firstOrNull { (tag, _) -> tag.isNotEmpty() && AppLocale.nameOf(current) == AppLocale.nameOf(tag) }?.first ?: ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                options.forEach { (tag, name) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                            .selectable(tag == selected, role = Role.RadioButton) { onPick(tag) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(tag == selected, onClick = null, Modifier.padding(horizontal = 8.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

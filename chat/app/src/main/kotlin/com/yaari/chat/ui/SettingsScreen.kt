package com.yaari.chat.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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

/** iOS-style grouped settings: white rounded groups on a soft grey page, with the Plus card on top. */
@Composable
fun SettingsScreen(vm: AppViewModel, s: UiState, onBack: () -> Unit, onEditProfile: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    var grievance by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.back), onBack, background = Color.Transparent, iconSize = 20.dp)
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            PlusCard(s) { vm.showPaywall(Paywall.GENERAL) }

            Group(stringResource(R.string.account)) {
                Item(stringResource(R.string.edit_profile), onClick = onEditProfile)
                // Android 13+ has a per-app language screen (English / हिन्दी).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Line()
                    Item(stringResource(R.string.language)) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()))
                        }
                    }
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

/** Ink card: "Get Plus" for free users (opens the paywall), the active plan otherwise. */
@Composable
private fun PlusCard(s: UiState, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val premium = s.premium
    Row(
        Modifier.fillMaxWidth().pressScale(interaction, 0.98f).clip(RoundedCornerShape(22.dp)).background(Brand.Night)
            .clickable(interaction, indication = null, enabled = !premium, role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brand.gradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = Color.White)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(stringResource(if (premium) R.string.premium_title else R.string.get_plus), color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    premium -> stringResource(R.string.plan_premium)
                    s.entitlements.genderPass -> stringResource(R.string.plan_pass)
                    else -> stringResource(R.string.premium_summary, ExitPolicy.PREMIUM_LOCK_MS / 1000.0)
                },
                color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!premium) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column {
        title?.let {
            Text(
                it, Modifier.padding(start = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface), content = content)
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
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        value?.let {
            Text(
                it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFFC2C2CA),
            modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(turn),
        )
    }
}

@Composable
private fun IconItem(icon: ImageVector, label: String, sub: String? = null, color: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = color ?: MaterialTheme.colorScheme.onSurface)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = (color ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.75f)) }
        }
    }
}

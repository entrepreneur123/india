package com.yaari.chat.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.model.ContactApp
import com.yaari.chat.model.Profile

// Messenger contacts friends exchange (044): my editor, and a friend's contacts with my share switch.

private val BRAND = mapOf(
    ContactApp.WHATSAPP to (0xFF25D366 to 0xFFFFFFFF), ContactApp.TELEGRAM to (0xFF229ED9 to 0xFFFFFFFF),
    ContactApp.INSTAGRAM to (0xFFE4405F to 0xFFFFFFFF), ContactApp.LINE to (0xFF06C755 to 0xFFFFFFFF),
    ContactApp.KAKAOTALK to (0xFFFEE500 to 0xFF191919), ContactApp.WECHAT to (0xFF07C160 to 0xFFFFFFFF),
    ContactApp.SNAPCHAT to (0xFFFFFC00 to 0xFF191919), ContactApp.DISCORD to (0xFF5865F2 to 0xFFFFFFFF),
)

@Composable
private fun ContactApp.label() = brand ?: stringResource(R.string.contacts_phone)

@Composable
private fun AppBadge(app: ContactApp) {
    val (bg, fg) = BRAND[app]?.let { Color(it.first) to Color(it.second) } ?: (Brand.Pink to Color.White)
    Box(Modifier.size(32.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        if (app.brand == null) Icon(Icons.Rounded.Phone, null, Modifier.size(18.dp), tint = fg)
        else Text(app.brand.take(1), color = fg, style = MaterialTheme.typography.titleSmall)
    }
}

/** My contacts, one field per app, checked as I type the way the server does. [onSaved]: what to do next (share). */
@Composable
fun ContactsEditorScreen(vm: AppViewModel, onDone: () -> Unit, onSaved: (Map<ContactApp, String>) -> Unit = {}) {
    BackHandler(onBack = onDone)
    val s by vm.contacts.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.contacts.loadMine(); vm.contacts.clearRefused() }
    var values by remember(s.mine != null) { mutableStateOf(s.mine?.contacts.orEmpty()) }
    val typed = ContactApp.entries.associateWith { values[it].orEmpty() }
    val bad = ContactApp.entries.filter { typed.getValue(it).let(it::clean) == null || it == s.refused }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorBar(stringResource(R.string.contacts_title), onDone, stringResource(R.string.save), s.mine != null && bad.isEmpty() && s.busy == null) {
                vm.contacts.save(typed) { onDone(); onSaved(it) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.contacts_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Shield, null, Modifier.size(20.dp), tint = Brand.Pink)
                    Text(stringResource(R.string.contacts_safety), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(4.dp))
                ContactApp.entries.forEach { app ->
                    Field(
                        typed.getValue(app), { v -> values = values + (app to v.take(if (app.number) 24 else 41)); vm.contacts.clearRefused() },
                        app.label(),
                        keyboard = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                            keyboardType = if (app.number) KeyboardType.Phone else KeyboardType.Ascii,
                        ),
                        error = if (app in bad) stringResource(if (app.number) R.string.contacts_bad_number else R.string.contacts_bad_handle) else null,
                        placeholder = if (app.number) "+91 98765 43210" else "@username",
                        leading = { AppBadge(app) },
                    )
                }
            }
        }
    }
}

/**
 * A friend's contacts (tap opens the app, long-press copies) and whether I show them mine. Nothing while we aren't
 * friends. [onEdit]: I have none saved yet, so sharing starts in my editor.
 */
@Composable
fun FriendContactsSection(vm: AppViewModel, p: Profile, onEdit: () -> Unit, modifier: Modifier = Modifier, title: Boolean = true) {
    val s by vm.contacts.state.collectAsStateWithLifecycle()
    LaunchedEffect(p.id) { vm.contacts.load(p.id) }
    val c = s.of[p.id] ?: return
    val theirs = c.contacts.orEmpty().entries.toList()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title) Text(stringResource(R.string.contacts_section), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        if (theirs.isEmpty()) {
            Text(stringResource(R.string.contacts_none, p.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            theirs.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (app, v) -> ContactChip(app, v, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.contacts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val busy = s.busy == p.id
        if (c.mineShared) {
            Row(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.contacts_sharing, p.name), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                SoftButton(stringResource(R.string.contacts_stop), { vm.contacts.share(p.id, p.name, false) }, enabled = !busy, outlined = true)
            }
        } else {
            val mine = s.mine
            SoftButton(
                stringResource(R.string.contacts_share, p.name),
                { if (mine?.contacts.isNullOrEmpty()) onEdit() else vm.contacts.share(p.id, p.name, true) },
                Modifier.fillMaxWidth(), enabled = mine != null && !busy, height = 44.dp,
                leading = { Icon(Icons.Rounded.ContactPhone, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactChip(app: ContactApp, value: String, modifier: Modifier) {
    val context = LocalContext.current
    val copied = stringResource(R.string.chat_copied)
    val copy = {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(app.code, value))
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
    val open = {
        val link = app.link(value)
        // No app or browser for it: copied instead.
        if (link == null) copy()
        else try { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) } catch (_: ActivityNotFoundException) { copy() }
    }
    Row(
        modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .combinedClickable(role = Role.Button, onLongClick = copy, onClick = open).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppBadge(app)
        Column(Modifier.weight(1f)) {
            Text(app.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The same from a friend's chat: the header's contacts button opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendContactsSheet(vm: AppViewModel, p: Profile, onEdit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(stringResource(R.string.contacts_section), Modifier.padding(horizontal = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        FriendContactsSection(vm, p, onEdit, Modifier.padding(bottom = 16.dp), title = false)
    }
}

/** The contacts button in a friend's chat header, with its sheet and (when I have none yet) my editor over the chat. */
@Composable
fun FriendContactsButton(vm: AppViewModel, p: Profile) {
    var sheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    CircleIconButton(Icons.Rounded.ContactPhone, stringResource(R.string.contacts_section), { sheet = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    if (sheet) FriendContactsSheet(vm, p, onEdit = { sheet = false; editing = true }, onDismiss = { sheet = false })
    if (editing) {
        Dialog({ editing = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            ContactsEditorScreen(vm, onDone = { editing = false }, onSaved = { if (it.isNotEmpty()) vm.contacts.share(p.id, p.name, true) })
        }
    }
}

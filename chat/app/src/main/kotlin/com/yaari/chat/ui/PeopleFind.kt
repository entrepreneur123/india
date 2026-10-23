package com.yaari.chat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.yaari.chat.model.RecentMatch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaari.chat.R
import com.yaari.chat.domain.Handle
import com.yaari.chat.model.Gender
import com.yaari.chat.model.PersonRow
import com.yaari.chat.model.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// Finding someone by @ID (042), and Settings' lists: people I blocked, my recent chats.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleSheet(title: String, sub: String?, onClose: () -> Unit, content: @Composable () -> Unit) = ModalBottomSheet(
    onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Box(Modifier.padding(horizontal = 24.dp)) { SheetTitle(title, sub, onClose) }
        content()
    }
}

@Composable
private fun PersonLine(p: PersonRow, onOpen: (() -> Unit)?, trailing: @Composable () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier).padding(horizontal = 24.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Avatar(Profile(p.id, p.name, Gender.OTHER, 0, "", "", photoUri = p.photoUri, colorSeed = p.id.hashCode()), 44.dp)
    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        LookName(p.id, size = 15.sp, fallback = { Text("@${p.handle}", style = MaterialTheme.typography.titleSmall, maxLines = 1) })
        Text(p.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    trailing()
}

@Composable
private fun Note(text: String) = Text(
    text, Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
)

@Composable
fun FindSheet(vm: AppViewModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Result<List<PersonRow>>?>(null) }
    val asked = query.trim().removePrefix("@")
    LaunchedEffect(asked) {
        if (asked.isEmpty()) return@LaunchedEffect run { found = null }
        delay(300) // typing: one search when it pauses
        found = try { Result.success(vm.findByHandle(asked)) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    PeopleSheet(stringResource(R.string.find_by_id), null, onClose) {
        OutlinedTextField(
            query, { query = it.take(Handle.MAX + 1) }, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            placeholder = { Text(stringResource(R.string.find_placeholder)) }, prefix = { Text("@") }, singleLine = true,
            shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        val list = found?.getOrNull()
        when {
            asked.isEmpty() -> Note(stringResource(R.string.find_hint))
            found == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            list == null -> Note(stringResource(R.string.people_load_failed))
            list.isEmpty() -> Note(stringResource(R.string.find_none, asked))
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
                items(list, key = { it.id }) { p -> PersonLine(p, onOpen = { vm.openProfile(p) }) }
            }
        }
    }
}

@Composable
fun BlockedSheet(vm: AppViewModel, onClose: () -> Unit) {
    var list by remember { mutableStateOf<Result<List<PersonRow>>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        list = try { Result.success(vm.blocks()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    PeopleSheet(stringResource(R.string.blocked_accounts), stringResource(R.string.blocked_note), onClose) {
        val people = list?.getOrNull()
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            people == null -> Note(stringResource(R.string.people_load_failed))
            people.isEmpty() -> Note(stringResource(R.string.blocked_empty))
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(top = 8.dp)) {
                items(people, key = { it.id }) { p ->
                    PersonLine(p, onOpen = null) {
                        SoftButton(stringResource(R.string.unblock), {
                            busy = p.id
                            vm.unblock(p) { ok -> busy = null; if (ok) list = Result.success(people.filter { it.id != p.id }) }
                        }, enabled = busy == null, height = 36.dp)
                    }
                }
            }
        }
    }
}

/** Settings › Recent chats: takes people off my recents (048), some or all; they come back only if we meet again. */
@Composable
fun RecentsSheet(vm: AppViewModel, recents: List<RecentMatch>, onClose: () -> Unit) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshRecents() }
    PeopleSheet(stringResource(R.string.recents_title), stringResource(R.string.recents_note), onClose) {
        if (recents.isEmpty()) Note(stringResource(R.string.recents_none))
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
            items(recents, key = { it.partner.id }) { r ->
                val id = r.partner.id
                val on = id in picked
                Row(
                    Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { picked = if (it) picked + id else picked - id }
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(r.partner, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(r.partner.nameWithFlag(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            DateUtils.getRelativeTimeSpanString(r.metAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    Checkbox(on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Brand.Saffron))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftButton(
                stringResource(R.string.recents_delete_all), { confirm = recents.map { it.partner.id } }, Modifier.weight(1f),
                enabled = recents.isNotEmpty() && !busy, outlined = true,
            )
            SoftButton(
                stringResource(R.string.recents_delete, picked.size), { confirm = picked.toList() }, Modifier.weight(1f),
                accent = true, enabled = picked.isNotEmpty() && !busy,
            )
        }
    }
    confirm?.let { ids ->
        AlertDialog(
            onDismissRequest = { if (!busy) confirm = null },
            title = { Text(stringResource(R.string.recents_confirm)) },
            text = { Text(stringResource(R.string.recents_confirm_body)) },
            confirmButton = {
                TextButton({
                    busy = true
                    vm.hideRecents(ids) { ok ->
                        busy = false
                        confirm = null
                        if (ok) picked = picked - ids.toSet()
                    }
                }, enabled = !busy) { Text(stringResource(R.string.recents_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirm = null }, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        )
    }
}


package com.yaari.chat.ui

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

// Finding someone by @ID (042), and Settings' list of people I blocked.

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

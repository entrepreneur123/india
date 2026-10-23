package com.yaari.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaari.chat.R
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.awaitVisible
import com.yaari.chat.model.RoomCategory
import com.yaari.chat.model.RoomQuery
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/**
 * The Rooms tab: every room is user-created and has chat, voice and music. Enter a code or create one, filter by
 * category, and the live rooms (refreshed every 30 s while on screen, pull to refresh, mine first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsTab(vm: AppViewModel, onSettings: () -> Unit) {
    val list by vm.voice.list.collectAsStateWithLifecycle()
    val inRoom by vm.voice.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(RoomSheet.NONE) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(900); refreshing = false } }
    LaunchedEffect(Unit) {
        while (true) {
            AppForeground.visible.awaitVisible()
            vm.voice.refreshList()
            delay(VoiceController.LIST_REFRESH_MS)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_rooms), onSettings)
        PullToRefreshBox(refreshing, onRefresh = { refreshing = true; vm.voice.refreshList() }, Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    Column {
                        Text(stringResource(R.string.rooms_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftButton(
                                stringResource(R.string.rooms_enter_code), { sheet = RoomSheet.CODE }, Modifier.weight(1f), height = 48.dp, outlined = true,
                                leading = { Icon(Icons.Outlined.Key, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                            )
                            PrimaryButton(
                                stringResource(R.string.rooms_create), { sheet = RoomSheet.CREATE }, Modifier.weight(1.1f), height = 48.dp,
                                leading = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Brand.Black) },
                            )
                        }
                    }
                }
                item(key = "search") { RoomSearch(list.query, vm.voice::search) }
                item(key = "filter") {
                    Segmented(
                        listOf<Pair<RoomCategory?, String>>(null to stringResource(R.string.rooms_all)) +
                            RoomCategory.entries.map { it to stringResource(it.label) },
                        list.filter, Modifier.padding(top = 4.dp), label = stringResource(R.string.rooms_filter), onSelect = vm.voice::filter,
                    )
                }
                item(key = "live") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiveBars(height = 14.dp)
                        Text(
                            stringResource(R.string.voice_live_now), Modifier.padding(start = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.voice_live_hint), Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End, maxLines = 2,
                        )
                    }
                }
                val rooms = list.shown
                when {
                    rooms == null && list.failed -> item(key = "failed") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(Icons.Rounded.GraphicEq, stringResource(R.string.rooms_load_failed), stringResource(R.string.err_network))
                            SoftButton(stringResource(R.string.common_try_again), vm.voice::refreshList, outlined = true)
                        }
                    }
                    rooms == null -> items(3) { Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).shimmer()) }
                    rooms.isEmpty() && !list.query.isEmpty -> item(key = "nomatch") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.rooms_no_match), stringResource(R.string.rooms_no_match_body))
                            PrimaryButton(
                                stringResource(R.string.rooms_create), { sheet = RoomSheet.CREATE }, height = 48.dp,
                                leading = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Brand.Black) },
                            )
                        }
                    }
                    rooms.isEmpty() -> item(key = "none") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(Icons.Rounded.GraphicEq, stringResource(R.string.rooms_none_title), stringResource(R.string.rooms_none_body))
                            PrimaryButton(
                                stringResource(R.string.rooms_create), { sheet = RoomSheet.CREATE }, height = 48.dp,
                                leading = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Brand.Black) },
                            )
                        }
                    }
                    else -> itemsIndexed(rooms, key = { _, r -> r.id }) { i, r ->
                        VoiceRoomCard(r, here = r.id == inRoom?.roomId, modifier = Modifier.enterStagger(i)) { vm.voice.enter(r) }
                    }
                }
            }
        }
    }
    when (sheet) {
        RoomSheet.CREATE -> CreateRoomSheet(list.busy, list.filter, onDismiss = { sheet = RoomSheet.NONE }) { spec ->
            vm.voice.create(spec) { sheet = RoomSheet.NONE }
        }
        RoomSheet.CODE -> JoinCodeSheet(list.busy, onDismiss = { sheet = RoomSheet.NONE }) { code -> vm.voice.joinCode(code) { sheet = RoomSheet.NONE } }
        RoomSheet.NONE -> Unit
    }
}

private enum class RoomSheet { NONE, CREATE, CODE }

/** Search by room name (sent after a short pause in typing) and by the room's language. */
@Composable
private fun RoomSearch(query: RoomQuery, onSearch: (RoomQuery) -> Unit) {
    var text by rememberSaveable { mutableStateOf(query.text) }
    LaunchedEffect(text) {
        if (text == query.text) return@LaunchedEffect
        delay(SEARCH_PAUSE_MS)
        onSearch(query.copy(text = text.trim()))
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { if (it.length <= VoiceController.MAX_SEARCH) text = it }, Modifier.weight(1.25f).heightIn(min = 52.dp), singleLine = true,
            placeholder = { Text(stringResource(R.string.rooms_search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (text.isNotEmpty()) ({
                CircleIconButton(Icons.Rounded.Close, stringResource(R.string.rooms_clear), { text = ""; onSearch(query.copy(text = "")) })
            }) else null,
            shape = CircleShape, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query.copy(text = text.trim())) }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedBorderColor = Brand.Saffron, cursorColor = Brand.Saffron,
            ),
        )
        LanguageSelect(query.lang, { onSearch(query.copy(lang = it, text = text.trim())) }, Modifier.weight(1f), anyLabel = stringResource(R.string.rooms_any_lang), height = 52.dp)
    }
}

private const val SEARCH_PAUSE_MS = 350L

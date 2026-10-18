package com.lottodost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lottodost.app.R
import com.lottodost.app.data.HistoryFeed
import com.lottodost.app.domain.Matcher
import com.lottodost.app.domain.TicketParser

/**
 * "When did this number win?" over the last ~90 days. Lists facts only (date, draw, prize):
 * no frequency charts or "hot numbers", which read as predictions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: HistoryFeed?,
    loading: Boolean,
    onLoad: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onLoad() }

    val ticket = submitted?.let(TicketParser::parseManual)
    val hits = remember(ticket, history) {
        if (ticket == null || history == null) emptyList()
        else history.draws.mapNotNull { d -> Matcher.check(ticket, d)?.takeIf { it.best != null } }
            .sortedWith(compareByDescending<Matcher.Check> { it.draw.date }.thenByDescending { it.draw.time })
    }
    val days = history?.days?.takeIf { it > 0 } ?: 90

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.history_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.history_hint, days), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase().take(12); submitted = null },
                    label = { Text(stringResource(R.string.ticket_number)) },
                    placeholder = { Text("6366") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitted = input; focus.clearFocus() }),
                    isError = submitted != null && ticket == null,
                    supportingText = { if (submitted != null && ticket == null) Text(stringResource(R.string.ticket_invalid)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(onClick = { submitted = input; focus.clearFocus() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_search))
                }
            }
            when {
                history == null && loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                }
                history == null -> item {
                    Column {
                        Text(stringResource(R.string.history_unavailable))
                        TextButton(onClick = onLoad) { Text(stringResource(R.string.retry)) }
                    }
                }
                ticket != null -> {
                    item {
                        Text(
                            if (hits.isEmpty()) stringResource(R.string.history_none, ticket.display(), days)
                            else pluralStringResource(R.plurals.history_count, hits.size, hits.size, days),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (hits.isNotEmpty()) item {
                        Slip {
                            hits.forEachIndexed { i, c ->
                                if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val w = c.best!!
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${formatDate(c.draw.date)} · ${formatTime(c.draw.time)}", fontWeight = FontWeight.SemiBold)
                                        Text(c.draw.name.removeSuffix(" WEEKLY LOTTERY"), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatRupees(w.tier.prize), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(tierTitle(w.tier), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (hits.any { it.best?.needsFullNumber == true }) item {
                        Text(stringResource(R.string.need_full_number), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

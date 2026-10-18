package com.lottodost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.SavedTicket
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.domain.Matcher
import com.lottodost.app.domain.TicketParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckScreen(
    draws: List<Draw>,
    scannedText: String?,
    onScannedConsumed: () -> Unit,
    onScan: () -> Unit,
    onSave: (SavedTicket) -> Unit,
    onOpenDraw: (Draw) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var dateFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var fromScan by rememberSaveable { mutableStateOf(false) }
    var scanFailed by rememberSaveable { mutableStateOf(false) }
    var savedMsg by rememberSaveable { mutableStateOf(false) }
    var saveSlot by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedText) {
        val text = scannedText ?: return@LaunchedEffect
        val ocr = TicketParser.fromOcr(text)
        val best = ocr.tickets.firstOrNull()
        if (best != null) {
            input = best.display()
            dateFilter = ocr.date
            fromScan = true
            scanFailed = false
            // The draw name printed on the ticket (e.g. "WISH") identifies the slot.
            saveSlot = draws.firstOrNull { d -> d.name.split(" ").any { it.length > 3 && it in ocr.words && it != "DEAR" && it != "WEEKLY" && it != "LOTTERY" } }?.slot
            submitted = null // user confirms the number first
        } else {
            scanFailed = true
        }
        onScannedConsumed()
    }

    val ticket = submitted?.let(TicketParser::parseManual)
    val pool = if (dateFilter != null) draws.filter { it.date == dateFilter } else draws
    val checks = ticket?.let { Matcher.checkAll(it, pool) }.orEmpty()

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.check_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.check_hint), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.uppercase().take(12); submitted = null; savedMsg = false },
                label = { Text(stringResource(R.string.ticket_number)) },
                placeholder = { Text("39C 06366") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitted = input; focus.clearFocus() }),
                isError = submitted != null && ticket == null,
                supportingText = {
                    when {
                        submitted != null && ticket == null -> Text(stringResource(R.string.ticket_invalid))
                        fromScan -> Text(stringResource(R.string.scan_confirm))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (scanFailed) Text(stringResource(R.string.scan_none), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = { submitted = input; focus.clearFocus() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.check_button))
                }
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scan_button)) }
            }
            dateFilter?.let { d ->
                FilterChip(selected = true, onClick = { dateFilter = null },
                    label = { Text(stringResource(R.string.only_date, formatDate(d))) }, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (ticket != null) {
            val wins = checks.filter { it.best != null }
            item {
                if (wins.isEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.no_win_title, ticket.display()), fontWeight = FontWeight.Bold)
                            Text(pluralStringResource(R.plurals.no_win_body, checks.size, checks.size))
                        }
                    }
                }
                if (checks.any { it.skippedTiers.isNotEmpty() }) {
                    Text(stringResource(R.string.need_5_digits), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            items(wins, key = { it.draw.id }) { c ->
                val win = c.best!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDraw(c.draw) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (win.needsFullNumber) stringResource(R.string.maybe_first) else stringResource(R.string.you_won),
                            fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text(tierTitle(win.tier) + " · " + formatRupees(win.tier.prize), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${c.draw.name} · ${formatDate(c.draw.date)} ${formatTime(c.draw.time)}", style = MaterialTheme.typography.bodySmall)
                        if (c.wins.size > 1) {
                            val others = c.wins.drop(1).map { tierTitle(it.tier) }.joinToString()
                            Text(stringResource(R.string.also_matches, others), style = MaterialTheme.typography.bodySmall)
                        }
                        if (c.draw.ocr) Text(stringResource(R.string.ocr_caution), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(if (win.tier.prize > 10_000) R.string.claim_info else R.string.claim_small),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item {
                // Save for automatic checking (useful before the draw happens).
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.save_title), fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DrawSchedule.SLOTS.filter { it.format == (if (ticket.digits.length == 4) it.format else "D5") }.forEach { s ->
                                FilterChip(selected = saveSlot == s.id, onClick = { saveSlot = s.id }, label = { Text(s.label) })
                            }
                        }
                        val date = dateFilter ?: rememberNowIst().toLocalDate().toString()
                        Button(enabled = saveSlot != null, onClick = {
                            onSave(SavedTicket(ticket.display(), date, saveSlot!!, System.currentTimeMillis()))
                            savedMsg = true
                        }) { Text(stringResource(R.string.save_button, formatDate(date))) }
                        if (savedMsg) Text(stringResource(R.string.saved_ok), color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

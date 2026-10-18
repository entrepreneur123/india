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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.unit.sp
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.SavedTicket
import com.lottodost.app.data.Upcoming
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.domain.Matcher
import com.lottodost.app.domain.Ticket
import com.lottodost.app.domain.TicketParser

private val GENERIC_WORDS = setOf("DEAR", "WEEKLY", "LOTTERY", "SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY",
    "THURSDAY", "FRIDAY", "SATURDAY", "STATE", "LOTTERIES", "NAGALAND", "SIKKIM", "PUNJAB")

@Composable
fun CheckScreen(
    draws: List<Draw>,
    upcoming: List<Upcoming>,
    today: String,
    scannedText: String?,
    onScannedConsumed: () -> Unit,
    onScan: () -> Unit,
    onSave: (SavedTicket) -> Unit,
    onOpenDraw: (Draw) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var dateFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var fromScan by rememberSaveable { mutableStateOf(false) }
    var scanFailed by rememberSaveable { mutableStateOf(false) }
    var savedMsg by rememberSaveable { mutableStateOf(false) }
    var saveSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var saveDate by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedText) {
        val text = scannedText ?: return@LaunchedEffect
        val ocr = TicketParser.fromOcr(text)
        val best = ocr.tickets.firstOrNull()
        if (best != null) {
            input = best.display()
            dateFilter = ocr.date
            fromScan = true
            scanFailed = false
            // The game name printed on the ticket (e.g. "WISH") identifies the slot.
            saveSlot = DrawSchedule.SLOTS.firstOrNull { s -> s.weekdayNames.any { it.uppercase() in ocr.words } }?.id
                ?: draws.firstOrNull { d -> d.name.split(" ").any { it.length > 3 && it !in GENERIC_WORDS && it in ocr.words } }?.slot
            submitted = null // the user confirms the number first
        } else {
            scanFailed = true
        }
        onScannedConsumed()
    }

    val ticket = submitted?.let(TicketParser::parseManual)
    val pool = if (dateFilter != null) draws.filter { it.date == dateFilter } else draws
    val checks = ticket?.let { Matcher.checkAll(it, pool) }.orEmpty()
    val wins = checks.filter { it.best != null }

    LaunchedEffect(submitted, wins.size) {
        if (submitted != null && wins.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.check_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.check_hint), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.uppercase().take(12); submitted = null; savedMsg = false; fromScan = false },
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
            item {
                if (wins.isEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.no_win_title, ticket.display()), fontWeight = FontWeight.Bold)
                            Text(pluralStringResource(R.plurals.no_win_body, checks.size, checks.size))
                        }
                    }
                }
                if (checks.any { it.skippedTiers.isNotEmpty() }) {
                    Text(stringResource(R.string.need_full_number), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            items(wins, key = { it.draw.id }) { c ->
                val win = c.best!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDraw(c.draw) }
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text((if (win.needsFullNumber) stringResource(R.string.maybe_first) else stringResource(R.string.you_won)) + " 🎉",
                            fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text(tierTitle(win.tier) + " · " + formatRupees(win.tier.prize), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${c.draw.name} · ${formatDate(c.draw.date)} ${formatTime(c.draw.time)}", style = MaterialTheme.typography.bodySmall)
                        if (c.wins.size > 1) {
                            val others = c.wins.drop(1).map { tierTitle(it.tier) }.joinToString()
                            Text(stringResource(R.string.also_matches, others), style = MaterialTheme.typography.bodySmall)
                        }
                        if (c.draw.ocr) Text(stringResource(R.string.ocr_caution), style = MaterialTheme.typography.bodySmall)
                        val claim = when {
                            c.draw.state != "Nagaland" -> R.string.claim_other_state
                            win.tier.prize > 10_000 -> R.string.claim_info
                            else -> R.string.claim_small
                        }
                        Text(stringResource(claim), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item {
                SaveCard(ticket, upcoming, draws, today, dateFilter, saveSlot, saveDate, savedMsg,
                    onPick = { slot, date -> saveSlot = slot; saveDate = date; savedMsg = false },
                    onSave = { onSave(it); savedMsg = true })
            }
        }
    }
}

/** Save for automatic checking (useful before the draw happens). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveCard(
    ticket: Ticket, upcoming: List<Upcoming>, draws: List<Draw>, today: String, dateFilter: String?,
    slot: String?, date: String?, saved: Boolean,
    onPick: (slot: String, date: String?) -> Unit, onSave: (SavedTicket) -> Unit,
) {
    val bumper = ticket.format == Ticket.Format.B6 || ticket.digits.length == 6
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.save_title), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bumper) {
                    // Special draws are identified by date: the next announced ones, plus any already drawn.
                    val dates = (upcoming.filter { it.date >= today }.map { it.date to it.name } +
                        draws.filter { it.special }.map { it.date to it.name }).distinctBy { it.first }.take(4)
                    dates.forEach { (d, name) ->
                        FilterChip(selected = slot == SavedTicket.SPECIAL && date == d, onClick = { onPick(SavedTicket.SPECIAL, d) },
                            label = { Text("${formatDate(d)} · ${name.take(24)}") })
                    }
                } else {
                    DrawSchedule.SLOTS.filter { ticket.digits.length == 4 || it.format == "D5" }.forEach { s ->
                        FilterChip(selected = slot == s.id, onClick = { onPick(s.id, null) }, label = { Text(s.label) })
                    }
                }
            }
            val saveDate = date ?: dateFilter ?: today
            Button(enabled = slot != null, onClick = {
                onSave(SavedTicket(ticket.display(), saveDate, slot!!, System.currentTimeMillis()))
            }) { Text(stringResource(R.string.save_button, formatDate(saveDate))) }
            if (saved) Text(stringResource(R.string.saved_ok), color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

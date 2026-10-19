package com.lottodost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import com.lottodost.app.domain.Bulk
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var many by rememberSaveable { mutableStateOf(false) }
    if (many) {
        BulkCheck(draws, onSingle = { many = false }, onOpenDraw = onOpenDraw, modifier = modifier)
        return
    }
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
            ModeSwitch(many = false, onChange = { many = it })
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
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan_button), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            dateFilter?.let { d ->
                FilterChip(selected = true, onClick = { dateFilter = null },
                    label = { Text(stringResource(R.string.only_date, formatDate(d))) }, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (ticket != null) {
            item {
                if (wins.isEmpty()) {
                    Slip(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
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
                val claim = when {
                    c.draw.state != "Nagaland" -> R.string.claim_other_state
                    win.tier.prize > 10_000 -> R.string.claim_info
                    else -> R.string.claim_small
                }
                WinSlip(
                    title = if (win.needsFullNumber) stringResource(R.string.maybe_first) else stringResource(R.string.you_won),
                    prize = tierTitle(win.tier) + " · " + formatRupees(win.tier.prize),
                    drawLine = "${c.draw.name.removeSuffix(" WEEKLY LOTTERY")} · ${formatDate(c.draw.date)} ${formatTime(c.draw.time)}",
                    notes = listOfNotNull(
                        if (c.wins.size > 1) stringResource(R.string.also_matches, c.wins.drop(1).map { tierTitle(it.tier) }.joinToString()) else null,
                        if (c.draw.ocr) stringResource(R.string.ocr_caution) else null,
                        if (!win.tier.confirmed) stringResource(R.string.tier_unconfirmed) else null,
                        stringResource(claim),
                    ),
                    onClick = { onOpenDraw(c.draw) },
                )
            }
            item {
                TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.history_open)) }
            }
            item {
                SaveCard(ticket, upcoming, draws, today, dateFilter, saveSlot, saveDate, savedMsg,
                    onPick = { slot, date -> saveSlot = slot; saveDate = date; savedMsg = false },
                    onSave = { onSave(it); savedMsg = true })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(many: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SegmentedButton(selected = !many, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
            Text(stringResource(R.string.mode_one))
        }
        SegmentedButton(selected = many, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
            Text(stringResource(R.string.mode_many))
        }
    }
}

/** Result slip for a win: green band with the verdict, then the prize, the draw and what to do next. */
@Composable
fun WinSlip(title: String, prize: String, drawLine: String, notes: List<String>, onClick: (() -> Unit)? = null) {
    Slip(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, onClick = onClick) {
        Surface(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxWidth()) {
            Text(title, color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(prize, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(drawLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

/** Many tickets (a bundle, or a seller's stock) against one chosen draw, with the total won. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkCheck(draws: List<Draw>, onSingle: () -> Unit, onOpenDraw: (Draw) -> Unit, modifier: Modifier) {
    val focus = LocalFocusManager.current
    val ready = draws.filter { it.hasNumbers }.take(12)
    var text by rememberSaveable { mutableStateOf("") }
    var drawId by rememberSaveable { mutableStateOf(ready.firstOrNull()?.id) }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    val draw = ready.firstOrNull { it.id == drawId }
    val parsed = submitted?.let(Bulk::parse)
    val result = if (parsed != null && draw != null) Bulk.check(parsed.tickets, draw) else null

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.check_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ModeSwitch(many = true, onChange = { if (!it) onSingle() })
            Text(stringResource(R.string.bulk_hint), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Eyebrow(stringResource(R.string.bulk_draw))
            // One scrollable row: the newest draw is first and pre-selected.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ready, key = { it.id }) { d ->
                    FilterChip(selected = d.id == drawId, onClick = { drawId = d.id },
                        label = { Text("${formatDate(d.date)} · ${formatTime(d.time)}") })
                }
            }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(4000); submitted = null },
                label = { Text(stringResource(R.string.bulk_label)) },
                placeholder = { Text("39C 06300-06399\n6366, 1873") },
                minLines = 4,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { submitted = text; focus.clearFocus() }, enabled = draw != null && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.check_button)) }
        }
        if (parsed != null && result != null && draw != null) {
            item {
                Slip(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, onClick = { onOpenDraw(draw) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(draw.name.removeSuffix(" WEEKLY LOTTERY") + " · " + formatDate(draw.date) + " " + formatTime(draw.time),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.bulk_summary, result.checked, result.winners.size, formatRupees(result.total)),
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (result.winners.isEmpty()) Text(stringResource(R.string.bulk_none))
                        if (parsed.tooMany) Text(stringResource(R.string.bulk_too_many, Bulk.MAX_TICKETS), color = MaterialTheme.colorScheme.error)
                        if (parsed.invalid.isNotEmpty()) Text(stringResource(R.string.bulk_invalid, parsed.invalid.take(5).joinToString(", ")),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (result.winners.isNotEmpty()) item {
                Slip {
                    result.winners.forEachIndexed { i, line ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(line.ticket.display(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatRupees(line.win.tier.prize) + if (line.sure) "" else " ?",
                                    color = if (line.sure) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold)
                                Text(tierTitle(line.win.tier), style = MaterialTheme.typography.bodySmall)
                                if (line.maybeHigher) Text(stringResource(R.string.need_full_short), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
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
    Slip {
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

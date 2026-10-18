package com.lottodost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.SavedTicket
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.domain.Matcher
import com.lottodost.app.domain.TicketParser

@Composable
fun TicketsScreen(
    tickets: List<SavedTicket>,
    draws: List<Draw>,
    onRemove: (SavedTicket) -> Unit,
    onOpenDraw: (Draw) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.tickets_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tickets_hint), style = MaterialTheme.typography.bodyMedium)
        }
        if (tickets.isEmpty()) item { Text(stringResource(R.string.tickets_empty), modifier = Modifier.padding(top = 24.dp)) }
        items(tickets, key = { it.key }) { t ->
            val ticket = TicketParser.parseManual(t.number)
            val checks = if (ticket == null) emptyList() else t.findDraws(draws).mapNotNull { Matcher.check(ticket, it) }
            val bestCheck = checks.maxByOrNull { it.best?.tier?.prize ?: -1L }
            val best = bestCheck?.best
            val draw = bestCheck?.draw ?: t.findDraws(draws).firstOrNull()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (best != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().then(if (draw != null) Modifier.clickable { onOpenDraw(draw) } else Modifier),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.number, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        val label = if (t.slot == SavedTicket.SPECIAL) stringResource(R.string.special_tag)
                        else DrawSchedule.slot(t.slot)?.label ?: t.slot
                        Text("$label · ${formatDate(t.date)}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            when {
                                draw == null -> stringResource(R.string.ticket_waiting)
                                best != null -> stringResource(R.string.ticket_won, tierTitle(best.tier), formatRupees(best.tier.prize))
                                else -> stringResource(R.string.ticket_no_prize)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = { onRemove(t) }) { Text(stringResource(R.string.remove)) }
                }
            }
        }
    }
}

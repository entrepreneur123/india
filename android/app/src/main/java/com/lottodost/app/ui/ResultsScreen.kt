package com.lottodost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.domain.DrawSchedule
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

@Composable
fun rememberNowIst(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now(DrawSchedule.IST)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = ZonedDateTime.now(DrawSchedule.IST)
        }
    }
    return now
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    state: ResultRepository.State,
    onOpen: (Draw) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNowIst()
    val today = now.toLocalDate().toString()
    val draws = state.feed?.draws.orEmpty()
    val dates = (listOf(today) + draws.map { it.date }).distinct().sortedDescending()
    var selected by rememberSaveable { mutableStateOf(today) }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.results_subtitle), style = MaterialTheme.typography.bodyMedium)
                if (state.offline) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.offline_banner), Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dates) { d ->
                        FilterChip(
                            selected = d == selected,
                            onClick = { selected = d },
                            label = { Text(if (d == today) stringResource(R.string.today) else formatDate(d)) },
                        )
                    }
                }
            }
            val isToday = selected == today
            items(DrawSchedule.SLOTS, key = { it.id }) { slot ->
                val draw = draws.firstOrNull { it.date == selected && it.slot == slot.id }
                SlotCard(slot, draw, if (isToday) now else null, onOpen)
            }
            item {
                Text(stringResource(R.string.source_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SlotCard(slot: DrawSchedule.Slot, draw: Draw?, nowIfToday: ZonedDateTime?, onOpen: (Draw) -> Unit) {
    val ctx = LocalContext.current
    val clickable = draw?.hasNumbers == true
    Card(
        modifier = Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable { onOpen(draw!!) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text(formatTime(slot.drawTime.toString()), color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(draw?.name?.takeIf { draw.status == "OK" } ?: slot.label.uppercase(),
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.state_lottery, slot.state), style = MaterialTheme.typography.bodySmall)
                }
                if (draw?.verified == true) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.verified)) })
                }
            }
            Spacer(Modifier.padding(4.dp))
            when {
                draw != null && draw.hasNumbers -> {
                    Text(stringResource(R.string.first_prize), style = MaterialTheme.typography.labelMedium)
                    Text(draw.firstPrize.orEmpty(), fontSize = 34.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.tap_full_result), style = MaterialTheme.typography.bodySmall)
                }
                draw != null && draw.officialPdf != null -> {
                    Text(stringResource(R.string.pdf_only), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { openUrl(ctx, draw.officialPdf!!) }) {
                        Text(stringResource(R.string.open_official_pdf))
                    }
                }
                nowIfToday != null -> {
                    when (val st = DrawSchedule.status(slot, nowIfToday, false)) {
                        is DrawSchedule.Status.Upcoming -> {
                            val h = st.minutesLeft / 60
                            val mm = st.minutesLeft % 60
                            Text(stringResource(R.string.draw_in, h, mm), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                        }
                        else -> Text(stringResource(R.string.result_coming), style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> Text(stringResource(R.string.no_result), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

package com.lottodost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottodost.app.BuildConfig
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.domain.DrawSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    state: ResultRepository.State,
    now: ZonedDateTime,
    onOpen: (Draw) -> Unit,
    onRefresh: () -> Unit,
    onLoadDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val today = now.toLocalDate().toString()
    val draws = state.draws.values
    val known = (listOf(today) + state.index?.dates.orEmpty() + draws.map { it.date }).distinct().sortedDescending()
    val chipDates = known.take(7)
    var selected by rememberSaveable { mutableStateOf(today) }
    var picking by remember { mutableStateOf(false) }

    fun select(d: String) {
        selected = d
        if (draws.none { it.date == d }) onLoadDay(d)
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.results_subtitle), style = MaterialTheme.typography.bodyMedium)
                Freshness(state, now)
            }
            state.config?.let { cfg ->
                if (BuildConfig.VERSION_CODE < cfg.latestVersionCode) item {
                    Banner(stringResource(if (BuildConfig.VERSION_CODE < cfg.minVersionCode) R.string.update_required else R.string.update_available),
                        action = stringResource(R.string.update_button)) {
                        openUrl(ctx, "https://play.google.com/store/apps/details?id=${ctx.packageName}")
                    }
                }
                cfg.announcement?.let { a -> item { Banner(a.text, action = a.url?.let { stringResource(R.string.open) }) { a.url?.let { openUrl(ctx, it) } } } }
            }
            state.index?.upcoming?.firstOrNull { it.date >= today && LocalDate.parse(it.date) <= now.toLocalDate().plusDays(14) }?.let { up ->
                item {
                    Banner(stringResource(R.string.upcoming_special, up.name, formatDate(up.date)), highlight = true)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chipDates) { d ->
                        FilterChip(selected = d == selected, onClick = { select(d) },
                            label = { Text(if (d == today) stringResource(R.string.today) else formatDate(d)) })
                    }
                    item {
                        FilterChip(selected = selected !in chipDates, onClick = { picking = true },
                            label = { Text(if (selected !in chipDates) formatDate(selected) else stringResource(R.string.more_dates)) })
                    }
                }
            }
            if (state.loadingDay == selected) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
            }
            if (selected in state.index?.noDraw.orEmpty()) item {
                Banner(stringResource(R.string.no_draw_day))
            } else {
                val isToday = selected == today
                items(DrawSchedule.SLOTS, key = { it.id }) { slot ->
                    val draw = draws.firstOrNull { it.date == selected && it.slot == slot.id }
                    SlotCard(slot, draw, selected, if (isToday) now else null, state.config?.liveDrawUrl, onOpen)
                }
                val specials = draws.filter { it.date == selected && it.special }.sortedBy { it.time }
                items(specials, key = { it.id }) { SpecialCard(it, onOpen) }
            }
            item {
                Text(stringResource(R.string.source_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (picking) {
        val allowed = state.index?.dates.orEmpty().toSet() + draws.map { it.date }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(selected).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().toString() in allowed
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        select(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

@Composable
private fun Freshness(state: ResultRepository.State, now: ZonedDateTime) {
    val text = when {
        state.refreshing -> stringResource(R.string.checking)
        state.lastCheckedMs == 0L -> null
        else -> {
            val mins = ((now.toInstant().toEpochMilli() - state.clockOffsetMs - state.lastCheckedMs) / 60_000).coerceAtLeast(0)
            if (mins < 1) stringResource(R.string.updated_just_now) else stringResource(R.string.updated_min_ago, mins)
        }
    }
    text?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (state.offline) Banner(stringResource(R.string.offline_banner))
}

@Composable
fun Banner(text: String, highlight: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Colloquial names players search for ("morning lottery sambad"): the old official slot names. */
@Composable
fun slotTag(slotId: String): String? = when (slotId) {
    "NL1PM" -> stringResource(R.string.tag_morning)
    "SK6PM" -> stringResource(R.string.tag_day)
    "NL8PM" -> stringResource(R.string.tag_night)
    else -> null
}

@Composable
private fun SlotCard(slot: DrawSchedule.Slot, draw: Draw?, date: String, nowIfToday: ZonedDateTime?, liveUrl: String?, onOpen: (Draw) -> Unit) {
    val ctx = LocalContext.current
    val clickable = draw?.hasNumbers == true
    Card(
        modifier = Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable { onOpen(draw!!) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatTime(slot.drawTime.toString()), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        slotTag(slot.id)?.let { Text(it, color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    // Before the result is in, show the scheduled game name for that weekday.
                    val expected = slot.gameName(LocalDate.parse(date).dayOfWeek.value)
                    // "WEEKLY LOTTERY" is on every weekly name; dropping it keeps the game name readable at large font sizes.
                    Text(draw?.name?.takeIf { draw.status == "OK" }?.removeSuffix(" WEEKLY LOTTERY") ?: expected,
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.state_lottery, slot.state), style = MaterialTheme.typography.bodySmall)
                }
                if (draw?.verified == true) AssistChip(onClick = {}, label = { Text(stringResource(R.string.verified)) })
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
                    OutlinedButton(onClick = { openUrl(ctx, draw.officialPdf!!) }) { Text(stringResource(R.string.open_official_pdf)) }
                }
                nowIfToday != null -> when (val st = DrawSchedule.status(slot, nowIfToday, false)) {
                    is DrawSchedule.Status.Upcoming -> Text(
                        stringResource(R.string.draw_in, st.minutesLeft / 60, st.minutesLeft % 60),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DrawSchedule.Status.Live -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.error, shape = CircleShape) {
                            Text(stringResource(R.string.live), color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (liveUrl != null) Button(onClick = { openUrl(ctx, liveUrl) },
                            colors = ButtonDefaults.buttonColors()) { Text(stringResource(R.string.watch_live)) }
                    }
                    DrawSchedule.Status.Delayed -> Column {
                        Text(stringResource(R.string.result_delayed), style = MaterialTheme.typography.bodyMedium)
                        if (liveUrl != null) OutlinedButton(onClick = { openUrl(ctx, liveUrl) }) { Text(stringResource(R.string.watch_live)) }
                    }
                    else -> Text(stringResource(R.string.result_coming), style = MaterialTheme.typography.titleMedium)
                }
                else -> Text(stringResource(R.string.no_result), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SpecialCard(draw: Draw, onOpen: (Draw) -> Unit) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().then(if (draw.hasNumbers) Modifier.clickable { onOpen(draw) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.special_tag), color = MaterialTheme.colorScheme.onSecondary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(draw.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    val state = if (draw.state.isBlank()) null else stringResource(R.string.state_lottery, draw.state)
                    Text(listOfNotNull(formatTime(draw.time), state).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.padding(4.dp))
            if (draw.hasNumbers) {
                Text(stringResource(R.string.first_prize) + " · " + prizeShort(draw.tiers.first().prize), style = MaterialTheme.typography.labelMedium)
                Text(draw.firstPrize.orEmpty(), fontSize = 34.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.tap_full_result), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.sheet_only), style = MaterialTheme.typography.bodyMedium)
                draw.sheetUrl?.let { url -> OutlinedButton(onClick = { openUrl(ctx, url) }) { Text(stringResource(R.string.open_sheet)) } }
            }
        }
    }
}

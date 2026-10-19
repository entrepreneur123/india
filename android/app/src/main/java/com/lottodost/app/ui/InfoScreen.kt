package com.lottodost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lottodost.app.BuildConfig
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.Prefs
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.domain.DrawSchedule
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

const val PRIVACY_URL = "https://entrepreneur123.github.io/india/privacy.html"
/** Public results page: the link people can open without the app (SMS, forwards). */
const val WEB_URL = "https://entrepreneur123.github.io/india/"
// Official page with the current form (the PDF file name changes when the form is revised).
const val CLAIM_FORM_URL = "https://www.nagalandlotteries.com/prize-claim-form.php"

/** Everything a player asks about, in one place: schedule, prizes, how to read a ticket, claims, safety. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoScreen(state: ResultRepository.State, now: ZonedDateTime, onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val draws = state.draws.values
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.info_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_open)) }

        Section(stringResource(R.string.info_schedule)) {
            val dow = now.dayOfWeek.value
            DrawSchedule.SLOTS.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(s.drawTime.toString()), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.gameName(dow), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.info_slot_meta, stateName(s.state), s.price, if (s.format == "D4") "12 3456" else "39C 06366"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(stringResource(R.string.info_schedule_note), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.info_week_names)) {
            val locale = Locale.getDefault()
            Row {
                Text("", Modifier.weight(1.1f))
                DrawSchedule.SLOTS.forEach { Text(formatTime(it.drawTime.toString()).replace(":00", "") + if (it.format == "D4") " L" else "",
                    Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
            }
            DayOfWeek.entries.forEach { d ->
                Row {
                    Text(d.getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (d.value == now.dayOfWeek.value) FontWeight.Bold else FontWeight.Normal)
                    DrawSchedule.SLOTS.forEach { s ->
                        Text(s.weekdayNames[d.value - 1], Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (d.value == now.dayOfWeek.value) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Text(stringResource(R.string.info_laxmi_legend), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.info_prizes)) {
            // Taken from the latest real result of each game, so it is always the current scheme.
            DrawSchedule.SLOTS.filter { it.id != "SK6PM" }.forEach { s ->
                val latest: Draw? = draws.filter { it.slot == s.id && it.hasNumbers }.maxByOrNull { it.date }
                Text(if (s.format == "D4") "Dear Laxmi (4 digits) · ₹${s.price}" else "Dear 1 PM / 6 PM / 8 PM · ₹${s.price}",
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                if (latest == null) {
                    Text(stringResource(R.string.info_prizes_pending), style = MaterialTheme.typography.bodySmall)
                } else latest.tiers.forEach { t ->
                    Row {
                        Text(tierTitle(t), Modifier.weight(1f))
                        Text(formatRupees(t.prize), fontWeight = FontWeight.SemiBold)
                    }
                    matchHint(t)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }

        Section(stringResource(R.string.info_ticket_title)) {
            Text("39C 06366", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.info_ticket_body))
        }

        state.index?.upcoming?.filter { it.date >= now.toLocalDate().toString() }?.takeIf { it.isNotEmpty() }?.let { up ->
            Section(stringResource(R.string.info_upcoming)) {
                up.forEach { Text("${formatDate(it.date)} · ${it.name}") }
            }
        }

        Section(stringResource(R.string.claim_title)) {
            Text(stringResource(R.string.claim_info))
            Text(stringResource(R.string.claim_docs_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Text(stringResource(R.string.claim_docs_body))
            TextButton(onClick = { openUrl(ctx, state.config?.claimFormUrl ?: CLAIM_FORM_URL) }) {
                Text(stringResource(R.string.claim_form))
            }
        }
        Section(stringResource(R.string.safety_title)) { Text(stringResource(R.string.safety_body)) }

        Section(stringResource(R.string.sources_title)) {
            Text(stringResource(R.string.sources_body))
            TextButton(onClick = { openUrl(ctx, state.config?.officialResultsUrl ?: "https://www.nagalandlotteries.com/results.php") }) {
                Text(stringResource(R.string.official_results))
            }
            TextButton(onClick = { openUrl(ctx, state.config?.liveDrawUrl ?: "https://www.nagalandlotteries.com/livedraw/index.php") }) {
                Text(stringResource(R.string.watch_live))
            }
        }

        Section(stringResource(R.string.settings)) {
            Text(stringResource(R.string.language), fontWeight = FontWeight.SemiBold)
            val current = currentLanguage()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGES.forEach { (tag, name) ->
                    FilterChip(selected = current == tag, onClick = { setLanguage(tag) }, label = { Text(name) })
                }
            }
            var notify by remember { mutableStateOf(Prefs.notificationsEnabled(ctx)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notify_results), Modifier.weight(1f))
                Switch(checked = notify, onCheckedChange = { notify = it; Prefs.setNotificationsEnabled(ctx, it) })
            }
            if (notify) {
                Text(stringResource(R.string.alerts_for), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val keys = DrawSchedule.SLOTS.map { it.id to it.label } + ("KL" to stringResource(R.string.lottery_kerala)) +
                        ("SPECIAL" to stringResource(R.string.alerts_specials))
                    keys.forEach { (key, label) ->
                        var on by remember(key) { mutableStateOf(Prefs.drawAlert(ctx, key)) }
                        FilterChip(selected = on, onClick = { on = !on; Prefs.setDrawAlert(ctx, key, on) }, label = { Text(label) })
                    }
                }
            }
        }

        Section(stringResource(R.string.disclaimer_title)) { Text(stringResource(R.string.disclaimer_body)) }
        Section(stringResource(R.string.privacy_title)) {
            Text(stringResource(R.string.privacy_body))
            TextButton(onClick = { openUrl(ctx, PRIVACY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
        }
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Slip {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

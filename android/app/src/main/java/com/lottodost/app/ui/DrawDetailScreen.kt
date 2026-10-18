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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.Tier

@Composable
fun tierTitle(tier: Tier): String = when (tier.rank) {
    "CONS" -> stringResource(R.string.tier_cons)
    "1" -> stringResource(R.string.tier_1)
    "2" -> stringResource(R.string.tier_2)
    "3" -> stringResource(R.string.tier_3)
    "4" -> stringResource(R.string.tier_4)
    "5" -> stringResource(R.string.tier_5)
    else -> tier.rank
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DrawDetailScreen(draw: Draw, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.filter(Char::isDigit)
    val shareBody = buildShareText(draw, stringResource(R.string.share_footer))

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(formatDate(draw.date) + " · " + formatTime(draw.time)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
            },
            actions = {
                IconButton(onClick = { shareText(ctx, shareBody) }) {
                    Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share))
                }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(draw.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.draw_meta, draw.state, draw.drawNo?.toString() ?: "-", draw.price),
                    style = MaterialTheme.typography.bodyMedium)
                if (draw.ocr) Banner(stringResource(R.string.ocr_caution))
                else if (draw.verified) Banner(stringResource(R.string.verified_long))
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(5) },
                    label = { Text(stringResource(R.string.find_in_result)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(draw.tiers, key = { it.rank }) { tier ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(tierTitle(tier), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(formatRupees(tier.prize), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp)) {
                            tier.numbers.forEach { n ->
                                // Same rules as Matcher: lower tiers compare last 4, others need all 5 digits.
                                val hit = when (tier.match) {
                                    "LAST4" -> q.length >= 4 && n == q.takeLast(4)
                                    "FULL" -> q.length == 5 && n.endsWith(q)
                                    else -> q.length == 5 && n == q
                                }
                                Surface(
                                    color = if (hit) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text(n, fontFamily = FontFamily.Monospace, fontSize = if (tier.match == "FULL") 26.sp else 17.sp,
                                        fontWeight = if (hit || tier.match == "FULL") FontWeight.Black else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draw.officialPdf?.let { url ->
                        OutlinedButton(onClick = { openUrl(ctx, url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_official_pdf))
                        }
                    }
                    Text(stringResource(R.string.claim_info), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
        Text(text, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun buildShareText(draw: Draw, footer: String): String {
    val lines = mutableListOf("${draw.name} — ${formatDate(draw.date)} ${formatTime(draw.time)}")
    for (t in draw.tiers) {
        lines += "${tierTitle(t)} (${formatRupees(t.prize)}): ${t.numbers.joinToString(" ")}"
    }
    lines += footer
    return lines.joinToString("\n")
}

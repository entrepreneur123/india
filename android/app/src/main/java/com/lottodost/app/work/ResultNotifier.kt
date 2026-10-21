package com.lottodost.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lottodost.app.MainActivity
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.Prefs
import com.lottodost.app.data.TicketStore
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.domain.Matcher
import com.lottodost.app.domain.TicketParser
import com.lottodost.app.ui.formatRupees
import com.lottodost.app.widget.ResultsWidget
import java.time.ZonedDateTime

/**
 * "Result is out" and saved-ticket notifications. Shared by the scheduled worker and the push
 * service, which can both see the same new result: a draw is announced once (the "notified" set),
 * and a ticket once (TicketStore.markNotified), whichever path gets there first.
 */
object ResultNotifier {
    const val CHANNEL = "results"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        // HIGH: a result is time-sensitive and it is the reason people install the app (heads-up banner).
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @Synchronized
    fun notifyAll(ctx: Context, draws: List<Draw>) {
        if (draws.isEmpty()) return
        ResultsWidget.update(ctx)
        notifyNewDraws(ctx, draws)
        notifySavedTickets(ctx, draws)
    }

    private fun notifyNewDraws(ctx: Context, draws: List<Draw>) {
        val prefs = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("draws", null)
        // Only today's and yesterday's results are news; history loaded by the user is not.
        val yesterday = ZonedDateTime.now(DrawSchedule.IST).toLocalDate().minusDays(1).toString()
        val ready = draws.filter { it.hasNumbers && it.date >= yesterday }
        if (seen == null) { // first run after install: don't flood with old results
            prefs.edit().putStringSet("draws", ready.map { it.id }.toSet()).apply()
            return
        }
        val pushed = prefs.getStringSet("pushed", emptySet())!!   // already shown from a push message
        val fresh = ready.filter { it.id !in seen && it.id !in pushed }
        if (Prefs.notificationsEnabled(ctx)) {
            fresh.filter { Prefs.drawAlert(ctx, Prefs.alertKey(it.slot, it.special, it.lottery)) }.forEach { d ->
                post(ctx, d.id.hashCode(), ctx.getString(R.string.notif_result_title, d.name),
                    ctx.getString(R.string.notif_result_body, d.firstPrize ?: ""))
            }
        }
        // Keep only ids still in the feed so the set stays small.
        val keep = (seen intersect draws.map { it.id }.toSet()) + fresh.map { it.id }
        if (keep != seen) prefs.edit().putStringSet("draws", keep).apply()
    }

    /**
     * A result notification straight from a push message (name and 1st prize), before the feed is
     * downloaded. The same draw is not announced again when the feed arrives.
     */
    @Synchronized
    fun announce(ctx: Context, id: String, key: String, name: String, firstPrize: String) {
        val prefs = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE)
        val yesterday = ZonedDateTime.now(DrawSchedule.IST).toLocalDate().minusDays(1).toString()
        if (id.take(10) < yesterday) return   // a delayed message about an old result is not news
        val seen = prefs.getStringSet("draws", emptySet())!!
        val pushed = prefs.getStringSet("pushed", emptySet())!!
        if (id in seen || id in pushed) return
        if (Prefs.notificationsEnabled(ctx) && (key.isEmpty() || Prefs.drawAlert(ctx, key))) {
            post(ctx, id.hashCode(), ctx.getString(R.string.notif_result_title, name), ctx.getString(R.string.notif_result_body, firstPrize))
        }
        // Remember the last two days only; ids start with their date.
        prefs.edit().putStringSet("pushed", pushed.filter { it.take(10) >= yesterday }.toSet() + id).apply()
    }

    private fun notifySavedTickets(ctx: Context, draws: List<Draw>) {
        val store = TicketStore.get(ctx)
        val done = mutableSetOf<String>()
        for (t in store.tickets.value.filter { !it.notified }) {
            val candidates = t.findDraws(draws)
            if (candidates.isEmpty()) continue
            val ticket = TicketParser.parseManual(t.number) ?: continue
            val checks = candidates.mapNotNull { Matcher.check(ticket, it) }
            val bestCheck = checks.maxByOrNull { it.best?.tier?.prize ?: -1L }
            val draw = bestCheck?.draw ?: candidates.first()
            val best = bestCheck?.best
            val body = if (best != null)
                ctx.getString(R.string.notif_ticket_won, t.number, formatRupees(best.tier.prize))
            else ctx.getString(R.string.notif_ticket_lost, t.number)
            post(ctx, t.key.hashCode(), draw.name, body)
            done += t.key
        }
        if (done.isNotEmpty()) store.markNotified(done)
    }

    private fun post(ctx: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

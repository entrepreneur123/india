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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lottodost.app.MainActivity
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.Prefs
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.data.TicketStore
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.domain.Matcher
import com.lottodost.app.domain.TicketParser
import com.lottodost.app.ui.formatRupees
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Wakes shortly after each draw (instead of polling all day) to fetch results, post a
 * "result out" notification and check the user's saved tickets.
 */
class ResultCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = ResultRepository.get(applicationContext)
        repo.refresh(ResultRepository.Mode.FULL, force = true)
        val draws = repo.state.value.drawList
        if (draws.isNotEmpty()) {
            if (Prefs.notificationsEnabled(applicationContext)) notifyNewDraws(draws)
            notifySavedTickets(draws)
        }
        val now = ZonedDateTime.now(DrawSchedule.IST)
        val waitingFor = DrawSchedule.recentlyDrawnSlot(now)
        val arrived = waitingFor == null || draws.any {
            it.slot == waitingFor && it.date == now.toLocalDate().toString() && it.status == "OK"
        }
        if (!arrived && runAttemptCount < MAX_RETRIES) return Result.retry()
        schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    private fun notifyNewDraws(draws: List<Draw>) {
        val prefs = applicationContext.getSharedPreferences("notified", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("draws", null)
        // Only today's and yesterday's results are news; history loaded by the user is not.
        val yesterday = ZonedDateTime.now(DrawSchedule.IST).toLocalDate().minusDays(1).toString()
        val ready = draws.filter { it.hasNumbers && it.date >= yesterday }
        if (seen == null) { // first run after install: don't flood with old results
            prefs.edit().putStringSet("draws", ready.map { it.id }.toSet()).apply()
            return
        }
        val fresh = ready.filter { it.id !in seen }
        fresh.forEach { d ->
            post(d.id.hashCode(), applicationContext.getString(R.string.notif_result_title, d.name),
                applicationContext.getString(R.string.notif_result_body, d.firstPrize ?: ""))
        }
        // Keep only ids still in the 7-day feed so the set stays small.
        val keep = (seen intersect draws.map { it.id }.toSet()) + fresh.map { it.id }
        if (keep != seen) prefs.edit().putStringSet("draws", keep).apply()
    }

    private fun notifySavedTickets(draws: List<Draw>) {
        val store = TicketStore.get(applicationContext)
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
                applicationContext.getString(R.string.notif_ticket_won, t.number, formatRupees(best.tier.prize))
            else applicationContext.getString(R.string.notif_ticket_lost, t.number)
            post(t.key.hashCode(), draw.name, body)
            done += t.key
        }
        if (done.isNotEmpty()) store.markNotified(done)
    }

    private fun post(id: Int, title: String, body: String) {
        val ctx = applicationContext
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
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        const val CHANNEL = "results"
        private const val WORK = "result-check"
        private const val MAX_RETRIES = 8

        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        /** Enqueue the next wake-up for the next draw's expected publish time. */
        fun schedule(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            val now = ZonedDateTime.now(DrawSchedule.IST)
            val delay = Duration.between(now, DrawSchedule.nextCheckAt(now)).toMillis().coerceAtLeast(60_000)
            val req = OneTimeWorkRequestBuilder<ResultCheckWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, policy, req)
        }
    }
}

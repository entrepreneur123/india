package com.lottodost.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.domain.DrawSchedule
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
        ResultNotifier.notifyAll(applicationContext, draws)
        val now = ZonedDateTime.now(DrawSchedule.IST)
        val waitingFor = DrawSchedule.recentlyDrawnSlot(now)
        val arrived = waitingFor == null || draws.any {
            it.slot == waitingFor && it.date == now.toLocalDate().toString() && it.status == "OK"
        }
        if (!arrived && runAttemptCount < MAX_RETRIES) return Result.retry()
        schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK = "result-check"
        private const val MAX_RETRIES = 8

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

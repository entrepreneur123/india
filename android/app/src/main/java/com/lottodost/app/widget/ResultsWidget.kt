package com.lottodost.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lottodost.app.MainActivity
import com.lottodost.app.R
import com.lottodost.app.data.Draw
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.ui.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget with today's three 1st prizes. It never fetches by itself: it redraws from
 * the cached feed whenever the app, the scheduled worker or a push brings new results.
 */
class ResultsWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ResultRepository.get(ctx).loadCache()
                render(ctx, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraw every placed widget from the repository's current state. Cheap; call after refreshes. */
        fun update(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, ResultsWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, manager, ids)
        }

        private fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val draws = ResultRepository.get(ctx).state.value.draws.values
            val now = ZonedDateTime.now(DrawSchedule.IST)
            // Before the first result of the day, yesterday's numbers are more useful than three dashes.
            val today = now.toLocalDate()
            val date = if (draws.any { it.date == today.toString() && it.hasNumbers }) today else today.minusDays(1)
            val views = RemoteViews(ctx.packageName, R.layout.widget_results)
            val dateLabel = if (date == today) ctx.getString(R.string.today)
            else date.format(DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.getDefault()))
            views.setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_title, dateLabel))
            val rows = listOf(R.id.time0 to R.id.prize0, R.id.time1 to R.id.prize1, R.id.time2 to R.id.prize2)
            val weekly = DrawSchedule.SLOTS.filter { it.format == "D5" }.take(3)
            weekly.forEachIndexed { i, slot ->
                val d: Draw? = draws.firstOrNull { it.slot == slot.id && it.date == date.toString() && it.hasNumbers }
                views.setTextViewText(rows[i].first, formatTime(slot.drawTime.toString()))
                views.setTextViewText(rows[i].second, d?.firstPrize ?: "—")
            }
            val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }

    }
}

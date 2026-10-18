package com.lottodost.app.data

import android.content.Context

/** Small app-level settings (SharedPreferences: tiny, synchronous reads are fine here). */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun languageChosen(ctx: Context) = p(ctx).getBoolean("language_chosen", false)
    fun setLanguageChosen(ctx: Context) = p(ctx).edit().putBoolean("language_chosen", true).apply()

    fun onboarded(ctx: Context) = p(ctx).getBoolean("onboarded", false)
    fun setOnboarded(ctx: Context) = p(ctx).edit().putBoolean("onboarded", true).apply()

    fun notificationsEnabled(ctx: Context) = p(ctx).getBoolean("notify_results", true)
    fun setNotificationsEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("notify_results", on).apply()

    /** Per-draw result alerts (1 PM / 6 PM / 8 PM / Laxmi / bumpers); all on by default. */
    fun alertKey(slot: String, special: Boolean) = if (special) "SPECIAL" else slot
    fun drawAlert(ctx: Context, key: String) = p(ctx).getBoolean("alert_$key", true)
    fun setDrawAlert(ctx: Context, key: String, on: Boolean) = p(ctx).edit().putBoolean("alert_$key", on).apply()
}

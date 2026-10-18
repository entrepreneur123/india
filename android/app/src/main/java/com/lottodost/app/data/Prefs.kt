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
}

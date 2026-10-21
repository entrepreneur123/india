package com.lottodost.app.push

import android.content.Context
import android.util.Log
import com.lottodost.app.data.Prefs
import com.lottodost.app.data.TicketStore
import com.lottodost.app.domain.DrawSchedule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.lottodost.app.BuildConfig

/**
 * Firebase Cloud Messaging, configured from BuildConfig instead of google-services.json so the
 * app still builds and works (scheduled checks only) before a Firebase project exists.
 * One topic per draw alert (r_NL1PM, r_KL, r_SPECIAL...): a phone is woken only for the results its owner
 * wants, plus the draws of saved tickets still waiting for a result.
 */
object Push {
    private const val LEGACY_TOPIC = "results"   // one topic for everyone, before 21-10-2026

    /** Every alert key, as in Prefs.alertKey and the server's push.alert_key. */
    fun allKeys(): List<String> = DrawSchedule.SLOTS.map { it.id } + listOf("KL", "SPECIAL")

    /** The topics this phone should be on now. */
    fun wantedKeys(ctx: Context): Set<String> {
        val alerts = if (Prefs.notificationsEnabled(ctx)) allKeys().filter { Prefs.drawAlert(ctx, it) } else emptyList()
        val tickets = TicketStore.get(ctx).tickets.value.filter { !it.notified }.map { it.slot }
        return (alerts + tickets).toSet()
    }

    val configured: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty() && BuildConfig.FIREBASE_API_KEY.isNotEmpty() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty() && BuildConfig.FIREBASE_SENDER_ID.isNotEmpty()

    fun init(ctx: Context) {
        if (!configured) return
        try {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx, FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build())
            }
            sync(ctx, force = true)
        } catch (e: RuntimeException) {
            // Missing Play services or a bad config must never stop the app: scheduled checks still run.
            Log.w("Push", "FCM unavailable", e)
        }
    }

    /** Subscribes to the wanted topics and leaves the others; call after an alert switch or a saved ticket changes. */
    fun sync(ctx: Context, force: Boolean = false) {
        if (!configured || FirebaseApp.getApps(ctx).isEmpty()) return
        val prefs = ctx.getSharedPreferences("push", Context.MODE_PRIVATE)
        val wanted = wantedKeys(ctx)
        val had = prefs.getStringSet("topics", null)
        if (!force && had == wanted) return
        try {
            val fm = FirebaseMessaging.getInstance()
            // Firebase retries these itself while offline, and both calls are idempotent.
            if (had == null) fm.unsubscribeFromTopic(LEGACY_TOPIC)
            allKeys().forEach { k -> if (k in wanted) fm.subscribeToTopic("r_$k") else fm.unsubscribeFromTopic("r_$k") }
            prefs.edit().putStringSet("topics", wanted).apply()
        } catch (e: RuntimeException) {
            Log.w("Push", "topic sync failed", e)
        }
    }
}

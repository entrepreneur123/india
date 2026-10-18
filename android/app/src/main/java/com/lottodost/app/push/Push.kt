package com.lottodost.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.lottodost.app.BuildConfig

/**
 * Firebase Cloud Messaging, configured from BuildConfig instead of google-services.json so the
 * app still builds and works (scheduled checks only) before a Firebase project exists.
 * Everyone subscribes to one topic; the message only says "new result", the app does the rest.
 */
object Push {
    const val TOPIC = "results"

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
            // Retried by Firebase itself when offline; idempotent on every start.
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
        } catch (e: RuntimeException) {
            // Missing Play services or a bad config must never stop the app: scheduled checks still run.
            Log.w("Push", "FCM unavailable", e)
        }
    }
}

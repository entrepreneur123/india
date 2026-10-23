package com.yaari.chat.live

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yaari.chat.BuildConfig
import com.yaari.chat.Flavor
import com.yaari.chat.PushNotifier
import com.yaari.chat.data.AppForeground
import com.yaari.chat.data.PushConfig
import com.yaari.chat.data.PushMessage
import com.yaari.chat.data.PushTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Firebase Cloud Messaging without the google-services Gradle plugin: the project's values come from BuildConfig
 * (gradle properties FIREBASE_*), and FirebaseApp is set up by hand in [init]. With any of them empty nothing here
 * runs: no Firebase start-up, no token, no registration (the manifest also drops Firebase's own start-up provider).
 */
object Fcm {
    val config = PushConfig(BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID)

    /** Application start: the default FirebaseApp, only when configured. */
    fun init(app: Application) {
        if (!config.enabled || FirebaseApp.getApps(app).isNotEmpty()) return
        FirebaseApp.initializeApp(
            app,
            FirebaseOptions.Builder().setApplicationId(config.appId).setApiKey(config.apiKey).setProjectId(config.projectId)
                .setGcmSenderId(config.senderId).build(),
        )
    }
}

/** The token from Firebase Messaging; null when it can't be had (no Play services, offline). */
class FcmTokens : PushTokens {
    override suspend fun token(): String? = suspendCancellableCoroutine { c ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> c.resume(if (t.isSuccessful) t.result else null) }
        }.onFailure { c.resume(null) }
    }
}

/** Data messages from the server's `push` function, and new tokens. */
class YaariMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val push = PushMessage.parse(message.data) ?: return
        // On screen, the realtime badges already say it.
        if (!AppForeground.visible.value) PushNotifier.show(this, push)
    }

    override fun onNewToken(token: String) {
        if (!Fcm.config.enabled) return
        scope.launch { runCatching { Flavor.push(application).renewed(token) } }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

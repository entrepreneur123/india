package com.lottodost.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lottodost.app.data.ResultRepository
import com.lottodost.app.work.ResultCheckWorker
import com.lottodost.app.work.ResultNotifier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A high-priority "result" message gives the app a short window (about 10–20 s) even in Doze.
 * Fetch the feed right here and notify; if the network is too slow, the scheduled worker catches up.
 */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "result") return
        val repo = ResultRepository.get(applicationContext)
        val ok = runBlocking {
            withTimeoutOrNull(15_000) { repo.refresh(ResultRepository.Mode.FULL, force = true) } ?: false
        }
        ResultNotifier.notifyAll(applicationContext, repo.state.value.drawList)
        if (!ok) ResultCheckWorker.schedule(applicationContext)
    }

    override fun onNewToken(token: String) {
        // Topic messaging needs no server-side token list; re-subscribe for the new token.
        Push.init(applicationContext)
    }
}

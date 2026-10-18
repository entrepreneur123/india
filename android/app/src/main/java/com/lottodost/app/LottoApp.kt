package com.lottodost.app

import android.app.Application
import com.lottodost.app.push.Push
import com.lottodost.app.work.ResultCheckWorker
import com.lottodost.app.work.ResultNotifier

class LottoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ResultNotifier.createChannel(this)
        Push.init(this)
        ResultCheckWorker.schedule(this)
    }
}

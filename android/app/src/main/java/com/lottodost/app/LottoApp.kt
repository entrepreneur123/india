package com.lottodost.app

import android.app.Application
import com.lottodost.app.work.ResultCheckWorker

class LottoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ResultCheckWorker.createChannel(this)
        ResultCheckWorker.schedule(this)
    }
}

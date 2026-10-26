package com.hakan.notdefteri

import android.app.Application
import com.hakan.notdefteri.alarm.AlarmScheduler
import com.hakan.notdefteri.data.Repository

class NotDefteriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        AlarmScheduler.createChannel(this)
    }
}

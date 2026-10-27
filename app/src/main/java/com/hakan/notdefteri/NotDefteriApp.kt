package com.hakan.notdefteri

import android.app.Application
import com.hakan.notdefteri.alarm.AlarmScheduler
import com.hakan.notdefteri.data.Repository
import com.hakan.notdefteri.data.Settings

class NotDefteriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Repository.init(this)
        AlarmScheduler.createChannel(this)
    }
}

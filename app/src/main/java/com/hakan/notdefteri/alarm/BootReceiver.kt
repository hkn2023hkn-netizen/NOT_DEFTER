package com.hakan.notdefteri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hakan.notdefteri.data.Repository

/** Telefon yeniden başlayınca alarmlar silinir; burada yeniden kurulur. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context, Repository.items.value)
    }
}

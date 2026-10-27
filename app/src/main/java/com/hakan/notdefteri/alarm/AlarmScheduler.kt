package com.hakan.notdefteri.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.hakan.notdefteri.MainActivity
import com.hakan.notdefteri.data.Item

object AlarmScheduler {
    const val CHANNEL_ID = "alarm_kanali"
    const val EXTRA_ITEM_ID = "item_id"
    const val EXTRA_EARLY = "early"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(CHANNEL_ID, "Alarmlar ve hatırlatıcılar", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Not ve görev alarmları"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    private fun requestCode(id: Int, early: Boolean) = id * 2 + if (early) 1 else 0

    private fun pendingIntent(context: Context, id: Int, early: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, id)
            putExtra(EXTRA_EARLY, early)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(id, early), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(context: Context, item: Item) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, item.id, true))
        am.cancel(pendingIntent(context, item.id, false))
    }

    /** Öğenin önceden ve zamanında alarmlarını kurar (geçmişte kalanları kurmaz). */
    fun schedule(context: Context, item: Item) {
        cancel(context, item)
        val due = item.dueAt ?: return
        if (item.done || item.deletedAt != null) return
        val now = System.currentTimeMillis()
        if (item.remindBeforeMinutes > 0) {
            val early = due - item.remindBeforeMinutes * 60_000L
            if (early > now) set(context, early, pendingIntent(context, item.id, true))
        }
        if (item.alarmAtDue && due > now) set(context, due, pendingIntent(context, item.id, false))
    }

    private fun set(context: Context, time: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactAllowed) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun rescheduleAll(context: Context, items: List<Item>) {
        items.forEach { schedule(context, it) }
    }
}

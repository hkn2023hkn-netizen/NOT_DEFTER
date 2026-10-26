package com.hakan.notdefteri.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hakan.notdefteri.R
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Repository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ITEM_ID, -1)
        val early = intent.getBooleanExtra(AlarmScheduler.EXTRA_EARLY, false)

        if (intent.action == ACTION_DISMISS) {
            context.getSystemService(NotificationManager::class.java).cancel(id)
            return
        }

        val item = Repository.get(id) ?: return
        val title = item.title.ifBlank { if (item.type == ItemType.TASK) "Görev" else "Not" }
        val time = item.dueAt?.let { SimpleDateFormat("d MMMM HH:mm", Locale.forLanguageTag("tr")).format(Date(it)) } ?: ""
        val text = if (early) "Yaklaşıyor: ${item.remindBeforeMinutes.toBeforeText()} sonra ($time)" else "Zamanı geldi ($time)"

        val fullScreen = PendingIntent.getActivity(
            context, id,
            Intent(context, AlarmActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ITEM_ID, id)
                putExtra(AlarmScheduler.EXTRA_EARLY, early)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            context, id * 2 + 100_000,
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(AlarmScheduler.EXTRA_ITEM_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AlarmScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + if (item.content.isNotBlank()) "\n" + item.content.take(200) else ""))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Kapat", dismiss)
            .setAutoCancel(true)
            .build()
        // Alarm sesi, kullanıcı kapatana kadar tekrar eder
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        try {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (e: SecurityException) {
            // Bildirim izni verilmemiş
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.hakan.notdefteri.DISMISS"
    }
}

fun Int.toBeforeText(): String = when {
    this % 1440 == 0 -> "${this / 1440} gün"
    this % 60 == 0 -> "${this / 60} saat"
    else -> "$this dakika"
}

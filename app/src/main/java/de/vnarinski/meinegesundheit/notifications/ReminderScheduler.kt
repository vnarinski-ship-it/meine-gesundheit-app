package de.vnarinski.meinegesundheit.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.vnarinski.meinegesundheit.domain.HealthReminder

object ReminderScheduler {
    fun schedule(context: Context, reminder: HealthReminder) {
        if (!reminder.enabled) return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", reminder.id)
            putExtra("title", reminder.title)
            putExtra("body", reminder.body)
            putExtra("repeatDaily", reminder.repeatDaily)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        var whenMs = reminder.triggerAt.toEpochMilli()
        val now = System.currentTimeMillis()
        if (whenMs <= now) {
            if (!reminder.repeatDaily) return
            val day = 24L * 60L * 60L * 1000L
            while (whenMs <= now) whenMs += day
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
    }

    fun cancel(context: Context, reminderId: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarm.cancel(pi)
        pi.cancel()
    }
}

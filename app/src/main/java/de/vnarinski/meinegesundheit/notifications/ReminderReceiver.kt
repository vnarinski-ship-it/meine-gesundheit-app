package de.vnarinski.meinegesundheit.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.temporal.ChronoUnit

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Gesundheitserinnerung"
        val body = intent.getStringExtra("body") ?: "Erinnerung"
        val id = intent.getStringExtra("id") ?: return
        val channelId = "health_reminders"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channelId, "Gesundheitserinnerungen", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        }
        if (intent.getBooleanExtra("repeatDaily", false)) {
            ReminderScheduler.schedule(
                context,
                de.vnarinski.meinegesundheit.domain.HealthReminder(
                    id = id,
                    kind = de.vnarinski.meinegesundheit.domain.ReminderKind.MEDICATION_DAILY,
                    targetId = "",
                    title = title,
                    body = body,
                    triggerAt = Instant.now().plus(1, ChronoUnit.DAYS),
                    repeatDaily = true
                )
            )
        }
    }
}

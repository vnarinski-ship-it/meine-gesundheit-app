package de.vnarinski.meinegesundheit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.vnarinski.meinegesundheit.data.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                HealthRepository(context.applicationContext).loadReminders().filter { it.enabled }.forEach {
                    ReminderScheduler.schedule(context.applicationContext, it)
                }
            }
            pending.finish()
        }
    }
}

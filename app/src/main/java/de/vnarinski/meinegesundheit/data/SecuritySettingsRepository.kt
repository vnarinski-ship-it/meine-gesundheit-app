package de.vnarinski.meinegesundheit.data

import android.content.Context
import java.time.Instant

data class SecuritySettings(
    val appLockEnabled: Boolean = false,
    val backupWarningDays: Int = 30,
    val lastBackupAt: Instant? = null
)

class SecuritySettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)

    fun load(): SecuritySettings = SecuritySettings(
        appLockEnabled = prefs.getBoolean("app_lock_enabled", false),
        backupWarningDays = prefs.getInt("backup_warning_days", 30).coerceIn(7, 180),
        lastBackupAt = prefs.getString("last_backup_at", null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
    )

    fun setAppLockEnabled(enabled: Boolean) { prefs.edit().putBoolean("app_lock_enabled", enabled).apply() }
    fun setBackupWarningDays(days: Int) { prefs.edit().putInt("backup_warning_days", days.coerceIn(7, 180)).apply() }
    fun markBackupCreated(at: Instant = Instant.now()) { prefs.edit().putString("last_backup_at", at.toString()).apply() }
}

package de.vnarinski.meinegesundheit.gps

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import de.vnarinski.meinegesundheit.MainActivity
import de.vnarinski.meinegesundheit.data.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class DriveTrackingService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "de.vnarinski.meinegesundheit.START_DRIVE"
        const val ACTION_STOP = "de.vnarinski.meinegesundheit.STOP_DRIVE"
        const val EXTRA_PRIVACY_MODE = "privacy_mode"
        const val PREFS = "drive_tracking"
        const val KEY_ACTIVE = "active"
        const val KEY_STARTED_AT = "started_at"
        const val CHANNEL_ID = "drive_tracking_channel"
        const val NOTIFICATION_ID = 3103
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private var startedAt: Instant? = null
    private var lastLocation: Location? = null
    private var distanceMeters = 0.0
    private var mode = DrivePrivacyMode.TIME_AND_DISTANCE
    private val route = mutableListOf<RoutePoint>()

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTrackingAndSave()
            else -> startTracking(intent)
        }
        return START_STICKY
    }

    private fun startTracking(intent: Intent?) {
        if (startedAt != null) return
        mode = runCatching {
            DrivePrivacyMode.valueOf(intent?.getStringExtra(EXTRA_PRIVACY_MODE) ?: DrivePrivacyMode.TIME_AND_DISTANCE.name)
        }.getOrDefault(DrivePrivacyMode.TIME_AND_DISTANCE)
        startedAt = Instant.now()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_STARTED_AT, startedAt.toString())
            .apply()

        startForeground(NOTIFICATION_ID, buildNotification("Fahrt wird aufgezeichnet"))
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 10f, this)
            } catch (_: Exception) { }
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 20f, this)
            } catch (_: Exception) { }
        }
    }

    override fun onLocationChanged(location: Location) {
        val previous = lastLocation
        if (previous != null && mode != DrivePrivacyMode.TIME_ONLY) {
            val delta = previous.distanceTo(location).toDouble()
            if (delta in 0.5..5_000.0) distanceMeters += delta
        }
        lastLocation = location
        if (mode == DrivePrivacyMode.FULL_ROUTE) {
            route += RoutePoint(location.latitude, location.longitude, location.time, location.accuracy)
        }
        val km = distanceMeters / 1000.0
        val text = if (mode == DrivePrivacyMode.TIME_ONLY) "Fahrt läuft" else "Fahrt läuft · %.1f km".format(km)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun stopTrackingAndSave() {
        val start = startedAt ?: getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_STARTED_AT, null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        runCatching { locationManager.removeUpdates(this) }
        if (start != null) {
            val end = Instant.now()
            val distance = if (mode == DrivePrivacyMode.TIME_ONLY) null else distanceMeters
            val routeCopy = if (mode == DrivePrivacyMode.FULL_ROUTE) route.toList() else emptyList()
            scope.launch {
                runCatching {
                    HealthRepository(applicationContext).addDriveSession(start, end, distance, mode, routeCopy)
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATIO")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Fahrtenaufzeichnung", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt an, wenn eine Arbeitsfahrt per GPS aufgezeichnet wird."
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, DriveTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Meine Gesundheit · Arbeitsfahrt")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Fahrt beenden", stopIntent)
            .build()
    }
}

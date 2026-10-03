package de.vnarinski.meinegesundheit.gps

enum class DrivePrivacyMode { TIME_ONLY, TIME_AND_DISTANCE, FULL_ROUTE }

data class DriveTrackingSettings(
    val automaticDetection: Boolean = false,
    val privacyMode: DrivePrivacyMode = DrivePrivacyMode.TIME_AND_DISTANCE,
    val minimumDriveMinutes: Int = 5
)

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val atEpochMillis: Long,
    val accuracyMeters: Float? = null
)

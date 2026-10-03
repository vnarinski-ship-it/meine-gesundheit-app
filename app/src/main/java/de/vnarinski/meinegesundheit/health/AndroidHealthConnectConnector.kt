package de.vnarinski.meinegesundheit.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.vnarinski.meinegesundheit.domain.*
import java.time.Instant

class AndroidHealthConnectConnector(private val context: Context) {
    companion object {
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
        fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun hasAllPermissions(): Boolean = client().permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readLast30Days(): List<HealthMetric> {
        val end = Instant.now()
        val start = end.minusSeconds(30L * 24L * 60L * 60L)
        val range = TimeRangeFilter.between(start, end)
        val out = mutableListOf<HealthMetric>()
        val hc = client()

        hc.readRecords(ReadRecordsRequest(StepsRecord::class, range)).records.forEach { r ->
            out += HealthMetric(
                id = "hc-steps-${r.metadata.id}", type = "steps", value = r.count.toDouble(), unit = "steps",
                start = r.startTime, end = r.endTime,
                provenance = Provenance(DataOrigin.HEALTH_CONNECT, r.metadata.dataOrigin.packageName, Confidence.MEASURED, r.endTime, r.metadata.id)
            )
        }
        hc.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records.forEach { r ->
            out += HealthMetric(
                id = "hc-weight-${r.metadata.id}", type = "weight", value = r.weight.inKilograms, unit = "kg",
                start = r.time,
                provenance = Provenance(DataOrigin.HEALTH_CONNECT, r.metadata.dataOrigin.packageName, Confidence.MEASURED, r.time, r.metadata.id)
            )
        }
        hc.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records.forEach { r ->
            val avg = r.samples.map { it.beatsPerMinute.toDouble() }.average().takeIf { !it.isNaN() }
            out += HealthMetric(
                id = "hc-heart-${r.metadata.id}", type = "heart_rate_avg", value = avg, unit = "bpm",
                start = r.startTime, end = r.endTime,
                provenance = Provenance(DataOrigin.HEALTH_CONNECT, r.metadata.dataOrigin.packageName, Confidence.MEASURED, r.endTime, r.metadata.id)
            )
        }
        hc.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records.forEach { r ->
            val minutes = java.time.Duration.between(r.startTime, r.endTime).toMinutes().toDouble()
            out += HealthMetric(
                id = "hc-sleep-${r.metadata.id}", type = "sleep_duration", value = minutes, unit = "min",
                start = r.startTime, end = r.endTime,
                provenance = Provenance(DataOrigin.HEALTH_CONNECT, r.metadata.dataOrigin.packageName, Confidence.MEASURED, r.endTime, r.metadata.id)
            )
        }
        return out
    }
}

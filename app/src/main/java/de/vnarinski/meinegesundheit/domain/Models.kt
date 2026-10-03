package de.vnarinski.meinegesundheit.domain

import java.time.Instant

enum class DataOrigin { MANUAL, CAMERA_AI, PDF_AI, LAB_IMPORT, HEALTH_CONNECT, GPS, WEARABLE }
enum class Confidence { MEASURED, USER_CONFIRMED, ESTIMATED }

data class Provenance(
    val origin: DataOrigin,
    val sourceName: String? = null,
    val confidence: Confidence,
    val recordedAt: Instant = Instant.now(),
    val externalId: String? = null,
    val externalVersion: Long? = null
)

data class MedicalDocument(
    val id: String,
    val title: String,
    val documentDate: Instant,
    val provider: String? = null,
    val documentType: String? = null,
    val originalUri: String,
    val aiSummary: String? = null,
    val provenance: Provenance
)

data class LabResult(
    val id: String,
    val codeSystem: String? = "LOINC",
    val code: String? = null,
    val name: String,
    val value: Double? = null,
    val textValue: String? = null,
    val unit: String? = null,
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
    val measuredAt: Instant,
    val provenance: Provenance
)

data class Medication(
    val id: String,
    val name: String,
    val dose: String? = null,
    val schedule: String? = null,
    val start: Instant? = null,
    val end: Instant? = null,
    val active: Boolean = true,
    val provenance: Provenance
)

data class SymptomEntry(
    val id: String,
    val bodyArea: String,
    val symptom: String,
    val intensity0to10: Int,
    val note: String? = null,
    val at: Instant,
    val provenance: Provenance
)

data class FoodItemEstimate(
    val name: String,
    val grams: Double?,
    val carbsG: Double?,
    val fiberG: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val kcal: Double?
)

data class Meal(
    val id: String,
    val at: Instant,
    val photoUri: String? = null,
    val items: List<FoodItemEstimate>,
    val note: String? = null,
    val provenance: Provenance
) {
    val carbsG get() = items.sumOf { it.carbsG ?: 0.0 }
    val netCarbsG get() = items.sumOf { (it.carbsG ?: 0.0) - (it.fiberG ?: 0.0) }.coerceAtLeast(0.0)
    val proteinG get() = items.sumOf { it.proteinG ?: 0.0 }
    val fatG get() = items.sumOf { it.fatG ?: 0.0 }
    val kcal get() = items.sumOf { it.kcal ?: 0.0 }
}

data class HealthMetric(
    val id: String,
    val type: String,
    val value: Double?,
    val textValue: String? = null,
    val unit: String? = null,
    val start: Instant,
    val end: Instant? = null,
    val provenance: Provenance
)

data class DriveSession(
    val id: String,
    val start: Instant,
    val end: Instant?,
    val distanceMeters: Double?,
    val longestContinuousMinutes: Int?,
    val privacyMode: String = "TIME_AND_DISTANCE",
    val routePointCount: Int = 0,
    val provenance: Provenance
) {
    val durationMinutes: Long? get() = end?.let { java.time.Duration.between(start, it).toMinutes() }
}

data class DoctorAppointment(
    val id: String,
    val startsAt: Instant,
    val doctorName: String? = null,
    val specialty: String? = null,
    val reason: String,
    val questions: List<String> = emptyList(),
    val notes: String? = null,
    val preparationSummary: String? = null,
    val createdAt: Instant = Instant.now(),
    val provenance: Provenance
)

data class TimelineEntry(
    val id: String,
    val at: Instant,
    val category: String,
    val title: String,
    val detail: String? = null
)

data class ExtractedLabCandidate(
    val tempId: String,
    val documentId: String,
    val name: String,
    val value: Double? = null,
    val textValue: String? = null,
    val unit: String? = null,
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
    val measuredAt: Instant,
    val sourceText: String? = null
)

enum class ReminderKind { MEDICATION_DAILY, APPOINTMENT }

data class HealthReminder(
    val id: String,
    val kind: ReminderKind,
    val targetId: String,
    val title: String,
    val body: String,
    val triggerAt: Instant,
    val repeatDaily: Boolean = false,
    val enabled: Boolean = true
)

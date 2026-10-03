package de.vnarinski.meinegesundheit.ai

/** KI-Ergebnisse müssen Messwerte, Nutzereingaben und Schätzungen klar trennen. */
data class AiFinding(
    val text: String,
    val evidenceIds: List<String>,
    val isEstimate: Boolean,
    val caution: String? = null
)

interface AiAnalyzer {
    suspend fun explainDocument(documentId: String): List<AiFinding>
    suspend fun compareLabResults(resultIds: List<String>): List<AiFinding>
    suspend fun analyzeMeal(photoUri: String): List<AiFinding>
    suspend fun prepareDoctorSummary(fromEpochMs: Long, toEpochMs: Long): String
}

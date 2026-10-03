package de.vnarinski.meinegesundheit.health

/**
 * Austauschbare Schnittstelle für Health Connect bzw. zukünftige ChatGPT-Health-Anbindung.
 * Die restliche App kennt keine konkrete externe Plattform.
 */
interface HealthConnector {
    suspend fun isAvailable(): Boolean
    suspend fun importNewData(): HealthSyncResult
    suspend fun exportSupportedData(): HealthSyncResult
}

data class HealthSyncResult(
    val imported: Int = 0,
    val exported: Int = 0,
    val errors: List<String> = emptyList()
)

package de.vnarinski.ocrhub.model

import java.time.Instant

data class DocumentRecord(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val importedAt: Instant,
    val ocrText: String = "",
    val pageCount: Int = 1,
    val source: String = "import"
)

data class ExportEnvelope(
    val schemaVersion: Int = 1,
    val target: String,
    val documentId: String,
    val fileName: String,
    val mimeType: String,
    val importedAt: String,
    val ocrText: String
)

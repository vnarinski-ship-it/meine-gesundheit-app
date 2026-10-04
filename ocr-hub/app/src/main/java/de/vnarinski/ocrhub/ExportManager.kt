package de.vnarinski.ocrhub

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.vnarinski.ocrhub.model.DocumentRecord
import org.json.JSONObject
import java.io.File

class ExportManager(private val context: Context) {
    fun share(rec: DocumentRecord, target: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ocr-" + rec.id + ".json")
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("sourceApp", "OCR Hub")
            .put("target", target)
            .put("documentId", rec.id)
            .put("fileName", rec.fileName)
            .put("mimeType", rec.mimeType)
            .put("importedAt", rec.importedAt.toString())
            .put("ocrText", rec.ocrText)
            .put("aiDocumentType", rec.aiDocumentType)
            .put("aiSummary", rec.aiSummary)
            .put("aiSuggestedTarget", rec.aiSuggestedTarget)
            .put("aiFacts", org.json.JSONArray(rec.aiFacts))
            .put("aiCaution", rec.aiCaution)
        file.writeText(json.toString(2))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.vnarinski.ocrhub+json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, rec.ocrText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (target == "health") intent.setPackage("de.vnarinski.meinegesundheit")
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent.createChooser(intent.setPackage(null), "OCR-Daten weitergeben"))
        }
    }
}

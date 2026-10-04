package de.vnarinski.ocrhub.data

import android.content.Context
import android.net.Uri
import de.vnarinski.ocrhub.model.DocumentRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class DocumentStore(private val context: Context) {
    private val vault = SecureVault(context)
    private val prefs = context.getSharedPreferences("ocr_hub_store", Context.MODE_PRIVATE)

    fun importUri(uri: Uri): DocumentRecord {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Datei konnte nicht gelesen werden")
        val id = UUID.randomUUID().toString()
        val ext = when {
            mime == "application/pdf" -> "pdf"
            mime.startsWith("image/") -> mime.substringAfter("/")
            else -> "bin"
        }
        val path = vault.write("doc-$id.$ext.enc", bytes)
        val rec = DocumentRecord(id, "Dokument-$id.$ext", mime, path, Instant.now())
        save(rec)
        return rec
    }

    fun updateOcr(id: String, text: String, pageCount: Int): DocumentRecord {
        val all = all().toMutableList()
        val i = all.indexOfFirst { it.id == id }
        require(i >= 0) { "Dokument nicht gefunden" }
        all[i] = all[i].copy(ocrText = text, pageCount = pageCount)
        persist(all)
        return all[i]
    }

    fun all(): List<DocumentRecord> {
        val raw = prefs.getString("records", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    DocumentRecord(
                        id = o.getString("id"),
                        fileName = o.getString("fileName"),
                        mimeType = o.getString("mimeType"),
                        localPath = o.getString("localPath"),
                        importedAt = Instant.parse(o.getString("importedAt")),
                        ocrText = o.optString("ocrText"),
                        pageCount = o.optInt("pageCount", 1),
                        source = o.optString("source", "import")
                    )
                )
            }
        }.sortedByDescending { it.importedAt }
    }

    fun bytes(rec: DocumentRecord): ByteArray = vault.read(rec.localPath)

    private fun save(rec: DocumentRecord) {
        val all = all().toMutableList()
        all.removeAll { it.id == rec.id }
        all += rec
        persist(all)
    }

    private fun persist(all: List<DocumentRecord>) {
        val arr = JSONArray()
        all.forEach { r ->
            arr.put(JSONObject()
                .put("id", r.id)
                .put("fileName", r.fileName)
                .put("mimeType", r.mimeType)
                .put("localPath", r.localPath)
                .put("importedAt", r.importedAt.toString())
                .put("ocrText", r.ocrText)
                .put("pageCount", r.pageCount)
                .put("source", r.source)
            )
        }
        prefs.edit().putString("records", arr.toString()).apply()
    }
}

package de.vnarinski.ocrhub.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OcrAiResult(
    val documentType: String,
    val summary: String,
    val suggestedTarget: String,
    val extractedFacts: List<String>,
    val caution: String
)

class OcrAiAnalyzer(
    private val apiKey: String,
    private val model: String
) {
    fun analyze(ocrText: String): OcrAiResult {
        require(ocrText.isNotBlank()) { "Kein OCR-Text vorhanden" }

        val prompt = """
            Analysiere den folgenden OCR-Text eines privaten Dokuments.
            Gib ausschließlich JSON in diesem Schema zurück:
            {
              "document_type": "Gesundheitsbefund|Labor|Technisches Handbuch|Fehlerprotokoll|Rechnung|Sonstiges",
              "summary": "kurze sachliche Zusammenfassung auf Deutsch",
              "suggested_target": "health|technician|archive",
              "extracted_facts": ["nur explizit im Dokument vorhandene Fakten"],
              "caution": "Hinweis auf mögliche OCR-Unsicherheiten oder leere Zeichenkette"
            }
            Erfinde nichts. Medizinische Inhalte nicht diagnostizieren. Technische Inhalte nicht als sicher bestätigen,
            wenn der OCR-Text unklar ist.

            OCR-TEXT:
            $ocrText
        """.trimIndent()

        val request = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", JSONArray().put(
                        JSONObject().put("type", "input_text").put("text", prompt)
                    ))
            ))

        val conn = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer " + apiKey)
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(request.toString().toByteArray()) }
        val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) error("OpenAI API " + conn.responseCode + ": " + response.take(300))

        val text = extractOutputText(JSONObject(response))
        val json = extractJson(text)
        val factsArray = json.optJSONArray("extracted_facts") ?: JSONArray()
        val facts = buildList {
            for (i in 0 until factsArray.length()) {
                factsArray.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return OcrAiResult(
            documentType = json.optString("document_type", "Sonstiges"),
            summary = json.optString("summary"),
            suggestedTarget = json.optString("suggested_target", "archive"),
            extractedFacts = facts,
            caution = json.optString("caution")
        )
    }

    private fun extractOutputText(root: JSONObject): String {
        val output = root.optJSONArray("output") ?: error("Keine KI-Antwort")
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") return part.optString("text")
            }
        }
        error("Keine Textantwort der KI")
    }

    private fun extractJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "KI-Antwort war kein JSON" }
        return JSONObject(text.substring(start, end + 1))
    }
}

package de.vnarinski.meinegesundheit.ai

import android.util.Base64
import de.vnarinski.meinegesundheit.domain.FoodItemEstimate
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MealAiResult(
    val items: List<FoodItemEstimate>,
    val summary: String,
    val uncertainty: String
)

data class ExtractedLabAiValue(
    val name: String,
    val value: Double?,
    val textValue: String?,
    val unit: String?,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val sourceText: String?
)

data class DocumentAiResult(
    val summary: String,
    val importantValues: List<String>,
    val questionsForDoctor: List<String>,
    val labCandidates: List<ExtractedLabAiValue>
)

class OpenAiAnalyzer(
    private val apiKey: String,
    private val model: String = "gpt-5"
) {
    fun analyzeMeal(imageBytes: ByteArray, mimeType: String = "image/jpeg"): MealAiResult {
        val dataUrl = "data:" + mimeType + ";base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val prompt = """
            Analyze this meal photo for a low-carb food diary. Return JSON only with this exact shape:
            {"items":[{"name":"","grams":0,"carbs_g":0,"fiber_g":0,"protein_g":0,"fat_g":0,"kcal":0}],"summary":"","uncertainty":""}
            Estimate portions conservatively. Values are estimates, not medical advice. If something cannot be identified, say so in uncertainty.
        """.trimIndent()
        val body = responseBody(prompt, "input_image", "image_url", dataUrl, null)
        val json = extractJson(body)
        val arr = json.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(FoodItemEstimate(
                    name = x.optString("name", "Unbekannt"),
                    grams = x.optDoubleNullable("grams"),
                    carbsG = x.optDoubleNullable("carbs_g"),
                    fiberG = x.optDoubleNullable("fiber_g"),
                    proteinG = x.optDoubleNullable("protein_g"),
                    fatG = x.optDoubleNullable("fat_g"),
                    kcal = x.optDoubleNullable("kcal")
                ))
            }
        }
        return MealAiResult(items, json.optString("summary"), json.optString("uncertainty"))
    }

    fun analyzeDocument(fileBytes: ByteArray, filename: String): DocumentAiResult {
        val base64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
        val body = responseBody(medicalPrompt(), "input_file", "file_data", base64, filename)
        return parseDocumentResult(body)
    }

    fun analyzeMedicalText(text: String): DocumentAiResult {
        require(text.isNotBlank()) { "Kein OCR-Text vorhanden" }
        val content = JSONArray().put(
            JSONObject()
                .put("type", "input_text")
                .put("text", medicalPrompt() + "\n\nOCR-TEXT:\n" + text)
        )
        val request = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        return parseDocumentResult(responseJson(request))
    }

    private fun medicalPrompt(): String = """
        Erkläre dieses medizinische Dokument verständlich auf Deutsch. Gib ausschließlich JSON in exakt diesem Schema zurück:
        {"summary":"","important_values":[""],"questions_for_doctor":[""],"lab_values":[{"name":"","value":null,"text_value":null,"unit":null,"reference_low":null,"reference_high":null,"source_text":""}]}
        Trenne ausdrücklich im Dokument genannte Fakten von Interpretation. Keine Diagnose stellen.
        In lab_values nur tatsächlich vorhandene Messwerte/Testwerte übernehmen.
        Fehlende Werte, Einheiten oder Referenzbereiche niemals erfinden.
    """.trimIndent()

    private fun parseDocumentResult(body: String): DocumentAiResult {
        val json = extractJson(body)
        fun strings(key: String): List<String> {
            val a = json.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until a.length()) {
                    val v = a.optString(i)
                    if (v.isNotBlank()) add(v)
                }
            }
        }
        val labsArray = json.optJSONArray("lab_values") ?: JSONArray()
        val labCandidates = buildList {
            for (i in 0 until labsArray.length()) {
                val x = labsArray.optJSONObject(i) ?: continue
                val name = x.optString("name").trim()
                if (name.isBlank()) continue
                add(ExtractedLabAiValue(
                    name = name,
                    value = x.optDoubleNullable("value"),
                    textValue = x.optNullableString("text_value"),
                    unit = x.optNullableString("unit"),
                    referenceLow = x.optDoubleNullable("reference_low"),
                    referenceHigh = x.optDoubleNullable("reference_high"),
                    sourceText = x.optNullableString("source_text")
                ))
            }
        }
        return DocumentAiResult(
            json.optString("summary"),
            strings("important_values"),
            strings("questions_for_doctor"),
            labCandidates
        )
    }

    private fun responseBody(prompt: String, inputType: String, dataKey: String, data: String, filename: String?): String {
        val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", prompt))
        val media = JSONObject().put("type", inputType).put(dataKey, data)
        if (filename != null) media.put("filename", filename)
        content.put(media)
        val request = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        return responseJson(request)
    }

    private fun responseJson(request: JSONObject): String {
        val conn = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer " + apiKey)
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(request.toString().toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("OpenAI API " + code + ": " + raw.take(500))
        return extractOutputText(JSONObject(raw))
    }

    private fun extractOutputText(root: JSONObject): String {
        val output = root.optJSONArray("output") ?: error("Keine KI-Antwort erhalten")
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") return part.optString("text")
            }
        }
        error("Keine Textantwort erhalten")
    }

    private fun extractJson(text: String): JSONObject {
        val trimmed = text.trim().removePrefix("~~~json").removePrefix("~~~").removeSuffix("~~~").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) error("KI-Antwort war kein gültiges JSON")
        return JSONObject(trimmed.substring(start, end + 1))
    }

    private fun JSONObject.optDoubleNullable(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

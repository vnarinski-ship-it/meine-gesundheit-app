package de.vnarinski.meinegesundheit.ai

import java.net.HttpURLConnection
import java.net.URL

object OpenAiKeyTester {
    fun test(apiKey: String): Result<Unit> = runCatching {
        require(apiKey.trim().isNotBlank()) { "API-Schlüssel ist leer" }
        val conn = (URL("https://api.openai.com/v1/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer " + apiKey.trim())
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            error("API antwortet mit HTTP $code" + if (body.isNotBlank()) ": " + body.take(220) else "")
        }
    }
}

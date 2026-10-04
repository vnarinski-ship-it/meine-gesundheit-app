package de.vnarinski.ocrhub.data

import android.content.Context
import org.json.JSONObject

data class AiSettings(
    val apiKey: String = "",
    val model: String = "chat-latest"
) {
    val configured: Boolean get() = apiKey.isNotBlank()
}

class AiSettingsStore(context: Context) {
    private val vault = SecureVault(context)
    private val fileName = "ai-settings.json.enc"
    private val path by lazy { java.io.File(context.filesDir, "vault/$fileName").absolutePath }

    fun load(): AiSettings {
        return runCatching {
            val raw = vault.read(path).toString(Charsets.UTF_8)
            val j = JSONObject(raw)
            AiSettings(j.optString("apiKey"), j.optString("model", "chat-latest"))
        }.getOrDefault(AiSettings())
    }

    fun save(settings: AiSettings) {
        val json = JSONObject()
            .put("apiKey", settings.apiKey.trim())
            .put("model", settings.model.trim().ifBlank { "chat-latest" })
        vault.write(fileName, json.toString().toByteArray())
    }
}

package de.vnarinski.meinegesundheit.data

import android.content.Context
import org.json.JSONObject

data class AiSettings(val apiKey: String = "", val model: String = "gpt-5") {
    val configured get() = apiKey.isNotBlank()
}

class AiSettingsRepository(context: Context) {
    private val vault = EncryptedVault(context)
    private val file = "ai-settings.json.enc"

    fun load(): AiSettings {
        val raw = vault.read(file)?.toString(Charsets.UTF_8) ?: return AiSettings()
        return runCatching {
            val j = JSONObject(raw)
            AiSettings(j.optString("apiKey"), j.optString("model", "gpt-5"))
        }.getOrDefault(AiSettings())
    }

    fun save(settings: AiSettings) {
        val j = JSONObject().put("apiKey", settings.apiKey.trim()).put("model", settings.model.trim().ifBlank { "gpt-5" })
        vault.write(file, j.toString().toByteArray())
    }
}

package de.vnarinski.ocrhub

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.vnarinski.ocrhub.ai.OcrAiAnalyzer
import de.vnarinski.ocrhub.data.AiSettings
import de.vnarinski.ocrhub.data.AiSettingsStore
import de.vnarinski.ocrhub.data.DocumentStore
import de.vnarinski.ocrhub.model.DocumentRecord
import de.vnarinski.ocrhub.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var store: DocumentStore
    private lateinit var engine: OcrEngine
    private lateinit var exporter: ExportManager
    private lateinit var aiSettingsStore: AiSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DocumentStore(this)
        engine = OcrEngine(this, store)
        exporter = ExportManager(this)
        aiSettingsStore = AiSettingsStore(this)
        setContent { MaterialTheme { OcrHubApp() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OcrHubApp() {
        var records by remember { mutableStateOf(store.all()) }
        var selected by remember { mutableStateOf<DocumentRecord?>(null) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var showAiSettings by remember { mutableStateOf(false) }
        var aiSettings by remember { mutableStateOf(aiSettingsStore.load()) }
        val scope = rememberCoroutineScope()

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                runCatching { store.importUri(uri) }
                    .onSuccess {
                        records = store.all()
                        selected = it
                        message = "Dokument importiert"
                    }
                    .onFailure { message = "Importfehler: " + (it.message ?: "unbekannt") }
            }
        }

        Scaffold(topBar = { TopAppBar(title = { Text("OCR Hub") }) }) { padding ->
            if (selected == null) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Text("Dokumente lokal erkennen und weitergeben", fontWeight = FontWeight.SemiBold) }
                    item {
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Foto oder PDF importieren") }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showAiSettings = !showAiSettings },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (aiSettings.configured) "KI-Einstellungen ✓" else "KI-Einstellungen")
                        }
                    }
                    if (showAiSettings) {
                        item {
                            var key by remember(aiSettings.apiKey) { mutableStateOf(aiSettings.apiKey) }
                            var model by remember(aiSettings.model) { mutableStateOf(aiSettings.model) }
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("OpenAI API", fontWeight = FontWeight.SemiBold)
                                    Text("OCR bleibt lokal. Erst „Mit KI bewerten“ sendet den korrigierten Text.")
                                    OutlinedTextField(
                                        value = key,
                                        onValueChange = { key = it },
                                        label = { Text("API-Schlüssel") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = model,
                                        onValueChange = { model = it },
                                        label = { Text("Modell") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            aiSettings = AiSettings(key.trim(), model.trim().ifBlank { "chat-latest" })
                                            aiSettingsStore.save(aiSettings)
                                            message = "API-Schlüssel verschlüsselt gespeichert"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Sicher speichern") }
                                }
                            }
                        }
                    }
                    message?.let { m -> item { Text(m) } }
                    items(records, key = { it.id }) { rec ->
                        ElevatedCard(onClick = { selected = rec }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(rec.fileName, fontWeight = FontWeight.SemiBold)
                                Text(if (rec.ocrText.isBlank()) "Noch nicht erkannt" else "OCR fertig · " + rec.pageCount + " Seite(n)")
                                if (rec.aiDocumentType.isNotBlank()) {
                                    Text("KI: " + rec.aiDocumentType + " · Ziel: " + rec.aiSuggestedTarget)
                                }
                            }
                        }
                    }
                }
            } else {
                val rec = selected!!
                var editableText by remember(rec.id, rec.ocrText) { mutableStateOf(rec.ocrText) }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Text(rec.fileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { Text("Original bleibt verschlüsselt gespeichert. OCR läuft lokal auf dem Gerät.") }
                    item {
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { engine.recognize(rec) }
                                        .onSuccess { result ->
                                            val updated = store.updateOcr(rec.id, result.text, result.pageCount)
                                            editableText = updated.ocrText
                                            records = store.all()
                                            selected = updated
                                            message = "OCR abgeschlossen"
                                        }
                                        .onFailure { message = "OCR-Fehler: " + (it.message ?: "unbekannt") }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Bitte warten …" else "OCR starten") }
                    }
                    item {
                        OutlinedTextField(
                            value = editableText,
                            onValueChange = { editableText = it },
                            label = { Text("Erkannter Text – korrigierbar") },
                            minLines = 12,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val updated = store.updateOcr(rec.id, editableText, rec.pageCount)
                                selected = updated
                                records = store.all()
                                message = "Korrektur gespeichert"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Korrektur speichern") }
                    }
                    item {
                        FilledTonalButton(
                            enabled = !busy && editableText.isNotBlank(),
                            onClick = {
                                if (!aiSettings.configured) {
                                    message = "Bitte zuerst API-Schlüssel in KI-Einstellungen speichern"
                                } else {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                OcrAiAnalyzer(aiSettings.apiKey, aiSettings.model).analyze(editableText)
                                            }
                                        }.onSuccess { result ->
                                            val updated = store.updateAi(
                                                rec.id,
                                                result.documentType,
                                                result.summary,
                                                result.suggestedTarget,
                                                result.extractedFacts,
                                                result.caution
                                            )
                                            selected = updated
                                            records = store.all()
                                            message = "KI-Bewertung gespeichert"
                                        }.onFailure {
                                            message = "KI-Fehler: " + (it.message ?: "unbekannt")
                                        }
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mit KI bewerten") }
                    }
                    if (rec.aiSummary.isNotBlank()) {
                        item {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("KI-Auswertung", fontWeight = FontWeight.Bold)
                                    Text("Typ: " + rec.aiDocumentType)
                                    Text("Empfohlenes Ziel: " + rec.aiSuggestedTarget)
                                    Text(rec.aiSummary)
                                    if (rec.aiFacts.isNotEmpty()) Text("Fakten: " + rec.aiFacts.joinToString(" · "))
                                    if (rec.aiCaution.isNotBlank()) Text("Hinweis: " + rec.aiCaution)
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exporter.share((selected ?: rec).copy(ocrText = editableText), "health") },
                                modifier = Modifier.weight(1f)
                            ) { Text("An Gesundheit") }
                            OutlinedButton(
                                onClick = { exporter.share((selected ?: rec).copy(ocrText = editableText), "technician") },
                                modifier = Modifier.weight(1f)
                            ) { Text("An Techniker") }
                        }
                    }
                    message?.let { m -> item { Text(m) } }
                    item { TextButton(onClick = { selected = null }) { Text("Zur Dokumentliste") } }
                }
            }
        }
    }
}

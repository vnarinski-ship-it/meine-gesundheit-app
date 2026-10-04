package de.vnarinski.meinegesundheit

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.vnarinski.meinegesundheit.data.HealthRepository
import de.vnarinski.meinegesundheit.data.AiSettings
import de.vnarinski.meinegesundheit.data.AiSettingsRepository
import de.vnarinski.meinegesundheit.data.PortableBackupManager
import de.vnarinski.meinegesundheit.data.SecuritySettings
import de.vnarinski.meinegesundheit.data.SecuritySettingsRepository
import de.vnarinski.meinegesundheit.ai.OpenAiAnalyzer
import de.vnarinski.meinegesundheit.domain.*
import de.vnarinski.meinegesundheit.gps.DrivePrivacyMode
import de.vnarinski.meinegesundheit.gps.DriveTrackingService
import de.vnarinski.meinegesundheit.notifications.ReminderScheduler
import de.vnarinski.meinegesundheit.report.DoctorPdfExporter
import de.vnarinski.meinegesundheit.health.AndroidHealthConnectConnector
import de.vnarinski.meinegesundheit.export.HealthDataExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HealthRepository(app)
    private val aiSettingsRepo = AiSettingsRepository(app)
    private val backupManager = PortableBackupManager(app)
    private val securityRepo = SecuritySettingsRepository(app)
    private val healthConnector = AndroidHealthConnectConnector(app)
    private val dataExporter = HealthDataExporter(app)

    private val _documents = MutableStateFlow<List<MedicalDocument>>(emptyList())
    val documents = _documents.asStateFlow()

    private val _labs = MutableStateFlow<List<LabResult>>(emptyList())
    val labs = _labs.asStateFlow()

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications = _medications.asStateFlow()

    private val _meals = MutableStateFlow<List<Meal>>(emptyList())
    val meals = _meals.asStateFlow()

    private val _symptoms = MutableStateFlow<List<SymptomEntry>>(emptyList())
    val symptoms = _symptoms.asStateFlow()

    private val _drives = MutableStateFlow<List<DriveSession>>(emptyList())
    val drives = _drives.asStateFlow()

    private val _healthMetrics = MutableStateFlow<List<HealthMetric>>(emptyList())
    val healthMetrics = _healthMetrics.asStateFlow()

    private val _healthConnectStatus = MutableStateFlow("Nicht geprüft")
    val healthConnectStatus = _healthConnectStatus.asStateFlow()

    private val _appointments = MutableStateFlow<List<DoctorAppointment>>(emptyList())
    val appointments = _appointments.asStateFlow()

    private val _driveActive = MutableStateFlow(isDriveServiceMarkedActive(app))
    val driveActive = _driveActive.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _aiSettings = MutableStateFlow(aiSettingsRepo.load())
    val aiSettings = _aiSettings.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy = _aiBusy.asStateFlow()

    private val _pendingLabCandidates = MutableStateFlow<List<ExtractedLabCandidate>>(emptyList())
    val pendingLabCandidates = _pendingLabCandidates.asStateFlow()

    private val _securitySettings = MutableStateFlow(securityRepo.load())
    val securitySettings = _securitySettings.asStateFlow()


    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _documents.value = repo.loadDocuments()
        _labs.value = repo.loadLabs()
        _medications.value = repo.loadMedications()
        _meals.value = repo.loadMeals()
        _symptoms.value = repo.loadSymptoms()
        _drives.value = repo.loadDrives()
        _healthMetrics.value = repo.loadHealthMetrics()
        _appointments.value = repo.loadAppointments()
        _driveActive.value = isDriveServiceMarkedActive(getApplication())
    }

    fun importOcrHubPayload(uri: Uri) = viewModelScope.launch {
        runCatching { repo.importOcrHubPayload(uri) }
            .onSuccess {
                _message.value = "OCR-Hub-Daten in Gesundheit übernommen"
                refresh()
            }
            .onFailure { _message.value = "OCR-Import fehlgeschlagen: " + (it.message ?: "unbekannt") }
    }

    fun importDocument(uri: Uri) = viewModelScope.launch {
        runCatching { repo.importDocument(uri) }
            .onSuccess { _message.value = "Befund sicher gespeichert"; refresh() }
            .onFailure { _message.value = "Import fehlgeschlagen: ${it.message}" }
    }

    fun addLab(name: String, value: Double?, unit: String?, low: Double?, high: Double?) = viewModelScope.launch {
        if (name.isBlank()) { _message.value = "Bitte Laborwert benennen"; return@launch }
        repo.addLab(name, value, unit, low, high)
        _message.value = "Laborwert gespeichert"
        refresh()
    }

    fun addMedication(name: String, dose: String?, schedule: String?) = viewModelScope.launch {
        if (name.isBlank()) { _message.value = "Bitte Medikament benennen"; return@launch }
        repo.addMedication(name, dose, schedule)
        _message.value = "Medikament gespeichert"
        refresh()
    }

    fun importMealPhoto(uri: Uri) = viewModelScope.launch {
        runCatching { repo.importMealPhoto(uri) }
            .onSuccess { _message.value = "Essensfoto verschlüsselt gespeichert"; refresh() }
            .onFailure { _message.value = "Foto konnte nicht gespeichert werden: ${it.message}" }
    }

    fun addManualMeal(label: String, carbs: Double?, fiber: Double?, protein: Double?, fat: Double?, kcal: Double?) = viewModelScope.launch {
        if (label.isBlank()) { _message.value = "Bitte Mahlzeit benennen"; return@launch }
        repo.addManualMeal(label, carbs, fiber, protein, fat, kcal)
        _message.value = "Mahlzeit gespeichert"
        refresh()
    }

    fun addSymptom(bodyArea: String, symptom: String, intensity: Int, note: String?) = viewModelScope.launch {
        if (symptom.isBlank()) { _message.value = "Bitte Beschwerde benennen"; return@launch }
        repo.addSymptom(bodyArea, symptom, intensity, note)
        _message.value = "Beschwerde gespeichert"
        refresh()
    }

    fun addAppointment(startsAt: java.time.Instant, doctorName: String?, specialty: String?, reason: String, notes: String?) = viewModelScope.launch {
        if (reason.isBlank()) { _message.value = "Bitte Grund für den Termin eingeben"; return@launch }
        runCatching { repo.addAppointment(startsAt, doctorName, specialty, reason, notes) }
            .onSuccess { _message.value = "Arzttermin gespeichert"; refresh() }
            .onFailure { _message.value = "Termin konnte nicht gespeichert werden: ${it.message}" }
    }

    fun addAppointmentQuestion(appointmentId: String, question: String) = viewModelScope.launch {
        if (question.isBlank()) return@launch
        runCatching { repo.addAppointmentQuestion(appointmentId, question) }
            .onSuccess { _message.value = "Frage gespeichert"; refresh() }
            .onFailure { _message.value = "Frage konnte nicht gespeichert werden: ${it.message}" }
    }

    fun prepareDoctorSummary(appointmentId: String) = viewModelScope.launch {
        val appointment = _appointments.value.firstOrNull { it.id == appointmentId } ?: return@launch
        val now = java.time.Instant.now()
        val cutoff180 = now.minusSeconds(180L * 24L * 60L * 60L)
        val cutoff30 = now.minusSeconds(30L * 24L * 60L * 60L)
        val recentDocs = _documents.value.filter { it.documentDate >= cutoff180 }.sortedByDescending { it.documentDate }.take(8)
        val recentLabs = _labs.value.filter { it.measuredAt >= cutoff180 }.sortedByDescending { it.measuredAt }.take(20)
        val recentSymptoms = _symptoms.value.filter { it.at >= cutoff180 }.sortedByDescending { it.at }.take(15)
        val activeMeds = _medications.value.filter { it.active }.sortedBy { it.name.lowercase() }
        val recentDrives = _drives.value.filter { it.start >= cutoff30 }
        val driveMinutes = recentDrives.sumOf { it.durationMinutes ?: 0L }

        val summary = buildString {
            append("ARZTVORBEREITUNG\n")
            append("Termin: ").append(appointment.reason)
            appointment.specialty?.let { append(" · ").append(it) }
            appointment.doctorName?.let { append(" · ").append(it) }
            append("\n\nAktuelle Medikamente:\n")
            if (activeMeds.isEmpty()) append("• keine eingetragen\n") else activeMeds.forEach { m ->
                append("• ").append(m.name)
                listOfNotNull(m.dose, m.schedule).takeIf { it.isNotEmpty() }?.let { append(" – ").append(it.joinToString(" · ")) }
                append('\n')
            }
            append("\nLetzte Beschwerden (bis 180 Tage):\n")
            if (recentSymptoms.isEmpty()) append("• keine eingetragen\n") else recentSymptoms.take(8).forEach { x ->
                append("• ").append(x.bodyArea).append(": ").append(x.symptom).append(" · ").append(x.intensity0to10).append("/10")
                x.note?.let { append(" · ").append(it) }; append('\n')
            }
            append("\nRelevante Laborwerte (bis 180 Tage):\n")
            if (recentLabs.isEmpty()) append("• keine eingetragen\n") else recentLabs.take(12).forEach { x ->
                append("• ").append(x.name).append(": ")
                append(x.value?.toString() ?: x.textValue ?: "–")
                x.unit?.let { append(' ').append(it) }; append('\n')
            }
            append("\nBefunde / Arztbriefe (bis 180 Tage):\n")
            if (recentDocs.isEmpty()) append("• keine eingetragen\n") else recentDocs.forEach { d -> append("• ").append(d.title).append('\n') }
            if (recentDrives.isNotEmpty()) {
                append("\nArbeitsfahrten letzte 30 Tage: ").append(recentDrives.size)
                append(" · insgesamt ").append(driveMinutes / 60).append(" h ").append(driveMinutes % 60).append(" min\n")
            }
            if (appointment.questions.isNotEmpty()) {
                append("\nMeine Fragen:\n")
                appointment.questions.forEach { append("• ").append(it).append('\n') }
            }
            appointment.notes?.let { append("\nEigene Notiz:\n").append(it).append('\n') }
            append("\nHinweis: Automatisch aus den von dir gespeicherten Daten zusammengestellt. Vor dem Termin bitte prüfen.")
        }
        runCatching { repo.saveAppointmentPreparation(appointmentId, summary) }
            .onSuccess { _message.value = "Arzt-Zusammenfassung erstellt"; refresh() }
            .onFailure { _message.value = "Zusammenfassung konnte nicht gespeichert werden: ${it.message}" }
    }

    fun startDrive(mode: DrivePrivacyMode) {
        val app = getApplication<Application>()
        val intent = Intent(app, DriveTrackingService::class.java)
            .setAction(DriveTrackingService.ACTION_START)
            .putExtra(DriveTrackingService.EXTRA_PRIVACY_MODE, mode.name)
        ContextCompat.startForegroundService(app, intent)
        _driveActive.value = true
        _message.value = when (mode) {
            DrivePrivacyMode.TIME_ONLY -> "Fahrt gestartet · nur Fahrzeit"
            DrivePrivacyMode.TIME_AND_DISTANCE -> "Fahrt gestartet · Zeit + Kilometer"
            DrivePrivacyMode.FULL_ROUTE -> "Fahrt gestartet · komplette Route"
        }
    }

    fun stopDrive() {
        val app = getApplication<Application>()
        app.startService(Intent(app, DriveTrackingService::class.java).setAction(DriveTrackingService.ACTION_STOP))
        _driveActive.value = false
        _message.value = "Fahrt wird beendet und gespeichert"
        viewModelScope.launch {
            delay(1200)
            refresh()
        }
    }


    fun saveAiSettings(apiKey: String, model: String) = viewModelScope.launch {
        val settings = AiSettings(apiKey.trim(), model.trim().ifBlank { "gpt-5" })
        aiSettingsRepo.save(settings)
        _aiSettings.value = settings
        _message.value = "KI-Einstellungen verschlüsselt gespeichert"
    }

    fun analyzeMeal(mealId: String) = viewModelScope.launch {
        val settings = _aiSettings.value
        if (!settings.configured) { _message.value = "Bitte zuerst API-Schlüssel im KI-Bereich speichern"; return@launch }
        val meal = _meals.value.firstOrNull { it.id == mealId } ?: return@launch
        val uri = meal.photoUri ?: run { _message.value = "Diese Mahlzeit hat kein Foto"; return@launch }
        _aiBusy.value = true
        runCatching {
            val bytes = repo.readVaultUri(uri)
            OpenAiAnalyzer(settings.apiKey, settings.model).analyzeMeal(bytes)
        }.onSuccess { result ->
            repo.applyMealAiResult(mealId, result.items, result.summary, result.uncertainty)
            _message.value = "Essensfoto analysiert – Werte sind Schätzungen"
            refresh()
        }.onFailure { _message.value = "KI-Analyse fehlgeschlagen: ${it.message}" }
        _aiBusy.value = false
    }

    fun analyzeDocument(documentId: String) = viewModelScope.launch {
        val settings = _aiSettings.value
        if (!settings.configured) { _message.value = "Bitte zuerst API-Schlüssel im KI-Bereich speichern"; return@launch }
        val doc = _documents.value.firstOrNull { it.id == documentId } ?: return@launch
        _aiBusy.value = true
        runCatching {
            val bytes = repo.readVaultUri(doc.originalUri)
            OpenAiAnalyzer(settings.apiKey, settings.model).analyzeDocument(bytes, doc.title)
        }.onSuccess { result ->
            repo.applyDocumentAiSummary(documentId, result.summary, result.importantValues, result.questionsForDoctor)
            _pendingLabCandidates.value = result.labCandidates.map { c ->
                ExtractedLabCandidate(
                    tempId = java.util.UUID.randomUUID().toString(),
                    documentId = documentId,
                    name = c.name,
                    value = c.value,
                    textValue = c.textValue,
                    unit = c.unit,
                    referenceLow = c.referenceLow,
                    referenceHigh = c.referenceHigh,
                    measuredAt = doc.documentDate,
                    sourceText = c.sourceText
                )
            }
            _message.value = if (_pendingLabCandidates.value.isEmpty())
                "Befund analysiert – keine strukturierten Laborwerte erkannt"
            else "Befund analysiert – Laborwerte bitte prüfen und bestätigen"
            refresh()
        }.onFailure { _message.value = "KI-Analyse fehlgeschlagen: ${it.message}" }
        _aiBusy.value = false
    }

    fun confirmLabCandidates(ids: Set<String>) = viewModelScope.launch {
        val selected = _pendingLabCandidates.value.filter { it.tempId in ids }
        if (selected.isEmpty()) { _message.value = "Keine Laborwerte ausgewählt"; return@launch }
        repo.addConfirmedLabCandidates(selected)
        _pendingLabCandidates.value = _pendingLabCandidates.value.filterNot { it.tempId in ids }
        _message.value = "${selected.size} Laborwert(e) bestätigt und gespeichert"
        refresh()
    }

    fun discardLabCandidates(documentId: String) {
        _pendingLabCandidates.value = _pendingLabCandidates.value.filterNot { it.documentId == documentId }
        _message.value = "Erkannte Laborwerte verworfen"
    }

    fun scheduleMedicationReminder(medicationId: String, hour: Int, minute: Int) = viewModelScope.launch {
        val med = _medications.value.firstOrNull { it.id == medicationId } ?: return@launch
        val now = java.time.ZonedDateTime.now()
        var next = now.withHour(hour.coerceIn(0,23)).withMinute(minute.coerceIn(0,59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val reminder = HealthReminder(
            id = "med-$medicationId", kind = ReminderKind.MEDICATION_DAILY, targetId = medicationId,
            title = "Medikament: ${med.name}",
            body = listOfNotNull(med.dose, med.schedule).joinToString(" · ").ifBlank { "Einnahme prüfen" },
            triggerAt = next.toInstant(), repeatDaily = true
        )
        repo.saveReminder(reminder)
        ReminderScheduler.schedule(getApplication(), reminder)
        _message.value = "Tägliche Erinnerung ${"%02d:%02d".format(hour, minute)} gespeichert"
    }

    fun scheduleAppointmentReminder(appointmentId: String) = viewModelScope.launch {
        val appt = _appointments.value.firstOrNull { it.id == appointmentId } ?: return@launch
        val trigger = appt.startsAt.minusSeconds(24L * 60L * 60L)
        if (!trigger.isAfter(java.time.Instant.now())) {
            _message.value = "Termin ist weniger als 24 Stunden entfernt"
            return@launch
        }
        val reminder = HealthReminder(
            id = "appt-$appointmentId", kind = ReminderKind.APPOINTMENT, targetId = appointmentId,
            title = "Arzttermin morgen",
            body = listOfNotNull(appt.reason, appt.specialty, appt.doctorName).joinToString(" · "),
            triggerAt = trigger
        )
        repo.saveReminder(reminder)
        ReminderScheduler.schedule(getApplication(), reminder)
        _message.value = "Erinnerung 24 Stunden vorher gespeichert"
    }

    fun createDoctorPdf(appointmentId: String): android.net.Uri? {
        val appt = _appointments.value.firstOrNull { it.id == appointmentId } ?: return null
        val summary = appt.preparationSummary ?: run { _message.value = "Bitte zuerst Arzt-Zusammenfassung erstellen"; return null }
        return runCatching {
            DoctorPdfExporter.create(getApplication(), "Arztbericht-${appt.reason}", summary)
        }.onSuccess { _message.value = "PDF-Arztbericht erstellt" }
         .onFailure { _message.value = "PDF konnte nicht erstellt werden: ${it.message}" }
         .getOrNull()
    }


    fun createPortableBackup(uri: Uri, password: String) = viewModelScope.launch {
        if (password.length < 8) { _message.value = "Backup-Passwort muss mindestens 8 Zeichen haben"; return@launch }
        runCatching { backupManager.create(uri, password.toCharArray()) }
            .onSuccess { result ->
                securityRepo.markBackupCreated(result.createdAt)
                _securitySettings.value = securityRepo.load()
                _message.value = "Verschlüsseltes Backup erstellt · ${result.fileCount} Dateien"
            }
            .onFailure { _message.value = "Backup fehlgeschlagen: ${it.message}" }
    }

    fun restorePortableBackup(uri: Uri, password: String) = viewModelScope.launch {
        if (password.isBlank()) { _message.value = "Bitte Backup-Passwort eingeben"; return@launch }
        runCatching { backupManager.restore(uri, password.toCharArray()) }
            .onSuccess { result ->
                _message.value = "Backup wiederhergestellt · ${result.fileCount} Dateien"
                refresh()
                // Re-schedule local reminders that were restored into the new device vault.
                runCatching { repo.loadReminders().filter { it.enabled }.forEach { ReminderScheduler.schedule(getApplication(), it) } }
            }
            .onFailure { _message.value = "Wiederherstellung fehlgeschlagen: ${it.message}" }
    }
    fun setAppLockEnabled(enabled: Boolean) {
        securityRepo.setAppLockEnabled(enabled)
        _securitySettings.value = securityRepo.load()
        _message.value = if (enabled) "App-Sperre aktiviert" else "App-Sperre deaktiviert"
    }

    fun setBackupWarningDays(days: Int) {
        securityRepo.setBackupWarningDays(days)
        _securitySettings.value = securityRepo.load()
        _message.value = "Backup-Hinweis auf ${days.coerceIn(7,180)} Tage gesetzt"
    }

    fun checkBackupAgeAndNotify() {
        val s = securityRepo.load()
        _securitySettings.value = s
        val now = java.time.Instant.now()
        val tooOld = s.lastBackupAt?.let { java.time.Duration.between(it, now).toDays() >= s.backupWarningDays } ?: true
        if (tooOld) {
            _message.value = if (s.lastBackupAt == null)
                "Noch kein portables Backup erstellt"
            else "Dein letztes Backup ist älter als ${s.backupWarningDays} Tage"
        }
    }

    fun clearMessage() { _message.value = null }

    private fun isDriveServiceMarkedActive(context: Context): Boolean =
        context.getSharedPreferences(DriveTrackingService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(DriveTrackingService.KEY_ACTIVE, false)
    fun checkHealthConnect() = viewModelScope.launch {
        _healthConnectStatus.value = runCatching {
            when (healthConnector.sdkStatus()) {
                androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE ->
                    if (healthConnector.hasAllPermissions()) "Verbunden" else "Verfügbar – Berechtigungen fehlen"
                androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect aktualisieren"
                else -> "Auf diesem Gerät nicht verfügbar"
            }
        }.getOrElse { "Fehler: ${it.message}" }
    }

    fun syncHealthConnect() = viewModelScope.launch {
        _healthConnectStatus.value = "Synchronisiere …"
        runCatching {
            require(healthConnector.sdkStatus() == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) { "Health Connect nicht verfügbar" }
            require(healthConnector.hasAllPermissions()) { "Bitte Health-Connect-Berechtigungen erteilen" }
            val metrics = healthConnector.readLast30Days()
            repo.upsertHealthMetrics(metrics)
            metrics.size
        }.onSuccess { count ->
            _healthConnectStatus.value = "Verbunden · letzte Synchronisation: $count Datensätze"
            _message.value = "$count Health-Connect-Datensätze synchronisiert"
            refresh()
        }.onFailure {
            _healthConnectStatus.value = "Synchronisation fehlgeschlagen"
            _message.value = it.message ?: "Health-Connect-Synchronisation fehlgeschlagen"
        }
    }

    private suspend fun exportSnapshot() = HealthDataExporter.Snapshot(
        documents = repo.loadDocuments(), labs = repo.loadLabs(), meds = repo.loadMedications(),
        symptoms = repo.loadSymptoms(), meals = repo.loadMeals(), drives = repo.loadDrives(),
        appointments = repo.loadAppointments(), healthMetrics = repo.loadHealthMetrics()
    )

    fun createCsvExport(): android.net.Uri? = runCatching {
        kotlinx.coroutines.runBlocking { dataExporter.uri(dataExporter.csv(exportSnapshot())) }
    }.onFailure { _message.value = "CSV-Export fehlgeschlagen: ${it.message}" }.getOrNull()

    fun createFhirExport(): android.net.Uri? = runCatching {
        kotlinx.coroutines.runBlocking { dataExporter.uri(dataExporter.fhir(exportSnapshot())) }
    }.onFailure { _message.value = "FHIR-Export fehlgeschlagen: ${it.message}" }.getOrNull()

    fun createHealthPdfExport(): android.net.Uri? = runCatching {
        kotlinx.coroutines.runBlocking { dataExporter.uri(dataExporter.pdf(exportSnapshot())) }
    }.onFailure { _message.value = "PDF-Export fehlgeschlagen: ${it.message}" }.getOrNull()

}

package de.vnarinski.meinegesundheit.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.vnarinski.meinegesundheit.AppViewModel
import de.vnarinski.meinegesundheit.gps.DrivePrivacyMode
import de.vnarinski.meinegesundheit.health.AndroidHealthConnectConnector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun fmt(i: Instant): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(i)

@Composable
fun LockScreen(error: String?, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🔒 Meine Gesundheit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Die App ist gesperrt.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onUnlock) { Text("Entsperren") }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, onFood: () -> Unit, onHealth: () -> Unit, onDrive: () -> Unit, onAi: () -> Unit, onBackup: () -> Unit) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val labs by vm.labs.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Meine Gesundheit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("Aktive Medikamente: " + meds.count { it.active } + " · Laborwerte: " + labs.size) }
        item { Button(onClick = onFood, modifier = Modifier.fillMaxWidth()) { Text("📷 Essen fotografieren") } }
        item { Button(onClick = onHealth, modifier = Modifier.fillMaxWidth()) { Text("❤️ Gesundheit") } }
        item { OutlinedButton(onClick = onDrive, modifier = Modifier.fillMaxWidth()) { Text("🚗 Arbeitsfahrt") } }
        item { OutlinedButton(onClick = onAi, modifier = Modifier.fillMaxWidth()) { Text("✨ KI") } }
        item { OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("🔐 Backup") } }
    }
}

@Composable
fun HealthScreen(nav: NavController) {
    val entries = listOf(
        "documents" to "Befunde & Arztbriefe",
        "labs" to "Laborwerte",
        "medications" to "Medikamente",
        "symptoms" to "Beschwerden",
        "appointments" to "Arzttermine",
        "security" to "Sicherheit",
        "backup" to "Backup & Handywechsel",
        "interop" to "Health Connect & Export"
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Gesundheit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        entries.forEach { e -> item { OutlinedButton(onClick = { nav.navigate(e.first) }, modifier = Modifier.fillMaxWidth()) { Text(e.second) } } }
    }
}

@Composable
fun DocumentsScreen(vm: AppViewModel) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.importDocument(u) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Befunde", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("+ Befund importieren") } }
        items(docs, key = { it.id }) { d ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(d.title, fontWeight = FontWeight.SemiBold)
                    Text(fmt(d.documentDate))
                    d.aiSummary?.let { Text(it) }
                    TextButton(onClick = { vm.analyzeDocument(d.id) }) { Text("Mit KI analysieren") }
                }
            }
        }
    }
}

@Composable
fun LabsScreen(vm: AppViewModel) {
    val data by vm.labs.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Laborwerte", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value, { value = it }, label = { Text("Wert") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(unit, { unit = it }, label = { Text("Einheit") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { vm.addLab(name, value.replace(',', '.').toDoubleOrNull(), unit, null, null); name = ""; value = ""; unit = "" }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") } }
        items(data, key = { it.id }) { x -> ListItem(headlineContent = { Text(x.name) }, supportingContent = { Text((x.value ?: x.textValue ?: "–").toString() + " " + (x.unit ?: "")) }) }
    }
}

@Composable
fun MedicationsScreen(vm: AppViewModel) {
    val data by vm.medications.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Medikamente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(name, { name = it }, label = { Text("Medikament") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(dose, { dose = it }, label = { Text("Dosis") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(schedule, { schedule = it }, label = { Text("Einnahme") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { vm.addMedication(name, dose, schedule); name = ""; dose = ""; schedule = "" }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") } }
        items(data, key = { it.id }) { m -> ListItem(headlineContent = { Text(m.name) }, supportingContent = { Text(listOfNotNull(m.dose, m.schedule).joinToString(" · ")) }) }
    }
}

@Composable
fun SymptomsScreen(vm: AppViewModel) {
    val data by vm.symptoms.collectAsStateWithLifecycle()
    var body by remember { mutableStateOf("") }
    var symptom by remember { mutableStateOf("") }
    var intensity by remember { mutableFloatStateOf(3f) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Beschwerden", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(body, { body = it }, label = { Text("Körperbereich") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(symptom, { symptom = it }, label = { Text("Beschwerde") }, modifier = Modifier.fillMaxWidth()) }
        item { Text("Stärke: " + intensity.toInt() + "/10"); Slider(intensity, { intensity = it }, valueRange = 0f..10f, steps = 9) }
        item { Button(onClick = { vm.addSymptom(body, symptom, intensity.toInt(), null); symptom = "" }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") } }
        items(data, key = { it.id }) { x -> ListItem(headlineContent = { Text(x.bodyArea + ": " + x.symptom) }, supportingContent = { Text(x.intensity0to10.toString() + "/10 · " + fmt(x.at)) }) }
    }
}

@Composable
fun FoodScreen(vm: AppViewModel) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importMealPhoto(u) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Ernährung / Low Carb", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("📷 Essensfoto auswählen") } }
        items(data, key = { it.id }) { m ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(fmt(m.at), fontWeight = FontWeight.SemiBold)
                    Text("Netto-KH ca. " + "%.1f".format(m.netCarbsG) + " g")
                    m.note?.let { Text(it) }
                    if (m.photoUri != null) TextButton(onClick = { vm.analyzeMeal(m.id) }) { Text("Mit KI analysieren") }
                }
            }
        }
    }
}

@Composable
fun TimelineScreen(vm: AppViewModel) {
    val labs by vm.labs.collectAsStateWithLifecycle()
    val symptoms by vm.symptoms.collectAsStateWithLifecycle()
    val drives by vm.drives.collectAsStateWithLifecycle()
    val rows = (labs.map { Triple(it.measuredAt, "Labor", it.name) } +
            symptoms.map { Triple(it.at, "Beschwerde", it.bodyArea + ": " + it.symptom) } +
            drives.map { Triple(it.start, "Fahrt", (it.durationMinutes ?: 0).toString() + " min") }).sortedByDescending { it.first }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Verlauf", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(rows) { r -> ListItem(headlineContent = { Text(r.third) }, supportingContent = { Text(r.second + " · " + fmt(r.first)) }) }
    }
}

@Composable
fun AiScreen(vm: AppViewModel) {
    val settings by vm.aiSettings.collectAsStateWithLifecycle()
    var key by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("KI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Daten werden nur nach deiner Aktion an die KI gesendet.")
        OutlinedTextField(key, { key = it }, label = { Text("OpenAI API-Schlüssel") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, label = { Text("Modell") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveAiSettings(key, model) }, modifier = Modifier.fillMaxWidth()) { Text("Sicher speichern") }
    }
}

@Composable
fun DriveScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val active by vm.driveActive.collectAsStateWithLifecycle()
    val data by vm.drives.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(DrivePrivacyMode.TIME_AND_DISTANCE) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted -> if (granted.values.any { it }) vm.startDrive(mode) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Arbeitsfahrten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DrivePrivacyMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(if (m == DrivePrivacyMode.TIME_ONLY) "nur Zeit" else if (m == DrivePrivacyMode.TIME_AND_DISTANCE) "Zeit + km" else "Route") }) }
        } }
        item { Button(onClick = {
            if (active) vm.stopDrive() else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) vm.startDrive(mode)
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, modifier = Modifier.fillMaxWidth()) { Text(if (active) "Fahrt beenden" else "Fahrt starten") } }
        items(data, key = { it.id }) { d -> ListItem(headlineContent = { Text((d.durationMinutes ?: 0).toString() + " min") }, supportingContent = { Text(fmt(d.start)) }) }
    }
}

@Composable
fun DoctorAppointmentsScreen(vm: AppViewModel) {
    val data by vm.appointments.collectAsStateWithLifecycle()
    var reason by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Arzttermine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(reason, { reason = it }, label = { Text("Grund") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { vm.addAppointment(Instant.now().plusSeconds(7 * 86400), null, null, reason, null); reason = "" }, modifier = Modifier.fillMaxWidth()) { Text("Termin in 7 Tagen anlegen") } }
        items(data, key = { it.id }) { a -> ListItem(headlineContent = { Text(a.reason) }, supportingContent = { Text(fmt(a.startsAt)) }) }
    }
}

@Composable
fun BackupScreen(vm: AppViewModel) {
    var password by remember { mutableStateOf("") }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u -> if (u != null) vm.createPortableBackup(u, password) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.restorePortableBackup(u, password) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Backup & Handywechsel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(password, { password = it }, label = { Text("Backup-Passwort") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { create.launch("meine-gesundheit-backup.mgh") }, modifier = Modifier.fillMaxWidth()) { Text("Backup erstellen") }
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Backup wiederherstellen") }
    }
}

@Composable
fun SecurityScreen(vm: AppViewModel) {
    val s by vm.securitySettings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sicherheit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("App-Sperre", Modifier.weight(1f)); Switch(s.appLockEnabled, vm::setAppLockEnabled) }
        Text("Gesundheitsdaten werden lokal verschlüsselt gespeichert.")
    }
}

@Composable
fun InteropScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val status by vm.healthConnectStatus.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(AndroidHealthConnectConnector.permissionContract()) { granted ->
        if (granted.containsAll(AndroidHealthConnectConnector.permissions)) vm.syncHealthConnect() else vm.checkHealthConnect()
    }
    fun share(uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        ctx.startActivity(Intent.createChooser(intent, "Export teilen"))
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Health Connect & Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("Status: " + status) }
        item { Button(onClick = { permission.launch(AndroidHealthConnectConnector.permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Health Connect verbinden") } }
        item { OutlinedButton(onClick = vm::syncHealthConnect, modifier = Modifier.fillMaxWidth()) { Text("Synchronisieren") } }
        item { Button(onClick = { vm.createCsvExport()?.let { share(it, "text/csv") } }, modifier = Modifier.fillMaxWidth()) { Text("CSV exportieren") } }
        item { Button(onClick = { vm.createFhirExport()?.let { share(it, "application/json") } }, modifier = Modifier.fillMaxWidth()) { Text("FHIR R4 exportieren") } }
        item { Button(onClick = { vm.createHealthPdfExport()?.let { share(it, "application/pdf") } }, modifier = Modifier.fillMaxWidth()) { Text("PDF exportieren") } }
    }
}

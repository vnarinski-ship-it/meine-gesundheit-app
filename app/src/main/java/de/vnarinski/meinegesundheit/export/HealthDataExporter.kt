package de.vnarinski.meinegesundheit.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import de.vnarinski.meinegesundheit.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HealthDataExporter(private val context: Context) {
    data class Snapshot(
        val documents: List<MedicalDocument>, val labs: List<LabResult>, val meds: List<Medication>,
        val symptoms: List<SymptomEntry>, val meals: List<Meal>, val drives: List<DriveSession>,
        val appointments: List<DoctorAppointment>, val healthMetrics: List<HealthMetric>
    )

    private fun exportFile(name: String) = File(context.cacheDir, "exports").apply { mkdirs() }.resolve(name)
    fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun csv(snapshot: Snapshot): File {
        val f = exportFile("meine-gesundheit-export.csv")
        f.bufferedWriter().use { w ->
            w.appendLine("category;date;name;value;unit;source")
            fun esc(x: Any?) = "\"${(x?.toString() ?: "").replace("\"", "\"\"")}\""
            snapshot.labs.forEach { w.appendLine(listOf("labor",it.measuredAt,it.name,it.value ?: it.textValue,it.unit,it.provenance.sourceName).joinToString(";") { x -> esc(x) }) }
            snapshot.symptoms.forEach { w.appendLine(listOf("symptom",it.at,"${it.bodyArea}: ${it.symptom}",it.intensity0to10,"0-10",it.provenance.sourceName).joinToString(";") { x -> esc(x) }) }
            snapshot.healthMetrics.forEach { w.appendLine(listOf("health_connect",it.start,it.type,it.value ?: it.textValue,it.unit,it.provenance.sourceName).joinToString(";") { x -> esc(x) }) }
            snapshot.meals.forEach { w.appendLine(listOf("meal",it.at,"Mahlzeit",it.netCarbsG,"g Netto-KH",it.provenance.sourceName).joinToString(";") { x -> esc(x) }) }
            snapshot.drives.forEach { w.appendLine(listOf("drive",it.start,"Fahrt",it.durationMinutes,"min",it.provenance.sourceName).joinToString(";") { x -> esc(x) }) }
        }
        return f
    }

    fun fhir(snapshot: Snapshot): File {
        val entries = JSONArray()
        snapshot.labs.forEach { x ->
            val resource = JSONObject().apply {
                put("resourceType", "Observation"); put("id", x.id); put("status", "final")
                put("code", JSONObject().put("text", x.name).apply {
                    x.code?.let { c -> put("coding", JSONArray().put(JSONObject().put("system", x.codeSystem ?: "http://loinc.org").put("code", c))) }
                })
                put("effectiveDateTime", x.measuredAt.toString())
                if (x.value != null) put("valueQuantity", JSONObject().put("value", x.value).put("unit", x.unit))
                else x.textValue?.let { put("valueString", it) }
            }
            entries.put(JSONObject().put("resource", resource))
        }
        snapshot.meds.forEach { m ->
            val resource = JSONObject().apply {
                put("resourceType", "MedicationStatement"); put("id", m.id); put("status", if (m.active) "active" else "completed")
                put("medicationCodeableConcept", JSONObject().put("text", m.name))
                m.start?.let { put("effectivePeriod", JSONObject().put("start", it.toString()).apply { m.end?.let { e -> put("end", e.toString()) } }) }
                if (!m.dose.isNullOrBlank() || !m.schedule.isNullOrBlank()) put("note", JSONArray().put(JSONObject().put("text", listOfNotNull(m.dose,m.schedule).joinToString(" · "))))
            }
            entries.put(JSONObject().put("resource", resource))
        }
        val bundle = JSONObject().put("resourceType","Bundle").put("type","collection").put("entry",entries)
        val f = exportFile("meine-gesundheit-fhir-r4.json")
        f.writeText(bundle.toString(2))
        return f
    }

    fun pdf(snapshot: Snapshot): File {
        val f = exportFile("meine-gesundheit-uebersicht.pdf")
        val pdf = PdfDocument(); var pageNo = 1; var page: PdfDocument.Page? = null; var y = 0
        val paint = Paint().apply { textSize = 11f }
        fun newPage() { page?.let { pdf.finishPage(it) }; page = pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo++).create()); y = 45 }
        fun line(t: String, bold: Boolean=false) { if (page == null || y > 800) newPage(); paint.isFakeBoldText = bold; page!!.canvas.drawText(t.take(92), 36f, y.toFloat(), paint); y += 18 }
        newPage(); line("Meine Gesundheit – Datenübersicht", true); line("Export: ${java.time.Instant.now()}"); y += 8
        line("Aktive Medikamente", true); snapshot.meds.filter { it.active }.forEach { line("• ${it.name} ${it.dose ?: ""} ${it.schedule ?: ""}") }
        y += 6; line("Letzte Laborwerte", true); snapshot.labs.sortedByDescending { it.measuredAt }.take(20).forEach { line("• ${it.name}: ${it.value ?: it.textValue ?: "–"} ${it.unit ?: ""}") }
        y += 6; line("Letzte Beschwerden", true); snapshot.symptoms.sortedByDescending { it.at }.take(15).forEach { line("• ${it.bodyArea}: ${it.symptom} ${it.intensity0to10}/10") }
        y += 6; line("Health Connect", true); snapshot.healthMetrics.sortedByDescending { it.start }.take(20).forEach { line("• ${it.type}: ${it.value ?: it.textValue ?: "–"} ${it.unit ?: ""}") }
        page?.let { pdf.finishPage(it) }; f.outputStream().use { pdf.writeTo(it) }; pdf.close(); return f
    }
}

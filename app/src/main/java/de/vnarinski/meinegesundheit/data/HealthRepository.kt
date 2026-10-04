package de.vnarinski.meinegesundheit.data

import android.content.Context
import android.net.Uri
import de.vnarinski.meinegesundheit.domain.*
import de.vnarinski.meinegesundheit.gps.DrivePrivacyMode
import de.vnarinski.meinegesundheit.gps.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class HealthRepository(private val context: Context) {
    private val vault = EncryptedVault(context)
    private val metaFile = "health-data.json.enc"

    private data class State(
        val documents: MutableList<MedicalDocument> = mutableListOf(),
        val labs: MutableList<LabResult> = mutableListOf(),
        val meds: MutableList<Medication> = mutableListOf(),
        val meals: MutableList<Meal> = mutableListOf(),
        val symptoms: MutableList<SymptomEntry> = mutableListOf(),
        val drives: MutableList<DriveSession> = mutableListOf(),
        val appointments: MutableList<DoctorAppointment> = mutableListOf(),
        val reminders: MutableList<HealthReminder> = mutableListOf(),
        val metrics: MutableList<HealthMetric> = mutableListOf()
    )

    private var state: State? = null
    private fun s(): State = state ?: decode().also { state = it }

    suspend fun loadDocuments() = withContext(Dispatchers.IO) { s().documents.toList() }
    suspend fun loadLabs() = withContext(Dispatchers.IO) { s().labs.toList() }
    suspend fun loadMedications() = withContext(Dispatchers.IO) { s().meds.toList() }
    suspend fun loadMeals() = withContext(Dispatchers.IO) { s().meals.toList() }
    suspend fun loadSymptoms() = withContext(Dispatchers.IO) { s().symptoms.toList() }
    suspend fun loadDrives() = withContext(Dispatchers.IO) { s().drives.toList() }
    suspend fun loadAppointments() = withContext(Dispatchers.IO) { s().appointments.toList() }
    suspend fun loadReminders() = withContext(Dispatchers.IO) { s().reminders.toList() }
    suspend fun loadHealthMetrics() = withContext(Dispatchers.IO) { s().metrics.toList() }


    suspend fun importOcrHubPayload(uri: Uri): MedicalDocument = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("OCR-Daten konnten nicht gelesen werden")
        val json = JSONObject(raw.toString(Charsets.UTF_8))
        require(json.optInt("schemaVersion", 0) == 1) { "Unbekanntes OCR-Hub-Format" }
        val id = UUID.randomUUID().toString()
        val blob = "ocr-hub-$id.json.enc"
        vault.write(blob, raw)
        val title = json.optString("fileName").ifBlank { "OCR Hub Dokument" }
        val ocrText = json.optString("ocrText")
        val importedAt = json.optString("importedAt").let { runCatching { Instant.parse(it) }.getOrElse { Instant.now() } }
        val item = MedicalDocument(
            id = id,
            title = title,
            documentDate = importedAt,
            originalUri = "vault://$blob",
            aiSummary = if (ocrText.isBlank()) "OCR Hub: kein erkannter Text" else "OCR Hub Text:\n$ocrText",
            provenance = Provenance(DataOrigin.LAB_IMPORT, "OCR Hub", Confidence.USER_CONFIRMED)
        )
        s().documents.add(0, item)
        save()
        item
    }

    suspend fun importDocument(uri: Uri): MedicalDocument = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Dokument konnte nicht gelesen werden")
        val id = UUID.randomUUID().toString()
        val blob = "document-$id.bin.enc"
        vault.write(blob, bytes)
        val item = MedicalDocument(id, "Befund ${Instant.now()}", Instant.now(), originalUri = "vault://$blob",
            provenance = Provenance(DataOrigin.MANUAL, "Import", Confidence.USER_CONFIRMED))
        s().documents.add(0, item); save(); item
    }

    suspend fun addLab(name: String, value: Double?, unit: String?, low: Double?, high: Double?): LabResult = withContext(Dispatchers.IO) {
        val item = LabResult(UUID.randomUUID().toString(), name=name.trim(), value=value, unit=unit, referenceLow=low,
            referenceHigh=high, measuredAt=Instant.now(), provenance=Provenance(DataOrigin.MANUAL, confidence=Confidence.USER_CONFIRMED))
        s().labs.add(0,item); save(); item
    }

    suspend fun addMedication(name: String, dose: String?, schedule: String?): Medication = withContext(Dispatchers.IO) {
        val item=Medication(UUID.randomUUID().toString(),name.trim(),dose,schedule,Instant.now(),active=true,
            provenance=Provenance(DataOrigin.MANUAL,confidence=Confidence.USER_CONFIRMED))
        s().meds.add(0,item); save(); item
    }

    suspend fun importMealPhoto(uri: Uri): Meal = withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("Foto konnte nicht gelesen werden")
        val id=UUID.randomUUID().toString(); val blob="meal-$id.jpg.enc"; vault.write(blob,bytes)
        val item=Meal(id,Instant.now(),"vault://$blob",emptyList(),"Foto gespeichert – KI-Analyse noch nicht bestätigt.",
            Provenance(DataOrigin.CAMERA_AI,"Kamera",Confidence.ESTIMATED))
        s().meals.add(0,item); save(); item
    }

    suspend fun addManualMeal(label:String, carbs:Double?, fiber:Double?, protein:Double?, fat:Double?, kcal:Double?): Meal = withContext(Dispatchers.IO) {
        val item=Meal(UUID.randomUUID().toString(),Instant.now(),items=listOf(FoodItemEstimate(label,null,carbs,fiber,protein,fat,kcal)),
            provenance=Provenance(DataOrigin.MANUAL,confidence=Confidence.USER_CONFIRMED))
        s().meals.add(0,item); save(); item
    }

    suspend fun addSymptom(bodyArea:String,symptom:String,intensity:Int,note:String?): SymptomEntry = withContext(Dispatchers.IO) {
        val item=SymptomEntry(UUID.randomUUID().toString(),bodyArea.ifBlank{"Allgemein"},symptom,intensity.coerceIn(0,10),note,Instant.now(),
            Provenance(DataOrigin.MANUAL,confidence=Confidence.USER_CONFIRMED))
        s().symptoms.add(0,item); save(); item
    }

    suspend fun addDriveSession(start:Instant,end:Instant,distanceMeters:Double?,privacyMode:DrivePrivacyMode,route:List<RoutePoint>): DriveSession = withContext(Dispatchers.IO) {
        val id=UUID.randomUUID().toString()
        if(privacyMode==DrivePrivacyMode.FULL_ROUTE && route.isNotEmpty()){
            val arr=JSONArray(); route.forEach{p->arr.put(JSONObject().put("lat",p.lat).put("lon",p.lon).put("at",p.atEpochMillis).put("acc",p.accuracyMeters))}
            vault.write("route-$id.json.enc",arr.toString().toByteArray())
        }
        val item=DriveSession(id,start,end,distanceMeters,null,privacyMode.name,route.size,Provenance(DataOrigin.GPS,"GPS",Confidence.MEASURED))
        s().drives.add(0,item); save(); item
    }

    suspend fun addAppointment(startsAt:Instant,doctorName:String?,specialty:String?,reason:String,notes:String?): DoctorAppointment = withContext(Dispatchers.IO) {
        val item=DoctorAppointment(UUID.randomUUID().toString(),startsAt,doctorName,specialty,reason,notes=notes,
            provenance=Provenance(DataOrigin.MANUAL,confidence=Confidence.USER_CONFIRMED))
        s().appointments.add(0,item); save(); item
    }

    suspend fun addAppointmentQuestion(id:String,q:String)=withContext(Dispatchers.IO){
        val i=s().appointments.indexOfFirst{it.id==id}; if(i>=0){s().appointments[i]=s().appointments[i].copy(questions=s().appointments[i].questions+q);save()}
    }

    suspend fun saveAppointmentPreparation(id:String,text:String)=withContext(Dispatchers.IO){
        val i=s().appointments.indexOfFirst{it.id==id}; if(i>=0){s().appointments[i]=s().appointments[i].copy(preparationSummary=text);save()}
    }

    suspend fun saveReminder(r:HealthReminder)=withContext(Dispatchers.IO){
        s().reminders.removeAll{it.id==r.id}; s().reminders.add(r); save()
    }

    suspend fun upsertHealthMetrics(items:List<HealthMetric>)=withContext(Dispatchers.IO){
        val m=s().metrics.associateBy{it.id}.toMutableMap(); items.forEach{m[it.id]=it}; s().metrics.clear(); s().metrics.addAll(m.values.sortedByDescending{it.start}); save()
    }

    suspend fun applyMealAiResult(id:String,items:List<FoodItemEstimate>,summary:String,uncertainty:String)=withContext(Dispatchers.IO){
        val i=s().meals.indexOfFirst{it.id==id}; if(i>=0){val old=s().meals[i];s().meals[i]=old.copy(items=items,note=listOf(summary,uncertainty).filter{it.isNotBlank()}.joinToString(" · "),
            provenance=old.provenance.copy(confidence=Confidence.ESTIMATED));save()}
    }

    suspend fun applyDocumentAiSummary(id:String,summary:String,important:List<String>,questions:List<String>)=withContext(Dispatchers.IO){
        val i=s().documents.indexOfFirst{it.id==id}; if(i>=0){val extra=listOfNotNull(summary.takeIf{it.isNotBlank()},important.takeIf{it.isNotEmpty()}?.joinToString("; ",prefix="Werte: "),
            questions.takeIf{it.isNotEmpty()}?.joinToString("; ",prefix="Arztfragen: ")).joinToString("\n");s().documents[i]=s().documents[i].copy(aiSummary=extra);save()}
    }

    suspend fun addConfirmedLabCandidates(items:List<ExtractedLabCandidate>)=withContext(Dispatchers.IO){
        items.forEach{c->s().labs.add(0,LabResult(UUID.randomUUID().toString(),name=c.name,value=c.value,textValue=c.textValue,unit=c.unit,
            referenceLow=c.referenceLow,referenceHigh=c.referenceHigh,measuredAt=c.measuredAt,
            provenance=Provenance(DataOrigin.PDF_AI,"PDF/KI + bestätigt",Confidence.USER_CONFIRMED))) };save()
    }

    fun readVaultUri(uri:String):ByteArray { require(uri.startsWith("vault://")); return vault.read(uri.removePrefix("vault://"))?:error("Datei fehlt") }

    private fun save(){
        val root=JSONObject()
        root.put("documents",JSONArray(s().documents.map{JSONObject().put("id",it.id).put("title",it.title).put("date",it.documentDate.toString()).put("uri",it.originalUri).put("summary",it.aiSummary)}))
        root.put("labs",JSONArray(s().labs.map{JSONObject().put("id",it.id).put("name",it.name).put("value",it.value).put("text",it.textValue).put("unit",it.unit).put("low",it.referenceLow).put("high",it.referenceHigh).put("at",it.measuredAt.toString())}))
        root.put("meds",JSONArray(s().meds.map{JSONObject().put("id",it.id).put("name",it.name).put("dose",it.dose).put("schedule",it.schedule).put("active",it.active)}))
        root.put("meals",JSONArray(s().meals.map{m->JSONObject().put("id",m.id).put("at",m.at.toString()).put("photo",m.photoUri).put("note",m.note).put("items",JSONArray(m.items.map{i->JSONObject().put("name",i.name).put("grams",i.grams).put("carbs",i.carbsG).put("fiber",i.fiberG).put("protein",i.proteinG).put("fat",i.fatG).put("kcal",i.kcal)}))}))
        root.put("symptoms",JSONArray(s().symptoms.map{JSONObject().put("id",it.id).put("body",it.bodyArea).put("symptom",it.symptom).put("intensity",it.intensity0to10).put("note",it.note).put("at",it.at.toString())}))
        root.put("drives",JSONArray(s().drives.map{JSONObject().put("id",it.id).put("start",it.start.toString()).put("end",it.end?.toString()).put("distance",it.distanceMeters).put("privacy",it.privacyMode).put("routeCount",it.routePointCount)}))
        root.put("appointments",JSONArray(s().appointments.map{JSONObject().put("id",it.id).put("startsAt",it.startsAt.toString()).put("doctor",it.doctorName).put("specialty",it.specialty).put("reason",it.reason).put("notes",it.notes).put("prep",it.preparationSummary).put("questions",JSONArray(it.questions))}))
        root.put("reminders",JSONArray(s().reminders.map{JSONObject().put("id",it.id).put("kind",it.kind.name).put("targetId",it.targetId).put("title",it.title).put("body",it.body).put("triggerAt",it.triggerAt.toString()).put("repeatDaily",it.repeatDaily).put("enabled",it.enabled)}))
        root.put("metrics",JSONArray(s().metrics.map{JSONObject().put("id",it.id).put("type",it.type).put("value",it.value).put("text",it.textValue).put("unit",it.unit).put("start",it.start.toString()).put("end",it.end?.toString()).put("source",it.provenance.sourceName).put("externalId",it.provenance.externalId)}))
        vault.write(metaFile,root.toString().toByteArray())
    }

    private fun decode():State{
        val bytes=vault.read(metaFile)?:return State()
        return runCatching{
            val r=JSONObject(bytes.toString(Charsets.UTF_8)); val st=State()
            fun arr(k:String)=r.optJSONArray(k)?:JSONArray()
            for(i in 0 until arr("documents").length()){val o=arr("documents").getJSONObject(i);st.documents+=MedicalDocument(o.getString("id"),o.getString("title"),Instant.parse(o.getString("date")),originalUri=o.getString("uri"),aiSummary=sn(o,"summary"),provenance=prov())}
            for(i in 0 until arr("labs").length()){val o=arr("labs").getJSONObject(i);st.labs+=LabResult(o.getString("id"),name=o.getString("name"),value=dn(o,"value"),textValue=sn(o,"text"),unit=sn(o,"unit"),referenceLow=dn(o,"low"),referenceHigh=dn(o,"high"),measuredAt=Instant.parse(o.getString("at")),provenance=prov())}
            for(i in 0 until arr("meds").length()){val o=arr("meds").getJSONObject(i);st.meds+=Medication(o.getString("id"),o.getString("name"),sn(o,"dose"),sn(o,"schedule"),active=o.optBoolean("active",true),provenance=prov())}
            for(i in 0 until arr("meals").length()){val o=arr("meals").getJSONObject(i);val a=o.optJSONArray("items")?:JSONArray();val items=buildList{for(j in 0 until a.length()){val x=a.getJSONObject(j);add(FoodItemEstimate(x.getString("name"),dn(x,"grams"),dn(x,"carbs"),dn(x,"fiber"),dn(x,"protein"),dn(x,"fat"),dn(x,"kcal")))}};st.meals+=Meal(o.getString("id"),Instant.parse(o.getString("at")),sn(o,"photo"),items,sn(o,"note"),prov())}
            for(i in 0 until arr("symptoms").length()){val o=arr("symptoms").getJSONObject(i);st.symptoms+=SymptomEntry(o.getString("id"),o.optString("body","Allgemein"),o.getString("symptom"),o.optInt("intensity"),sn(o,"note"),Instant.parse(o.getString("at")),prov())}
            for(i in 0 until arr("drives").length()){val o=arr("drives").getJSONObject(i);st.drives+=DriveSession(o.getString("id"),Instant.parse(o.getString("start")),sn(o,"end")?.let(Instant::parse),dn(o,"distance"),null,o.optString("privacy","TIME_AND_DISTANCE"),o.optInt("routeCount"),Provenance(DataOrigin.GPS,"GPS",Confidence.MEASURED))}
            for(i in 0 until arr("appointments").length()){val o=arr("appointments").getJSONObject(i);val q=o.optJSONArray("questions")?:JSONArray();val qs=buildList{for(j in 0 until q.length())add(q.optString(j))};st.appointments+=DoctorAppointment(o.getString("id"),Instant.parse(o.getString("startsAt")),sn(o,"doctor"),sn(o,"specialty"),o.getString("reason"),qs,sn(o,"notes"),sn(o,"prep"),provenance=prov())}
            for(i in 0 until arr("reminders").length()){val o=arr("reminders").getJSONObject(i);st.reminders+=HealthReminder(o.getString("id"),ReminderKind.valueOf(o.getString("kind")),o.getString("targetId"),o.getString("title"),o.getString("body"),Instant.parse(o.getString("triggerAt")),o.optBoolean("repeatDaily"),o.optBoolean("enabled",true))}
            for(i in 0 until arr("metrics").length()){val o=arr("metrics").getJSONObject(i);st.metrics+=HealthMetric(o.getString("id"),o.getString("type"),dn(o,"value"),sn(o,"text"),sn(o,"unit"),Instant.parse(o.getString("start")),sn(o,"end")?.let(Instant::parse),Provenance(DataOrigin.HEALTH_CONNECT,sn(o,"source"),Confidence.MEASURED,externalId=sn(o,"externalId")))}
            st
        }.getOrElse{State()}
    }

    private fun prov()=Provenance(DataOrigin.MANUAL,confidence=Confidence.USER_CONFIRMED)
    private fun sn(o:JSONObject,k:String)=if(!o.has(k)||o.isNull(k))null else o.optString(k).takeIf{it.isNotBlank()&&it!="null"}
    private fun dn(o:JSONObject,k:String)=if(!o.has(k)||o.isNull(k))null else o.optDouble(k).takeIf{!it.isNaN()}
}

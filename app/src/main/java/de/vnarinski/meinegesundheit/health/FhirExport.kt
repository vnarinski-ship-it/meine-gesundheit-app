package de.vnarinski.meinegesundheit.health

import de.vnarinski.meinegesundheit.domain.LabResult

/** Minimaler FHIR-R4-kompatibler JSON-Entwurf für Observation-Laborwerte.
 * Später wird dies durch einen vollständigen FHIR-Serializer ersetzt.
 */
fun LabResult.toFhirObservationJson(): String = """
{
  "resourceType": "Observation",
  "id": "$id",
  "status": "final",
  "code": {
    "coding": [{
      "system": "http://loinc.org",
      "code": ${code?.let { "\"$it\"" } ?: "null"},
      "display": "${name.replace("\"", "\\\"")}"
    }]
  },
  "effectiveDateTime": "$measuredAt",
  "valueQuantity": {
    "value": ${value ?: "null"},
    "unit": ${unit?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"}
  }
}
""".trimIndent()

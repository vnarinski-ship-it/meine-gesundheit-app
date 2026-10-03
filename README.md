# Meine Gesundheit – V1.0

Private Android-Gesundheitsakte für Befunde, Laborwerte, Medikamente, Beschwerden, Low-Carb-Ernährung, Fahrten und Arztvorbereitung.

## Neu in V1.0
- Health-Connect-Grundintegration für Schritte, Herzfrequenz, Gewicht und Schlaf.
- Berechtigungen werden ausschließlich über den Android/Health-Connect-Dialog erteilt.
- Importierte Datensätze erhalten Herkunft/External-ID und werden verschlüsselt lokal gespeichert.
- CSV-Export für Tabellen und langfristiges Archiv.
- FHIR-R4-JSON-Export für Laborwerte und Medikamente.
- PDF-Gesundheitsübersicht.
- Alle Exportdateien werden nur auf ausdrückliche Aktion erzeugt und über Android geteilt.

## Bereits enthalten
- verschlüsselter lokaler Tresor (Android Keystore / AES-GCM)
- Befund/PDF-Import und KI-Zusammenfassung
- Labor-Prüfliste vor Übernahme KI-erkannter Werte
- Medikamente und Erinnerungen
- Low-Carb-Mahlzeiten/Fotos und KI-Schätzung
- GPS-Arbeitsfahrten in drei Datenschutzstufen
- Beschwerden 0–10 und gemeinsame Zeitachse
- Arzttermine, Fragenliste und PDF-Arztbericht
- portables AES-256-GCM-Backup für Handywechsel
- App-Sperre über Biometrie/Gerätesperre

## Datenschutzprinzip
Die App bleibt ohne Health Connect und ohne KI nutzbar. Externe Übertragung erfolgt nur nach Nutzeraktion. Originalbefunde bleiben unverändert.

## Build
Android Studio / Gradle-Projekt. Diese Arbeitsumgebung enthält keinen Gradle-Wrapper; daher liegt V1.0 als Quellprojekt vor und wurde hier nicht als APK kompiliert.

## APK-Build
Ein GitHub-Actions-Workflow unter `.github/workflows/build-apk.yml` ist enthalten. Er richtet Java 17, Android SDK 35 und Gradle 8.10.2 ein und erzeugt `app-debug.apk` als GitHub-Artifact. Siehe `BUILD_APK.md`.

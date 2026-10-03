# APK bauen

## Automatisch mit GitHub Actions
1. Den Inhalt dieses Projekts in ein GitHub-Repository auf Branch `main` hochladen.
2. In GitHub den Reiter **Actions** öffnen.
3. Workflow **Build Android APK** öffnen.
4. **Run workflow** drücken (oder ein Push auf `main` startet den Build automatisch).
5. Nach erfolgreichem Build unter **Artifacts** `meine-gesundheit-debug-apk` herunterladen.
6. Darin liegt `app-debug.apk`.

## Lokal mit Android Studio
1. Projektordner in Android Studio öffnen.
2. JDK 17 verwenden.
3. Android SDK Platform 35 und Build Tools 35.0.0 installieren.
4. `Build > Build APK(s)` ausführen.

Die Debug-APK ist für Tests/Installation geeignet. Für eine dauerhafte Release-Verteilung sollte später eine eigene Signatur (Keystore) verwendet werden.

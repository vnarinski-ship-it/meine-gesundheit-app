# Dauerhafte Signatur für Meine Gesundheit

Der private Keystore wird **nicht** im Repository gespeichert.

Einmalig in GitHub unter **Settings → Secrets and variables → Actions** vier Repository-Secrets anlegen:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Danach unter **Actions → Build Signed Health APK → Run workflow** starten.

Das Artifact heißt `meine-gesundheit-signed-apk`.

Wichtig: Der erste Wechsel von einer alten Debug-Signatur auf diese dauerhafte Release-Signatur erfordert einmalig:
1. Backup in der alten App erstellen.
2. Alte App deinstallieren.
3. Signierte Release-APK installieren.
4. Backup wiederherstellen.

Ab dann können alle zukünftigen APKs mit demselben Keystore normal als Update installiert werden.

package de.vnarinski.meinegesundheit.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PortableBackupManager(private val context: Context) {
    private val vault = EncryptedVault(context)
    private val magic = "MGHLTH08".toByteArray(Charsets.US_ASCII)
    private val iterations = 210_000

    data class BackupResult(val fileCount: Int, val createdAt: Instant)
    data class RestoreResult(val fileCount: Int, val createdAt: Instant?)

    fun create(target: Uri, password: CharArray): BackupResult {
        require(password.size >= 8) { "Backup-Passwort muss mindestens 8 Zeichen haben" }
        val names = vault.listNames().filterNot { it == "ai-settings.json.enc" }
        val createdAt = Instant.now()
        val zipBytes = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                val manifest = JSONObject().apply {
                    put("format", "meine-gesundheit-portable-backup")
                    put("version", 1)
                    put("appVersion", "0.9")
                    put("createdAt", createdAt.toString())
                    put("files", JSONArray(names))
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
                names.forEach { name ->
                    val plain = vault.read(name) ?: return@forEach
                    zip.putNextEntry(ZipEntry("vault/$name"))
                    zip.write(plain)
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
        val encrypted = encrypt(zipBytes, password)
        context.contentResolver.openOutputStream(target, "w")?.use { it.write(encrypted) }
            ?: error("Backup-Datei konnte nicht geöffnet werden")
        password.fill('\u0000')
        return BackupResult(names.size, createdAt)
    }

    fun restore(source: Uri, password: CharArray): RestoreResult {
        require(password.isNotEmpty()) { "Backup-Passwort fehlt" }
        val encrypted = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: error("Backup-Datei konnte nicht gelesen werden")
        val zipBytes = try { decrypt(encrypted, password) } catch (e: Exception) {
            password.fill('\u0000')
            throw IllegalArgumentException("Backup-Passwort falsch oder Datei beschädigt")
        }
        password.fill('\u0000')

        val restored = linkedMapOf<String, ByteArray>()
        var createdAt: Instant? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val data = zip.readBytes()
                if (e.name == "manifest.json") {
                    val m = JSONObject(data.toString(Charsets.UTF_8))
                    require(m.optString("format") == "meine-gesundheit-portable-backup") { "Kein gültiges Gesundheits-Backup" }
                    require(m.optInt("version") == 1) { "Backup-Version wird noch nicht unterstützt" }
                    createdAt = m.optString("createdAt").takeIf { it.isNotBlank() }?.let(Instant::parse)
                } else if (e.name.startsWith("vault/")) {
                    val name = e.name.removePrefix("vault/")
                    require(!name.contains("/") && !name.contains("..")) { "Ungültiger Backup-Eintrag" }
                    restored[name] = data
                }
            }
        }
        require(restored.isNotEmpty()) { "Backup enthält keine Gesundheitsdaten" }

        vault.clearAll()
        restored.forEach { (name, data) -> vault.write(name, data) }
        return RestoreResult(restored.size, createdAt)
    }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plain)
        return ByteArrayOutputStream().use { out ->
            out.write(magic); out.write(1); out.write(salt.size); out.write(salt); out.write(iv.size); out.write(iv); out.write(cipherText)
            out.toByteArray()
        }
    }

    private fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size > magic.size + 4) { "Backup-Datei ist zu kurz" }
        require(bytes.copyOfRange(0, magic.size).contentEquals(magic)) { "Unbekanntes Backup-Format" }
        var p = magic.size
        val version = bytes[p++].toInt() and 0xff
        require(version == 1) { "Backup-Version wird nicht unterstützt" }
        val saltLen = bytes[p++].toInt() and 0xff
        require(saltLen in 8..64 && p + saltLen < bytes.size) { "Backup-Header beschädigt" }
        val salt = bytes.copyOfRange(p, p + saltLen); p += saltLen
        val ivLen = bytes[p++].toInt() and 0xff
        require(ivLen in 12..32 && p + ivLen < bytes.size) { "Backup-Header beschädigt" }
        val iv = bytes.copyOfRange(p, p + ivLen); p += ivLen
        val cipherText = bytes.copyOfRange(p, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }
}

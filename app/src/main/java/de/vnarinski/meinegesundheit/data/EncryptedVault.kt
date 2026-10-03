package de.vnarinski.meinegesundheit.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedVault(private val context: Context) {
    private val alias = "meine_gesundheit_vault_v1"
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun write(name: String, plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        require(iv.size <= 255)
        File(dir, sanitize(name)).outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(encrypted)
        }
    }

    fun read(name: String): ByteArray? {
        val file = File(dir, sanitize(name))
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null
        val ivLength = bytes[0].toInt() and 0xff
        if (ivLength <= 0 || bytes.size <= 1 + ivLength) return null
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val encrypted = bytes.copyOfRange(1 + ivLength, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    fun delete(name: String) = File(dir, sanitize(name)).delete()
    fun listNames(): List<String> = dir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()
    fun clearAll() { dir.listFiles()?.forEach { if (it.isFile) it.delete() } }
    private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

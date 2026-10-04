package de.vnarinski.ocrhub.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureVault(context: Context) {
    private val alias = "ocr_hub_vault_v1"
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun write(name: String, plain: ByteArray): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val enc = cipher.doFinal(plain)
        val file = File(dir, safe)
        file.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(enc)
        }
        return file.absolutePath
    }

    fun read(path: String): ByteArray {
        val bytes = File(path).readBytes()
        val ivLen = bytes.first().toInt() and 0xff
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val enc = bytes.copyOfRange(1 + ivLen, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
    }
}

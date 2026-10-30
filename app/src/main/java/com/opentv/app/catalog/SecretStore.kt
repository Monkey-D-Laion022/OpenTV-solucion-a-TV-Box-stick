package com.opentv.app.catalog

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda las claves de TMDB y RPDB en el almacenamiento PRIVADO de OpenTV, cifradas con una clave AES-GCM del
 * Android Keystore (APIs públicas, sin dependencias). Qué protege: que otra app, una copia de la carpeta de datos o un
 * respaldo muestren la clave en claro (además `allowBackup` está desactivado). Qué NO protege: no es un secreto
 * inextraíble; alguien con root, o con el teléfono desbloqueado y herramientas de depuración, podría obtenerla mientras
 * OpenTV la usa. Si el Keystore no está disponible, se guarda sin cifrado adicional y [protectionOf] lo informa.
 */
class SecretStore(context: Context) {
    enum class Protection { KEYSTORE, PRIVATE_ONLY }

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun put(name: String, value: String): Protection {
        val encrypted = try {
            encrypt(value)
        } catch (e: Exception) {
            null
        }
        return if (encrypted != null) {
            prefs.edit().putString(name, V1 + encrypted).commit()
            Protection.KEYSTORE
        } else {
            prefs.edit().putString(name, P0 + Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)).commit()
            Protection.PRIVATE_ONLY
        }
    }

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            when {
                stored.startsWith(V1) -> decrypt(stored.removePrefix(V1))
                stored.startsWith(P0) -> String(Base64.decode(stored.removePrefix(P0), Base64.NO_WRAP), Charsets.UTF_8)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun has(name: String): Boolean = prefs.contains(name)

    fun remove(name: String) {
        prefs.edit().remove(name).commit()
    }

    fun protectionOf(name: String): Protection? {
        val stored = prefs.getString(name, null) ?: return null
        return if (stored.startsWith(V1)) Protection.KEYSTORE else Protection.PRIVATE_ONLY
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER)
        store.load(null)
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(":")
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    companion object {
        const val TMDB = "tmdb"
        const val RPDB = "rpdb"
        private const val FILE = "opentv_catalog_secrets"
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "opentv_catalog_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val V1 = "v1:"
        private const val P0 = "p0:"
    }
}

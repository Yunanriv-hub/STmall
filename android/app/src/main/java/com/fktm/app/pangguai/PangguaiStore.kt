package com.fktm.app.pangguai

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
 * Encrypted Pangguai session store. Upstream keeps the bearer token in plain
 * SharedPreferences and logs it; STmall intentionally does neither.
 */
class PangguaiStore(context: Context) {

    private companion object {
        const val PREFS = "fktm_pangguai_secure"
        const val KEY_ALIAS = "fktm_pangguai_master_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_TAG_BITS = 128
        const val FIELD_TOKEN = "token_enc"
        const val FIELD_MOBILE = "mobile_enc"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentToken(): String? = readEncrypted(FIELD_TOKEN)

    fun currentMobile(): String? = readEncrypted(FIELD_MOBILE)

    fun saveSession(token: String, mobile: String) {
        writeEncrypted(FIELD_TOKEN, token)
        writeEncrypted(FIELD_MOBILE, mobile)
    }

    fun clearSession() {
        prefs.edit()
            .remove(FIELD_TOKEN)
            .remove(FIELD_MOBILE)
            .apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun writeEncrypted(field: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(field, packed).apply()
    }

    private fun readEncrypted(field: String): String? {
        val packed = prefs.getString(field, null) ?: return null
        return runCatching {
            val parts = packed.split(':', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        }.getOrNull()
    }
}

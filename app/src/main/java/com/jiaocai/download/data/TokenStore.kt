package com.jiaocai.download.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.credentialsDataStore by preferencesDataStore(name = "credentials")

/**
 * 本地持久化登录凭据。明文 token 先用 Android Keystore 的 AES/GCM 密钥加密，
 * 再把 base64(iv ‖ ciphertext) 存入 DataStore，确保明文不落盘。
 */
class TokenStore(private val context: Context) {

    private val key = stringPreferencesKey("token_json")

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密并保存明文 token JSON。 */
    suspend fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ct = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ct // iv(12) + ciphertext
        context.credentialsDataStore.edit { it[key] = Base64.encodeToString(packed, Base64.NO_WRAP) }
    }

    /** 读取并解密 token；未保存或解密失败返回 null。 */
    suspend fun load(): String? {
        val stored = context.credentialsDataStore.data.first()[key] ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_LEN)
            val ct = bytes.copyOfRange(IV_LEN, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { it.remove(key) }
    }

    private companion object {
        const val ALIAS = "textbook_token_key"
        const val IV_LEN = 12
        const val TAG_BITS = 128
    }
}

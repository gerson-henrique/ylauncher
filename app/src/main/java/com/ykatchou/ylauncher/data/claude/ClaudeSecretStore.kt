package com.ykatchou.ylauncher.data.claude

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ykatchou.ylauncher.util.YLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Stores the Anthropic API key encrypted, so it never sits in the repo and never sits in plaintext
 * on disk. The AES-256/GCM key lives in the Android Keystore (hardware-backed where the device
 * supports it) and never leaves it; only the ciphertext — IV prepended to the encrypted bytes,
 * base64'd — goes into a small DataStore. Losing the Keystore key (factory reset, key invalidation)
 * simply means the stored blob no longer decrypts and the user re-pastes the key; that is the right
 * failure, not a fallback to plaintext.
 */
@Singleton
class ClaudeSecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.claudeSecretStore

    /** Whether a key is stored — drives the "paste your key" prompt without ever exposing it. */
    val hasKey: Flow<Boolean> = dataStore.data.map { !it[BLOB].isNullOrBlank() }

    suspend fun setApiKey(key: String) {
        val trimmed = key.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(BLOB)
            else prefs[BLOB] = encrypt(trimmed)
        }
    }

    /**
     * The workspace this API key acts in. Not a secret — a plain identifier — so it is stored in the
     * clear. Required only for identity-linked keys, which the API rejects without it; blank otherwise.
     */
    suspend fun setWorkspaceId(id: String) {
        val trimmed = id.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(WORKSPACE) else prefs[WORKSPACE] = trimmed
        }
    }

    suspend fun getWorkspaceId(): String? = dataStore.data.first()[WORKSPACE]?.takeIf { it.isNotBlank() }

    /** Forget the stored key and workspace — brings back the paste-your-key prompt. */
    suspend fun clear() {
        dataStore.edit { it.remove(BLOB); it.remove(WORKSPACE) }
    }

    /** The decrypted key, or null when unset or no longer decryptable. Never logged. */
    suspend fun getApiKey(): String? {
        val blob = dataStore.data.first()[BLOB]?.takeIf { it.isNotBlank() } ?: return null
        return try {
            decrypt(blob)
        } catch (t: Throwable) {
            YLogger.e(TAG, "stored key no longer decryptable", t as? Exception ?: Exception(t))
            null
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_LEN)
        val ct = bytes.copyOfRange(GCM_IV_LEN, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val TAG = "ClaudeSecretStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "ylauncher_claude_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LEN = 12
        const val GCM_TAG_BITS = 128
        val BLOB = stringPreferencesKey("claude_api_key_blob")
        val WORKSPACE = stringPreferencesKey("claude_workspace_id")
    }
}

private val Context.claudeSecretStore: DataStore<Preferences> by preferencesDataStore(name = "claude_secret")

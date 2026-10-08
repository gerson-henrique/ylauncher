package com.ykatchou.ylauncher.data.ruby

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
 * Where the Ruby connection lives: the base URL (plain — it is only an address, and it must be
 * editable because the Dell's LAN IP drifts) and the pairing token (encrypted). The token is the
 * one secret the contract says to keep, so it gets the same AES-256/GCM-in-Keystore treatment as
 * [com.ykatchou.ylauncher.data.claude.ClaudeSecretStore]: the key never leaves the Keystore, only
 * the ciphertext (IV ++ bytes, base64) reaches DataStore. Losing the key (factory reset) just means
 * the blob no longer decrypts and the user re-pairs — the right failure, never a plaintext fallback.
 */
@Singleton
class RubyConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.rubyConfigStore

    /** Cricket's address, always trimmed of a trailing slash, falling back to the known default. */
    val baseUrl: Flow<String> = dataStore.data.map {
        it[BASE_URL]?.takeIf { u -> u.isNotBlank() } ?: DEFAULT_BASE_URL
    }

    /**
     * The Ruby lived on the Dell (192.168.0.30), which is now a Windows box; Cricket lives on the
     * Mac. A phone paired back then kept that address and its token, so it retried a dead host
     * forever and never showed pairing. Drop both once: the next start lands on the pairing screen
     * already pointing at the Mac.
     */
    suspend fun migrarDoDell() {
        if (dataStore.data.first()[BASE_URL]?.trimEnd('/') in LEGACY_BASE_URLS) {
            dataStore.edit { it.remove(BASE_URL); it.remove(TOKEN_BLOB) }
        }
    }

    /** Whether we are paired — drives the pairing screen without ever decrypting the token. */
    val hasToken: Flow<Boolean> = dataStore.data.map { !it[TOKEN_BLOB].isNullOrBlank() }

    suspend fun baseUrlNow(): String = baseUrl.first()

    suspend fun setBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        dataStore.edit { if (clean.isEmpty()) it.remove(BASE_URL) else it[BASE_URL] = clean }
    }

    /** The decrypted token, or null when unpaired or no longer decryptable. Never logged. */
    suspend fun tokenNow(): String? {
        val blob = dataStore.data.first()[TOKEN_BLOB]?.takeIf { it.isNotBlank() } ?: return null
        return try {
            decrypt(blob)
        } catch (t: Throwable) {
            YLogger.e(TAG, "stored token no longer decryptable", t as? Exception ?: Exception(t))
            null
        }
    }

    suspend fun setToken(token: String) {
        val t = token.trim()
        dataStore.edit { if (t.isEmpty()) it.remove(TOKEN_BLOB) else it[TOKEN_BLOB] = encrypt(t) }
    }

    /** Forget the token — brings back the pairing screen (used when the server returns 401). */
    suspend fun clearToken() {
        dataStore.edit { it.remove(TOKEN_BLOB) }
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

    companion object {
        /** The Dell on the home WiFi, from the contract. Editable in settings; not compiled fixed. */
        const val DEFAULT_BASE_URL = "http://192.168.0.9:8080"
        private val LEGACY_BASE_URLS = setOf("http://192.168.0.30:8080", "http://192.168.68.106:8080", "http://ruby.local:8080")

        private const val TAG = "RubyConfig"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "ylauncher_ruby_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private val BASE_URL = stringPreferencesKey("ruby_base_url")
        private val TOKEN_BLOB = stringPreferencesKey("ruby_token_blob")
    }
}

private val Context.rubyConfigStore: DataStore<Preferences> by preferencesDataStore(name = "ruby_config")

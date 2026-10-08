package com.ykatchou.ylauncher.data.ponte

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

/** Where the paired Mac is and the two tokens of the pairing (the Mac's, and the one we gave it). */
data class PontePairing(
    val host: String,
    val port: Int,
    val name: String,
    val macToken: String,
    val phoneToken: String,
)

/**
 * The ponte pairing, kept like the Ruby token: the address in plain DataStore, both tokens as
 * AES/GCM ciphertext under a Keystore key that never leaves the device. A key lost to a reset
 * means the blobs stop decrypting and the person pairs again — never a plaintext fallback.
 */
@Singleton
class PonteConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.ponteStore

    val pairedName: Flow<String?> = store.data.map { it[NAME]?.takeIf { _ -> !it[MAC_TOKEN].isNullOrBlank() } }

    suspend fun pairing(): PontePairing? {
        val p = store.data.first()
        val host = p[HOST] ?: return null
        val macToken = p[MAC_TOKEN]?.let(::decryptOrNull) ?: return null
        val phoneToken = p[PHONE_TOKEN]?.let(::decryptOrNull) ?: return null
        return PontePairing(host, p[PORT] ?: 8738, p[NAME] ?: "Mac", macToken, phoneToken)
    }

    suspend fun save(pairing: PontePairing) {
        store.edit {
            it[HOST] = pairing.host
            it[PORT] = pairing.port
            it[NAME] = pairing.name
            it[MAC_TOKEN] = encrypt(pairing.macToken)
            it[PHONE_TOKEN] = encrypt(pairing.phoneToken)
        }
    }

    /** The Mac moved on the LAN (found again over mDNS). */
    suspend fun setHost(host: String) {
        store.edit { it[HOST] = host }
    }

    suspend fun forget() {
        store.edit { it.clear() }
    }

    /** Time stamp of the last Mac clipboard item applied here, so a stale one is never re-applied. */
    suspend fun lastMacClip(): Long = store.data.first()[LAST_MAC_CLIP] ?: 0L

    suspend fun setLastMacClip(seq: Long) {
        store.edit { it[LAST_MAC_CLIP] = seq }
    }

    /** Whether the Mac has our fingerprint-bound public key (registered on first "controlar Mac"). */
    suspend fun screenKeyRegistered(): Boolean = store.data.first()[SCREEN_KEY] == true

    suspend fun setScreenKeyRegistered() {
        store.edit { it[SCREEN_KEY] = true }
    }

    private fun decryptOrNull(blob: String): String? = runCatching { decrypt(blob) }.getOrNull()

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, bytes.copyOfRange(0, GCM_IV_LEN)),
        )
        return String(cipher.doFinal(bytes.copyOfRange(GCM_IV_LEN, bytes.size)), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ylauncher_ponte"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LEN = 12
        const val GCM_TAG_BITS = 128

        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val NAME = stringPreferencesKey("name")
        val MAC_TOKEN = stringPreferencesKey("mac_token")
        val PHONE_TOKEN = stringPreferencesKey("phone_token")
        val LAST_MAC_CLIP = longPreferencesKey("last_mac_clip")
        val SCREEN_KEY = booleanPreferencesKey("screen_key_registered")
    }
}

private val Context.ponteStore: DataStore<Preferences> by preferencesDataStore(name = "ponte")

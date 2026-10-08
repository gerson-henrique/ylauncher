package com.ykatchou.ylauncher.data.ponte

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The fingerprint half of "controlar Mac". An EC key lives in the Keystore and can sign only
 * inside a fingerprint authentication — not even this app can use it otherwise. The Mac holds the
 * public half and opens Screen Sharing only for a signature over its fresh challenge.
 *
 * Uses the platform BiometricPrompt (Android 11+ for strong-biometric-bound keys) so the home
 * activity need not become a FragmentActivity just for this.
 */
object TelaGate {

    private const val KEY_ALIAS = "ylauncher_ponte_tela"
    private const val KEYSTORE = "AndroidKeyStore"

    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** The public key to register with the Mac (SPKI DER, base64), creating the pair if needed. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun publicKey(): String {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                        .setInvalidatedByBiometricEnrollment(true)
                        .build(),
                )
                generateKeyPair()
            }
        }
        return Base64.encodeToString(ks.getCertificate(KEY_ALIAS).publicKey.encoded, Base64.NO_WRAP)
    }

    /** Ask for the fingerprint and sign [challenge] with it. Null when cancelled or refused. */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun sign(context: Context, challenge: String): String? {
        val bm = context.getSystemService(BiometricManager::class.java)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            return null
        }
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = ks.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
        val signature = runCatching { Signature.getInstance("SHA256withECDSA").apply { initSign(key) } }
            .getOrNull() ?: return null   // key invalidated by a new fingerprint enrolment

        return suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            BiometricPrompt.Builder(context)
                .setTitle("controlar o Mac")
                .setSubtitle("a digital abre o Compartilhamento de Tela")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("cancelar", context.mainExecutor) { _, _ -> if (cont.isActive) cont.resume(null) }
                .build()
                .authenticate(
                    BiometricPrompt.CryptoObject(signature),
                    cancel,
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val signed = runCatching {
                                val s = checkNotNull(result.cryptoObject?.signature)
                                s.update(challenge.toByteArray(Charsets.UTF_8))
                                Base64.encodeToString(s.sign(), Base64.NO_WRAP)
                            }.getOrNull()
                            if (cont.isActive) cont.resume(signed)
                        }

                        override fun onAuthenticationError(code: Int, msg: CharSequence) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
        }
    }

    /** Forget the key (after re-pairing, the Mac no longer knows it). */
    fun reset() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }
}

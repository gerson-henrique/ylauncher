package com.ykatchou.ylauncher.data.ponte

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.PersistableBundle
import android.provider.OpenableColumns
import com.ykatchou.ylauncher.data.qr.QrContent
import com.ykatchou.ylauncher.util.YLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * The phone side of the ponte (橋) to the Mac's `hashi`. Talks plain HTTP on the home LAN with the
 * bearer token from the pairing QR, and runs [PonteServer] so the Mac can push its clipboard here.
 *
 * Every call is best effort and returns whether it worked: the Mac being asleep or off the network
 * is the normal case, not an error to surface beyond "fora de alcance".
 */
@Singleton
class Ponte @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: PonteConfig,
) {
    val pairedName = config.pairedName

    /** Fire-and-forget work that must outlive the screen that started it (a share, a clip). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val server = PonteServer(PHONE_PORT, ::onClipFromMac)

    /** The last text the Mac pushed here. The keyboard reports it as a fresh copy; it must not go back. */
    @Volatile private var lastFromMac: String? = null

    /** Start listening for the Mac, if paired. Called at app start and right after pairing. */
    fun start() {
        scope.launch {
            val pairing = config.pairing() ?: return@launch
            server.start(pairing.phoneToken)
        }
    }

    /** Pair with the Mac from its QR: give it our address and a fresh token of our own. */
    suspend fun pair(code: QrContent.Ponte): Boolean = withContext(Dispatchers.IO) {
        val phoneToken = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val body = JSONObject()
            .put("port", PHONE_PORT)
            .put("token", phoneToken)
            .put("nome", Build.MODEL)
            .toString()
        val reply = request(code.host, code.port, code.token, "POST", "/parear", body.toByteArray()) ?: return@withContext false
        val name = runCatching { JSONObject(reply).optString("nome") }.getOrNull()?.takeIf { it.isNotBlank() } ?: code.name
        config.save(PontePairing(code.host, code.port, name, code.token, phoneToken))
        server.start(phoneToken)
        true
    }

    suspend fun forget() {
        server.stop()
        config.forget()
        TelaGate.reset()
    }

    /** Result of asking to control the Mac: the address to open the viewer on, or why not. */
    sealed interface Screen {
        data class Open(val host: String) : Screen
        data class Refused(val why: String) : Screen
    }

    /**
     * Fingerprint → signed challenge → the Mac turns Screen Sharing on. The first time, our public
     * key is registered with the Mac; after that the Mac refuses any other key.
     */
    suspend fun openScreen(context: Context): Screen {
        if (!TelaGate.supported()) return Screen.Refused("precisa do Android 11+")
        val pairing = config.pairing() ?: return Screen.Refused("ponte não pareada")
        if (!config.screenKeyRegistered()) {
            val key = withContext(Dispatchers.IO) { TelaGate.publicKey() }
            if (call("POST", "/tela/chave", key.toByteArray()) == null) {
                return Screen.Refused("o Mac recusou a chave — rode hashi parear --novo e pareie de novo")
            }
            config.setScreenKeyRegistered()
        }
        val challenge = call("GET", "/tela/desafio")
            ?.let { runCatching { JSONObject(it).getString("desafio") }.getOrNull() }
            ?: return Screen.Refused("Mac fora de alcance")
        // A biometric failure is a refusal, never a crash: this runs inside the launcher, and a
        // crashing home app makes Android fall back to the stock launcher.
        val signature = withContext(Dispatchers.Main) {
            runCatching { TelaGate.sign(context, challenge) }
                .onFailure { YLogger.e(TAG, "biometric sign failed", it as? Exception ?: Exception(it)) }
                .getOrNull()
        } ?: return Screen.Refused("digital não confirmada")
        val body = JSONObject().put("desafio", challenge).put("assinatura", signature).toString()
        call("POST", "/tela/abrir", body.toByteArray())
            ?: return Screen.Refused("o Mac não abriu a tela — o hashi-tela está instalado?")
        return Screen.Open(config.pairing()?.host ?: pairing.host)
    }

    suspend fun reachable(): Boolean = call("GET", "/oi") != null

    suspend fun sendClip(text: String): Boolean {
        if (text == lastFromMac) return true
        return call("POST", "/clip", text.toByteArray()) != null
    }

    suspend fun openOnMac(url: String): Boolean = call("POST", "/abrir", url.toByteArray()) != null

    suspend fun macAction(action: String): Boolean = call("POST", "/mac/$action") != null

    suspend fun volume(delta: Int): Int? =
        call("POST", "/mac/volume?d=$delta")?.let { runCatching { JSONObject(it).getInt("volume") }.getOrNull() }

    /** Ask the Mac to connect adb to this phone and open scrcpy. It finds the adb port itself. */
    suspend fun mirror(): Boolean = call("POST", "/espelhar") != null

    /** Stream one shared item to the Mac's inbox. Returns the name it was saved under. */
    suspend fun sendFile(uri: Uri): String? {
        val name = displayName(uri) ?: "arquivo"
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
        return input.use { stream ->
            call("POST", "/arquivo?nome=${URLEncoder.encode(name, "UTF-8")}", stream = stream)
                ?.let { runCatching { JSONObject(it).getString("salvo") }.getOrNull() ?: name }
        }
    }

    /** Catch up on a Mac copy made while the phone was not listening (the home just came back). */
    suspend fun pullMacClip() {
        val reply = call("GET", "/clip") ?: return
        val json = runCatching { JSONObject(reply) }.getOrNull() ?: return
        val seq = json.optLong("seq")
        val text = json.optString("texto")
        if (seq > config.lastMacClip() && text.isNotEmpty()) onClipFromMac(text, seq)
    }

    private fun onClipFromMac(text: String, seq: Long) {
        lastFromMac = text
        if (seq > 0) scope.launch { config.setLastMacClip(seq) }
        val clip = ClipData.newPlainText("Mac", text)
        // Tell the keyboard's clipboard history this came from the bridge, so it is not echoed.
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_FROM_PONTE, true) }
        android.os.Handler(context.mainLooper).post {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        }
    }

    /** A call to the paired Mac; on a connection failure, look for it on the LAN once and retry. */
    private suspend fun call(method: String, path: String, body: ByteArray? = null, stream: InputStream? = null): String? =
        withContext(Dispatchers.IO) {
            val p = config.pairing() ?: return@withContext null
            request(p.host, p.port, p.macToken, method, path, body, stream)?.let { return@withContext it }
            if (stream != null) return@withContext null   // a consumed stream cannot be resent
            val found = discover() ?: return@withContext null
            if (found == p.host) return@withContext null
            config.setHost(found)
            request(found, p.port, p.macToken, method, path, body)
        }

    private fun request(
        host: String,
        port: Int,
        token: String,
        method: String,
        path: String,
        body: ByteArray? = null,
        stream: InputStream? = null,
    ): String? = runCatching {
        val conn = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = if (stream != null) 0 else READ_TIMEOUT_MS
        conn.setRequestProperty("Authorization", "Bearer $token")
        when {
            stream != null -> {
                conn.doOutput = true
                conn.setChunkedStreamingMode(64 * 1024)
                conn.outputStream.use { stream.copyTo(it) }
            }
            body != null -> {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            method == "POST" -> {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
                conn.outputStream.close()
            }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
        conn.disconnect()
        if (code in 200..299) text.orEmpty() else {
            YLogger.d(TAG, "$method $path → $code ${text.orEmpty().take(200)}")
            null
        }
    }.onFailure { YLogger.d(TAG, "$method $path failed: ${it.message}") }.getOrNull()

    /** Find `_hashi._tcp` on the LAN and return its IPv4 address. */
    private suspend fun discover(): String? = withTimeoutOrNull(DISCOVER_TIMEOUT_MS) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        suspendCancellableCoroutine { cont ->
            var resolving = false
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (resolving) return
                    resolving = true
                    @Suppress("DEPRECATION")
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i: NsdServiceInfo, error: Int) { resolving = false }
                        override fun onServiceResolved(i: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val ip = (i.host as? Inet4Address)?.hostAddress
                            if (ip != null && cont.isActive) cont.resume(ip)
                        }
                    })
                }
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onDiscoveryStarted(type: String) {}
                override fun onDiscoveryStopped(type: String) {}
                override fun onStartDiscoveryFailed(type: String, error: Int) { if (cont.isActive) cont.resume(null) }
                override fun onStopDiscoveryFailed(type: String, error: Int) {}
            }
            nsd.discoverServices("_hashi._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    companion object {
        private const val TAG = "Ponte"
        const val PHONE_PORT = 8737
        private const val CONNECT_TIMEOUT_MS = 2500
        private const val READ_TIMEOUT_MS = 8000
        private const val DISCOVER_TIMEOUT_MS = 4000L

        /** Clip extra marking an item the bridge itself wrote. */
        const val EXTRA_FROM_PONTE = "com.ykatchou.ylauncher.ponte.FROM_MAC"

        /** Plain-text clip that may cross, or null for sensitive / non-text items. */
        fun shareableText(clip: ClipData?): String? {
            val desc: ClipDescription = clip?.description ?: return null
            val extras = desc.extras
            if (extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return null
            if (extras?.getBoolean(EXTRA_FROM_PONTE) == true) return null
            if (clip.itemCount == 0) return null
            return clip.getItemAt(0).text?.toString()?.takeIf { it.isNotEmpty() }
        }
    }
}

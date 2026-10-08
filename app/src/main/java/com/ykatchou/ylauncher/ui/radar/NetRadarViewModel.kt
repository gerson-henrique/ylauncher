package com.ykatchou.ylauncher.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykatchou.ylauncher.data.ponte.Ponte
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Shizuku's health, as the Sala de Máquinas needs to show and act on it. */
enum class ShizukuState { UP, NEEDS_PERMISSION, DOWN }

/**
 * State behind the Sala de Máquinas: Shizuku's health and wireless debugging, polled only while the
 * page is on screen — a readout has no business waking the device from a background loop.
 */
@HiltViewModel
class NetRadarViewModel @Inject constructor(
    private val ponte: Ponte,
) : ViewModel() {

    /** The paired Mac's name, or null when the bridge is not paired. */
    val ponteName: StateFlow<String?> = ponte.pairedName.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether the Mac answered the last ping; null before the first one. */
    private val _macUp = MutableStateFlow<Boolean?>(null)
    val macUp: StateFlow<Boolean?> = _macUp.asStateFlow()

    /** One-line results of the bridge buttons, shown as toasts. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // The machine-room controls: Shizuku health and whether wireless debugging is on. Both are
    // polled while the page is open; the wifi-debug read only works once Shizuku is up.
    private val _shizuku = MutableStateFlow(ShizukuState.DOWN)
    val shizuku: StateFlow<ShizukuState> = _shizuku.asStateFlow()

    private val _wifiDebugOn = MutableStateFlow<Boolean?>(null)
    val wifiDebugOn: StateFlow<Boolean?> = _wifiDebugOn.asStateFlow()

    /** The port wireless debugging listens on right now — it changes every time it is toggled. */
    private val _adbPort = MutableStateFlow<Int?>(null)
    val adbPort: StateFlow<Int?> = _adbPort.asStateFlow()

    private var job: Job? = null

    fun setActive(active: Boolean) {
        if (active) start() else stop()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                refreshShizuku()
                if (tick++ % MAC_PING_EVERY == 0 && ponteName.value != null) _macUp.value = ponte.reachable()
                delay(POLL_MS)
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
    }

    /** Read Shizuku's state, and — if it is up — whether wireless debugging is currently enabled. */
    private suspend fun refreshShizuku() {
        val state = withContext(Dispatchers.IO) {
            when {
                com.ykatchou.ylauncher.data.running.ShizukuShell.isReady() -> ShizukuState.UP
                com.ykatchou.ylauncher.data.running.ShizukuShell.needsPermission() -> ShizukuState.NEEDS_PERMISSION
                else -> ShizukuState.DOWN
            }
        }
        _shizuku.value = state
        _wifiDebugOn.value = if (state == ShizukuState.UP) {
            withContext(Dispatchers.IO) {
                com.ykatchou.ylauncher.data.running.ShizukuShell.run("settings get global adb_wifi_enabled")
                    ?.trim()?.let { it == "1" }
            }
        } else {
            null
        }
        _adbPort.value = if (_wifiDebugOn.value == true) {
            withContext(Dispatchers.IO) {
                com.ykatchou.ylauncher.data.running.ShizukuShell.run("getprop service.adb.tls.port")
                    ?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            }
        } else {
            null
        }
    }

    fun requestShizukuPermission() {
        com.ykatchou.ylauncher.data.running.ShizukuShell.requestPermission(SHIZUKU_REQ)
    }

    /** Flip wireless debugging via Shizuku — the recurring reconnection pain, killed with one tap. */
    fun toggleWifiDebug() {
        viewModelScope.launch {
            val target = if (_wifiDebugOn.value == true) "0" else "1"
            withContext(Dispatchers.IO) {
                com.ykatchou.ylauncher.data.running.ShizukuShell.run("settings put global adb_wifi_enabled $target")
            }
            refreshShizuku()
        }
    }

    /** Run a bridge action off the main thread and report it in one line. */
    private fun bridge(ok: String, action: suspend () -> Boolean) {
        viewModelScope.launch {
            val done = action()
            _macUp.value = done
            _messages.emit(if (done) ok else "Mac fora de alcance")
        }
    }

    fun mirror() {
        if (_wifiDebugOn.value == false) {
            _messages.tryEmit("liga o Wi‑Fi debug primeiro")
            return
        }
        bridge("abrindo no Mac") { ponte.mirror() }
    }

    /** Fingerprint-gated: on success the viewer opens on the Mac's address. */
    fun controlMac(context: android.content.Context, openViewer: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = ponte.openScreen(context)) {
                is Ponte.Screen.Open -> {
                    _macUp.value = true
                    openViewer(r.host)
                }
                is Ponte.Screen.Refused -> _messages.emit(r.why)
            }
        }
    }

    fun lockMac() = bridge("Mac bloqueado") { ponte.macAction("bloquear") }
    fun sleepMac() = bridge("Mac dormindo") { ponte.macAction("suspender") }
    fun playPause() = bridge("⏯") { ponte.macAction("tocar") }
    fun sendClip(text: String?) {
        if (text.isNullOrEmpty()) {
            _messages.tryEmit("nada copiado")
            return
        }
        bridge("copiado no Mac") { ponte.sendClip(text) }
    }

    fun volume(delta: Int) {
        viewModelScope.launch {
            val v = ponte.volume(delta)
            _macUp.value = v != null
            _messages.emit(v?.let { "volume $it" } ?: "Mac fora de alcance")
        }
    }

    fun forgetMac() {
        viewModelScope.launch {
            ponte.forget()
            _macUp.value = null
        }
    }

    private companion object {
        const val MAC_PING_EVERY = 5
        const val POLL_MS = 1200L
        const val SHIZUKU_REQ = 4610
    }
}

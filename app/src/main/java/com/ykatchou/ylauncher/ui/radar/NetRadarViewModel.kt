package com.ykatchou.ylauncher.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykatchou.ylauncher.data.net.IpOwner
import com.ykatchou.ylauncher.data.net.NetRadarSource
import dagger.hilt.android.lifecycle.HiltViewModel
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

/** One call that appeared on the radar. */
data class RadarLine(
    val id: Long,
    val uid: Int,
    val app: String,
    val remote: String,
    val port: Int,
    val proto: String,
    val owner: String?,   // Google / Meta / … when the IP is recognised, else null
    val atMs: Long,
    val fresh: Boolean,   // just appeared — gets the pulse
)

data class RadarSummary(val bytesPerSec: Long, val appsOnNet: Int, val topOrg: String)

/** Shizuku's health, as the Sala de Máquinas needs to show and act on it. */
enum class ShizukuState { UP, NEEDS_PERMISSION, DOWN }

/**
 * Polls the socket tables while the page is on screen, and turns each *new* connection into a line
 * on the feed. Only samples when active — a network readout has no business waking the device from
 * a background loop. Connections that persist across polls are not re-emitted; only the moment one
 * opens shows up, which is what makes it read like a scanner.
 */
@HiltViewModel
class NetRadarViewModel @Inject constructor(
    private val source: NetRadarSource,
) : ViewModel() {

    private val _feed = MutableStateFlow<List<RadarLine>>(emptyList())
    val feed: StateFlow<List<RadarLine>> = _feed.asStateFlow()

    private val _summary = MutableStateFlow(RadarSummary(0, 0, "—"))
    val summary: StateFlow<RadarSummary> = _summary.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    // The machine-room controls: Shizuku health and whether wireless debugging is on. Both are
    // polled while the page is open; the wifi-debug read only works once Shizuku is up.
    private val _shizuku = MutableStateFlow(ShizukuState.DOWN)
    val shizuku: StateFlow<ShizukuState> = _shizuku.asStateFlow()

    private val _wifiDebugOn = MutableStateFlow<Boolean?>(null)
    val wifiDebugOn: StateFlow<Boolean?> = _wifiDebugOn.asStateFlow()

    private var job: Job? = null
    private var seen = emptySet<String>()
    private var firstDone = false
    private var nextId = 0L
    private val eventTimes = ArrayDeque<Long>()  // for calls/min
    private val myUid = android.os.Process.myUid()
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastNetMs = 0L

    fun setActive(active: Boolean) {
        if (active) start() else stop()
    }

    fun togglePause() {
        _paused.value = !_paused.value
    }

    private fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            while (isActive) {
                refreshShizuku()
                if (!_paused.value) poll()
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

    private suspend fun poll() = runCatching { pollOnce() }
        .onFailure { com.ykatchou.ylauncher.util.YLogger.e("NetRadar", "poll failed", it as? Exception ?: Exception(it)) }
        .let { Unit }

    private suspend fun pollOnce() {
        val snap = withContext(Dispatchers.IO) { source.snapshot() }
        if (snap == null) {
            _available.value = false
            return
        }
        _available.value = true

        val now = System.currentTimeMillis()
        val keysNow = snap.mapTo(HashSet()) { it.key }
        val fresh = snap.filter { it.key !in seen }
        seen = keysNow

        if (fresh.isNotEmpty()) {
            val lines = fresh.map { c ->
                RadarLine(
                    id = nextId++,
                    uid = c.uid,
                    app = source.appLabel(c.uid),
                    remote = c.remoteIp,
                    port = c.remotePort,
                    proto = c.proto,
                    owner = IpOwner.ownerOf(c.remoteIp),
                    atMs = now,
                    fresh = firstDone,   // the opening burst is not a pulse; later arrivals are
                )
            }
            _feed.value = (lines.reversed() + _feed.value).take(MAX_LINES)
            if (firstDone) repeat(fresh.size) { eventTimes.addLast(now) }
        }
        firstDone = true

        // Useful summary, not activity noise: how much data is moving, how many apps are on the
        // network, and which owner it concentrates on. Bytes matter more than socket counts.
        val bytesPerSec = throughput(now)
        val appsOnNet = snap.filter { it.uid >= 10000 }.map { it.uid }.distinct().size
        val topOrg = snap.mapNotNull { IpOwner.ownerOf(it.remoteIp) }
            .filter { it != "rede local" }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "—"
        _summary.value = RadarSummary(bytesPerSec, appsOnNet, topOrg)
    }

    /** Total device throughput since the last poll, in bytes/sec. Zero on the first sample. */
    private fun throughput(now: Long): Long {
        val rx = android.net.TrafficStats.getTotalRxBytes()
        val tx = android.net.TrafficStats.getTotalTxBytes()
        if (rx == android.net.TrafficStats.UNSUPPORTED.toLong()) return 0L
        val rate = if (lastNetMs == 0L) 0L else {
            val secs = ((now - lastNetMs) / 1000.0).coerceAtLeast(0.001)
            (((rx - lastRx) + (tx - lastTx)) / secs).toLong().coerceAtLeast(0L)
        }
        lastRx = rx; lastTx = tx; lastNetMs = now
        return rate
    }

    private companion object {
        const val POLL_MS = 1200L
        const val SHIZUKU_REQ = 4610
        const val MAX_LINES = 80
    }
}

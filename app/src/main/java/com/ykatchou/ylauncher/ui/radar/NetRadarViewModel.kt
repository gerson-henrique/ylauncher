package com.ykatchou.ylauncher.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val atMs: Long,
    val fresh: Boolean,   // just appeared — gets the pulse
)

data class RadarSummary(val callsPerMin: Int, val activeConns: Int, val topApp: String)

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

    private var job: Job? = null
    private var seen = emptySet<String>()
    private var firstDone = false
    private var nextId = 0L
    private val eventTimes = ArrayDeque<Long>()  // for calls/min
    private val myUid = android.os.Process.myUid()  // exclude ourselves from "top talker"

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
                if (!_paused.value) poll()
                delay(POLL_MS)
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
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
                    atMs = now,
                    fresh = firstDone,   // the opening burst is not a pulse; later arrivals are
                )
            }
            _feed.value = (lines.reversed() + _feed.value).take(MAX_LINES)
            if (firstDone) repeat(fresh.size) { eventTimes.addLast(now) }
        }
        firstDone = true

        val cutoff = now - 60_000
        while (eventTimes.isNotEmpty() && eventTimes.first() < cutoff) eventTimes.removeFirst()

        // "Top talker" only over real apps — otherwise the system catch-all bucket wins trivially
        // and names nothing useful; and the user doesn't care that we ourselves opened a socket.
        val top = snap.filter { it.uid >= 10000 && it.uid != myUid }
            .groupingBy { it.uid }.eachCount().maxByOrNull { it.value }?.key
        _summary.value = RadarSummary(
            callsPerMin = eventTimes.size,
            activeConns = snap.size,
            topApp = top?.let { source.appLabel(it) } ?: "—",
        )
    }

    private companion object {
        const val POLL_MS = 1200L
        const val MAX_LINES = 80
    }
}

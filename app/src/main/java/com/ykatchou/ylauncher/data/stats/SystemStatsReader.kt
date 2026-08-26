package com.ykatchou.ylauncher.data.stats

import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import com.ykatchou.ylauncher.data.running.ShizukuShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects one [SystemStats] reading.
 *
 * Each metric comes from the cheapest source that can supply it: memory straight off procfs,
 * battery through the public manager, and CPU only through Shizuku — `/proc/stat` returns
 * Permission denied to an ordinary app, verified on device.
 */
@Singleton
class SystemStatsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    suspend fun read(): SystemStats {
        val mem = readMemInfo()
        val netRate = readNetRate()
        return SystemStats(
            memAvailableBytes = mem?.second,
            memTotalBytes = mem?.first,
            cpuPercent = readCpuPercent(),
            swapUsedBytes = mem?.let { it.third - it.fourth },
            swapTotalBytes = mem?.third,
            netRxBytesPerSec = netRate.first,
            netTxBytesPerSec = netRate.second,
            foregroundServices = readForegroundServices(),
            currentMilliAmps = readCurrentMilliAmps(),
            temperatureCelsius = readTemperature(),
        )
    }

    /**
     * RAM total/available and the zram pool total/free, in bytes. All four come from the same
     * file, readable without any permission.
     */
    private data class MemInfo(val first: Long, val second: Long, val third: Long, val fourth: Long)

    private fun readMemInfo(): MemInfo? = runCatching {
        var total: Long? = null
        var available: Long? = null
        var swapTotal: Long? = null
        var swapFree: Long? = null
        File("/proc/meminfo").forEachLine { line ->
            when {
                line.startsWith("MemTotal:") -> total = line.kbValue()
                line.startsWith("MemAvailable:") -> available = line.kbValue()
                line.startsWith("SwapTotal:") -> swapTotal = line.kbValue()
                line.startsWith("SwapFree:") -> swapFree = line.kbValue()
            }
        }
        MemInfo(
            first = total ?: return null,
            second = available ?: return null,
            third = swapTotal ?: 0L,
            fourth = swapFree ?: 0L,
        )
    }.getOrNull()

    /** `MemTotal:       12065156 kB` -> bytes */
    private fun String.kbValue(): Long? =
        Regex("""(\d+)""").find(this)?.groupValues?.get(1)?.toLongOrNull()?.times(1024)

    /**
     * Two samples spaced far enough apart to be meaningful. `/proc/stat` counts since boot, so a
     * single read describes the whole uptime rather than the present moment.
     */
    private suspend fun readCpuPercent(): Int? {
        val first = ShizukuShell.run(CAT_PROC_STAT)?.let(CpuStatParser::parse) ?: return null
        delay(SAMPLE_GAP_MS)
        val second = ShizukuShell.run(CAT_PROC_STAT)?.let(CpuStatParser::parse) ?: return null
        return CpuStatParser.loadPercent(first, second)
    }


    /** The property is documented in microamps; negative means draining. */
    private fun readCurrentMilliAmps(): Int? = runCatching {
        val microAmps = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (microAmps == Long.MIN_VALUE || microAmps == 0L) null else (microAmps / 1000).toInt()
    }.getOrNull()

    /** Reported in tenths of a degree. */
    private fun readTemperature(): Float? = runCatching {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            ?.takeIf { it > 0 }
            ?.let { it / 10f }
    }.getOrNull()


    /**
     * Apps holding a foreground service. This is the number the running-apps column cannot show:
     * an app can be absent from the task list and still be working.
     */
    private fun readForegroundServices(): Int? =
        ShizukuShell.run("dumpsys activity services | grep -c isForeground=true")
            ?.trim()
            ?.toIntOrNull()

    /**
     * Throughput since the previous call, as bytes per second. TrafficStats exposes only
     * cumulative counters, so a rate exists solely as a delta between two reads — the first call
     * after start has nothing to compare against and reports nothing.
     */
    private fun readNetRate(): Pair<Long?, Long?> {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val nowMs = System.currentTimeMillis()
        if (rx == TrafficStats.UNSUPPORTED.toLong()) return null to null

        val prevRx = lastRxBytes
        val prevTx = lastTxBytes
        val prevAt = lastNetSampleAt
        lastRxBytes = rx
        lastTxBytes = tx
        lastNetSampleAt = nowMs

        if (prevRx == null || prevTx == null || prevAt == 0L) return null to null
        val seconds = (nowMs - prevAt) / 1000.0
        if (seconds <= 0) return null to null
        return ((rx - prevRx) / seconds).toLong().coerceAtLeast(0) to
            ((tx - prevTx) / seconds).toLong().coerceAtLeast(0)
    }

    private var lastRxBytes: Long? = null
    private var lastTxBytes: Long? = null
    private var lastNetSampleAt = 0L

    private companion object {
        const val CAT_PROC_STAT = "cat /proc/stat"
        const val SAMPLE_GAP_MS = 500L
    }
}

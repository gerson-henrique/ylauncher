package com.ykatchou.ylauncher.data.stats

import android.content.Context
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
        return SystemStats(
            memAvailableBytes = mem?.second,
            memTotalBytes = mem?.first,
            cpuPercent = readCpuPercent(),
            batteryPercent = readBatteryPercent(),
            currentMilliAmps = readCurrentMilliAmps(),
            temperatureCelsius = readTemperature(),
        )
    }

    /** Total and available bytes. Readable without any permission. */
    private fun readMemInfo(): Pair<Long, Long>? = runCatching {
        var total: Long? = null
        var available: Long? = null
        File("/proc/meminfo").forEachLine { line ->
            when {
                line.startsWith("MemTotal:") -> total = line.kbValue()
                line.startsWith("MemAvailable:") -> available = line.kbValue()
            }
        }
        val t = total ?: return null
        val a = available ?: return null
        t to a
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

    private fun readBatteryPercent(): Int? = runCatching {
        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
    }.getOrNull()

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

    private companion object {
        const val CAT_PROC_STAT = "cat /proc/stat"
        const val SAMPLE_GAP_MS = 500L
    }
}

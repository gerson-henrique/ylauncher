package com.ykatchou.ylauncher.data.stats

/**
 * A reading of what the open apps are costing right now.
 *
 * Every field is nullable because each comes from a different source with its own way of being
 * unavailable — CPU needs Shizuku, current draw is not exposed on every device. A missing value
 * is left out of the panel rather than shown as a zero, which would read as a real measurement.
 */
data class SystemStats(
    /** Bytes of RAM available to start new work, not merely unallocated. */
    val memAvailableBytes: Long? = null,
    val memTotalBytes: Long? = null,
    /** Whole-device CPU load, 0–100. Null without Shizuku. */
    val cpuPercent: Int? = null,
    /**
     * 1-minute load average: processes competing for CPU, which is not the same as CPU busy.
     * A core can be 100% busy with nothing queued, or 40% busy with a queue ten deep.
     */
    val loadAverage: Float? = null,
    /** Cores on this device — the real ceiling for [loadAverage]. */
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    /** Bytes per second across all interfaces since the previous sample. */
    val netRxBytesPerSec: Long? = null,
    val netTxBytesPerSec: Long? = null,
    /** Apps running a persistent service without being open. Null without Shizuku. */
    val foregroundServices: Int? = null,
    /** Milliamps: negative while draining, positive while charging. */
    val currentMilliAmps: Int? = null,
    /** Battery temperature in Celsius. */
    val temperatureCelsius: Float? = null,
) {
    val memUsedFraction: Float?
        get() {
            val total = memTotalBytes ?: return null
            val available = memAvailableBytes ?: return null
            if (total <= 0) return null
            return ((total - available).toFloat() / total).coerceIn(0f, 1f)
        }

    val isCharging: Boolean get() = (currentMilliAmps ?: 0) > 0

    /**
     * Load as a share of capacity. Unlike milliamps, this one has a real ceiling: one process per
     * core is exactly saturated, so the bar is measuring against something rather than a guess.
     */
    val loadFraction: Float?
        get() = loadAverage?.let { (it / cpuCores.coerceAtLeast(1)).coerceIn(0f, 1f) }
}

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
     * Bytes the kernel is holding compressed in zram because they would not otherwise fit.
     *
     * This is the honest memory-pressure signal, and it disagrees with "GB free" on purpose:
     * MemAvailable counts reclaimable cache, so it stays comfortable while the kernel is already
     * compressing to cope. Swap in use only happens under real pressure.
     */
    val swapUsedBytes: Long? = null,
    val swapTotalBytes: Long? = null,
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

    /** Share of the zram pool in use. Its size is a real ceiling the kernel itself set. */
    val swapUsedFraction: Float?
        get() {
            val total = swapTotalBytes ?: return null
            val used = swapUsedBytes ?: return null
            if (total <= 0) return null
            return (used.toFloat() / total).coerceIn(0f, 1f)
        }
}

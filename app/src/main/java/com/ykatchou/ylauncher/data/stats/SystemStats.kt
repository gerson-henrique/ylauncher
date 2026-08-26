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
    /** Battery charge, 0–100. */
    val batteryPercent: Int? = null,
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
}

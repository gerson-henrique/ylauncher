package com.ykatchou.ylauncher.data.stats

/**
 * Turns two `/proc/stat` readings into a load percentage.
 *
 * The file holds cumulative jiffies since boot, so a single reading says nothing about now — it
 * describes the whole uptime. Load only exists as the difference between two samples, which is
 * why this takes a pair.
 */
object CpuStatParser {

    /**
     * The aggregate `cpu` line, e.g. `cpu 27157416 5331272 25293755 516951642 146399 ...`
     * Fields, in order: user, nice, system, idle, iowait, irq, softirq, steal.
     */
    data class Sample(val idle: Long, val total: Long)

    fun parse(procStat: String): Sample? {
        val line = procStat.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val values = line.removePrefix("cpu ").trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (values.size < IDLE_INDEX + 2) return null

        // idle + iowait: both are time the CPU spent doing no work.
        val idle = values[IDLE_INDEX] + values[IDLE_INDEX + 1]
        return Sample(idle = idle, total = values.sum())
    }

    /** Load between two samples, 0–100. Null when the samples cannot yield a meaningful delta. */
    fun loadPercent(first: Sample, second: Sample): Int? {
        val totalDelta = second.total - first.total
        val idleDelta = second.idle - first.idle
        // A zero or negative delta means the counters did not advance, or wrapped after a
        // suspend — either way there is no load to report, so say nothing rather than 0%.
        if (totalDelta <= 0) return null
        val busy = (totalDelta - idleDelta).toFloat() / totalDelta
        return (busy * 100).toInt().coerceIn(0, 100)
    }

    private const val IDLE_INDEX = 3
}

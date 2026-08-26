package com.ykatchou.ylauncher.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuStatParserTest {

    /** Captured from a BV9300 Pro running Android 15. */
    private val dump = """
        cpu  27157416 5331272 25293755 516951642 146399 6951347 1349952 0 0 0
        cpu0 4303438 639247 5803857 56786150 25938 3203695 352458 0 0 0
        intr 1234567
    """.trimIndent()

    @Test
    fun `reads the aggregate line, not the first per-core one`() {
        val sample = CpuStatParser.parse(dump)!!
        // idle + iowait from the "cpu " line: 516951642 + 146399
        assertEquals(517098041L, sample.idle)
    }

    @Test
    fun `load is the busy share between two samples`() {
        // 100 jiffies pass, 25 of them idle -> 75% busy.
        val first = CpuStatParser.Sample(idle = 1000, total = 2000)
        val second = CpuStatParser.Sample(idle = 1025, total = 2100)
        assertEquals(75, CpuStatParser.loadPercent(first, second))
    }

    @Test
    fun `fully idle reads as zero, fully busy as one hundred`() {
        assertEquals(
            0,
            CpuStatParser.loadPercent(
                CpuStatParser.Sample(idle = 1000, total = 2000),
                CpuStatParser.Sample(idle = 1100, total = 2100),
            ),
        )
        assertEquals(
            100,
            CpuStatParser.loadPercent(
                CpuStatParser.Sample(idle = 1000, total = 2000),
                CpuStatParser.Sample(idle = 1000, total = 2100),
            ),
        )
    }

    /**
     * Counters that did not advance mean no measurement, not an idle CPU — reporting 0% would
     * claim a reading that was never taken.
     */
    @Test
    fun `says nothing when the counters did not advance`() {
        val same = CpuStatParser.Sample(idle = 1000, total = 2000)
        assertNull(CpuStatParser.loadPercent(same, same))
    }

    @Test
    fun `survives output without a cpu line`() {
        assertNull(CpuStatParser.parse("intr 123\nctxt 456"))
        assertNull(CpuStatParser.parse(""))
    }
}

package com.ykatchou.ylauncher.data.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DangerGateTest {

    @Test
    fun `read-only diagnostics run without asking`() {
        listOf(
            "getprop ro.product.model",
            "dumpsys meminfo com.whatsapp",
            "cat /proc/meminfo",
            "pm list packages",
            "settings get system min_refresh_rate",
            "top -n 1",
            "ps -A",
        ).forEach { assertFalse("should be safe: $it", DangerGate.isDangerous(it)) }
    }

    @Test
    fun `state-changing and destructive commands trip the gate`() {
        listOf(
            "rm -rf /sdcard/x",
            "pm uninstall com.whatsapp",
            "pm clear com.instagram.android",
            "am force-stop com.spotify.music",
            "settings put system min_refresh_rate 60",
            "reboot",
            "svc power reboot",
            "setprop persist.x 1",
        ).forEach { assertTrue("should be dangerous: $it", DangerGate.isDangerous(it)) }
    }

    @Test
    fun `classification ignores case`() {
        assertTrue(DangerGate.isDangerous("PM Uninstall com.foo"))
    }

    @Test
    fun `reason names the matched token, or null when safe`() {
        assertEquals("pm uninstall", DangerGate.reason("pm uninstall com.foo"))
        assertNull(DangerGate.reason("getprop ro.build.version.sdk"))
    }
}

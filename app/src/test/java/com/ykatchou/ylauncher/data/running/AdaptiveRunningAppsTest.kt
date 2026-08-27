package com.ykatchou.ylauncher.data.running

import android.os.UserHandle
import com.ykatchou.ylauncher.data.model.AppInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveRunningAppsTest {

    private val user = mockk<UserHandle>(relaxed = true)
    private fun app(pkg: String) = AppInfo(pkg, pkg, null, user)

    private val shizuku = mockk<ShizukuRunningApps>(relaxed = true)
    private val usageStats = mockk<UsageStatsRunningApps>(relaxed = true)
    private val adaptive = AdaptiveRunningApps(shizuku, usageStats)

    /**
     * Regression: closing every app makes the privileged source return an empty list. Treating
     * that as a failure fell through to usage stats, which lists recently-*used* apps — so the
     * column refilled with exactly the apps the user had just killed.
     */
    @Test
    fun `an empty privileged result is trusted, not treated as a failure`() = runTest {
        every { shizuku.isAvailable() } returns true
        coEvery { shizuku.getRunningApps(any()) } returns emptyList()
        coEvery { usageStats.getRunningApps(any()) } returns listOf(app("com.recently.used"))

        assertEquals(emptyList<AppInfo>(), adaptive.getRunningApps(6))
    }

    @Test
    fun `a privileged read that fails falls back to usage stats`() = runTest {
        every { shizuku.isAvailable() } returns true
        coEvery { shizuku.getRunningApps(any()) } returns null
        coEvery { usageStats.getRunningApps(any()) } returns listOf(app("com.fallback"))

        assertEquals(listOf(app("com.fallback")), adaptive.getRunningApps(6))
    }

    @Test
    fun `both sources failing yields an empty column rather than a crash`() = runTest {
        every { shizuku.isAvailable() } returns true
        coEvery { shizuku.getRunningApps(any()) } returns null
        coEvery { usageStats.getRunningApps(any()) } returns null

        assertEquals(emptyList<AppInfo>(), adaptive.getRunningApps(6))
    }

    @Test
    fun `without shizuku it reads usage stats directly`() = runTest {
        every { shizuku.isAvailable() } returns false
        coEvery { usageStats.getRunningApps(any()) } returns listOf(app("com.floor"))

        assertEquals(listOf(app("com.floor")), adaptive.getRunningApps(6))
    }
}

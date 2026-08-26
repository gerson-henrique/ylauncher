package com.ykatchou.ylauncher.data.running

import com.ykatchou.ylauncher.data.model.AppInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uses the privileged source when it is up and the usage-stats floor when it is not.
 *
 * The choice is made per call rather than once at startup, because Shizuku does not survive a
 * reboot: the launcher can be running long before the service comes back, and must not be stuck
 * on the degraded source until it is restarted. The launcher is the one app that has to keep
 * working, so it never *depends* on Shizuku — it only gets better when Shizuku is there.
 */
@Singleton
class AdaptiveRunningApps @Inject constructor(
    private val shizuku: ShizukuRunningApps,
    private val usageStats: UsageStatsRunningApps,
) : RunningAppsSource {

    private val active: RunningAppsSource
        get() = if (shizuku.isAvailable()) shizuku else usageStats

    override val canClose: Boolean
        get() = active.canClose

    /**
     * Falls back only when the chosen source could not read — never when it read successfully and
     * found nothing.
     *
     * Treating "empty" as "broken" was a real bug: closing every app produces an empty list, which
     * sent the column to the usage-stats floor, which lists recently-*used* apps — so the moment
     * the user finished closing everything, the column repopulated with the very apps they had
     * just killed. Empty is an answer, and here it is the most important one.
     */
    override suspend fun getRunningApps(limit: Int): List<AppInfo> {
        val chosen = active
        chosen.getRunningApps(limit)?.let { return it }
        if (chosen !== usageStats) usageStats.getRunningApps(limit)?.let { return it }
        return emptyList()
    }

    override suspend fun close(app: AppInfo): Boolean = active.close(app)
}

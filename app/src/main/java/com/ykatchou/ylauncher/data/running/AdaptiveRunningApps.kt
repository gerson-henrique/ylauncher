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

    override suspend fun getRunningApps(limit: Int): List<AppInfo> {
        val chosen = active
        val apps = chosen.getRunningApps(limit)
        // A privileged read that comes back empty is more likely a broken binder than an idle
        // device — fall back rather than showing an empty column.
        return if (apps.isEmpty() && chosen !== usageStats) {
            usageStats.getRunningApps(limit)
        } else {
            apps
        }
    }

    override suspend fun close(app: AppInfo): Boolean = active.close(app)
}

package com.ykatchou.ylauncher.data.running

import android.app.usage.UsageStatsManager
import android.content.Context
import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.repository.AppRepository
import com.ykatchou.ylauncher.util.UsageStatsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The floor: builds the list from usage statistics, which need only a permission the user can
 * grant. This reports apps *recently used* rather than apps *currently running* — close enough to
 * switch between, since tapping one resumes its existing task, but it cannot end anything.
 */
@Singleton
class UsageStatsRunningApps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
) : RunningAppsSource {

    override val canClose = false

    override suspend fun getRunningApps(limit: Int): List<AppInfo>? {
        // No permission means this source cannot read, which is not the same as nothing
        // running — say so with null instead of claiming an idle device.
        if (!UsageStatsHelper.hasPermission(context)) return null

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Query the daily bucket and narrow by timestamp afterwards. Asking for a short window
        // directly comes back empty — the buckets are day-sized, so a 6h range matches nothing.
        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - DAY_MS,
            now,
        ) ?: return null

        val cutoff = now - RECENT_WINDOW_MS
        return stats
            .filter {
                it.packageName !in excludedPackages &&
                    it.lastTimeUsed > cutoff &&
                    it.totalTimeInForeground > 0
            }
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }
            .mapNotNull { appRepository.findAppByPackage(it.packageName) }
            .take(limit)
    }

    override suspend fun close(app: AppInfo) = false

    private val excludedPackages: Set<String>
        get() = setOf(
            // Showing ourselves would be noise — the launcher is always "running".
            context.packageName,
            // The stock launcher still runs here because it provides the system's gesture
            // handler, but it is not an app the user opened.
            "com.blackview.launcher",
            "com.android.launcher3",
        )

    private companion object {
        /** Anything untouched for longer than this has stopped being "what I have open". */
        const val RECENT_WINDOW_MS = 6L * 60 * 60 * 1000
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

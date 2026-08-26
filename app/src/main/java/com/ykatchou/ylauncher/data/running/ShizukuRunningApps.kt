package com.ykatchou.ylauncher.data.running

import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.repository.AppRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The privileged grade: real tasks, and the ability to end them.
 *
 * Reads the actual task list rather than inferring it from usage, so an app opened and left
 * sitting still shows up, and one that was never opened does not.
 */
@Singleton
class ShizukuRunningApps @Inject constructor(
    private val appRepository: AppRepository,
) : RunningAppsSource {

    override val canClose = true

    fun isAvailable(): Boolean = ShizukuShell.isReady()

    override suspend fun getRunningApps(limit: Int): List<AppInfo> {
        val dump = ShizukuShell.run(DUMP_RECENTS) ?: return emptyList()
        return RecentTasksParser.packages(dump)
            .mapNotNull { appRepository.findAppByPackage(it) }
            .take(limit)
    }

    /**
     * Ending an app is two operations, not one. force-stop kills the processes but leaves the task
     * sitting in the recents list, so the app would keep showing in the column as a ghost;
     * removing the task without the stop would hide an app that is still very much alive.
     *
     * The task id is read before the stop, while the entry is guaranteed to still be there.
     */
    override suspend fun close(app: AppInfo): Boolean {
        val dump = ShizukuShell.run(DUMP_RECENTS)
        val taskId = dump?.let { RecentTasksParser.taskIdOf(it, app.packageName) }

        val stopped = ShizukuShell.run("am force-stop ${app.packageName}") != null
        if (taskId != null) ShizukuShell.run("am stack remove $taskId")

        return stopped
    }

    private companion object {
        const val DUMP_RECENTS = "dumpsys activity recents"
    }
}

package com.ykatchou.ylauncher.data.running

import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.repository.AppRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The privileged grade: real tasks, and the ability to end them.
 *
 * Reads the actual task list rather than inferring it from usage, so an app that was opened and
 * left sitting still shows up, and one that was never opened does not.
 */
@Singleton
class ShizukuRunningApps @Inject constructor(
    private val appRepository: AppRepository,
) : RunningAppsSource {

    override val canClose = true

    fun isAvailable(): Boolean = ShizukuShell.isReady()

    override suspend fun getRunningApps(limit: Int): List<AppInfo> {
        val output = ShizukuShell.run("dumpsys activity recents") ?: return emptyList()

        return output.lineSequence()
            .filter { it.contains("Recent #") }
            // type= tells home and recents apart from real apps, so the launcher itself and the
            // system's gesture handler never show up as something the user opened.
            .filter { TYPE_STANDARD in it }
            .mapNotNull { PACKAGE_IN_TASK.find(it)?.groupValues?.get(1) }
            .distinct()
            .mapNotNull { appRepository.findAppByPackage(it) }
            .take(limit)
            .toList()
    }

    /**
     * Ending an app is two operations, not one. force-stop kills the processes but leaves the
     * task sitting in the recents list, so the app would keep showing in the column as a ghost;
     * removing the task without the stop would hide an app that is still very much alive.
     */
    override suspend fun close(app: AppInfo): Boolean {
        val pkg = app.packageName
        val taskId = findTaskId(pkg)

        val stopped = ShizukuShell.run("am force-stop $pkg") != null
        if (taskId != null) ShizukuShell.run("am stack remove $taskId")

        return stopped
    }

    private fun findTaskId(pkg: String): String? {
        val output = ShizukuShell.run("dumpsys activity recents") ?: return null
        val line = output.lineSequence().firstOrNull { it.contains("Recent #") && it.contains(pkg) }
            ?: return null
        return TASK_ID.find(line)?.groupValues?.get(1)
    }

    private companion object {
        const val TYPE_STANDARD = "type=standard"

        /** `A=10384:com.facebook.katana` or `I=com.pkg/.Activity` in a `Recent #n` line. */
        val PACKAGE_IN_TASK = Regex("""[AI]=(?:\d+:)?([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)""")

        /** `Task{eb9eba0 #1444 type=...}` — the number after the hash. */
        val TASK_ID = Regex("""#(\d+)""")
    }
}

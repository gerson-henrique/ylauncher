package com.ykatchou.ylauncher.data.running

/**
 * Reads `dumpsys activity recents` output. Pure string work, kept apart from the shell call so it
 * can be tested against real dump lines — the shapes here are fiddly enough to have already
 * produced one wrong-target bug.
 */
object RecentTasksParser {

    private const val TYPE_STANDARD = "type=standard"

    /** `A=10384:com.facebook.katana` or `I=com.pkg/.Activity` within a `Recent #n` line. */
    private val PACKAGE_IN_TASK = Regex("""[AI]=(?:\d+:)?([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)""")

    /**
     * The id inside `Task{d10633a #1498 type=...}`. Anchored on `Task{` on purpose: the line opens
     * with `Recent #4:`, so an unanchored `#(\d+)` captures the list position instead, and the app
     * ends up asking the system to remove a completely unrelated task.
     */
    private val TASK_ID = Regex("""Task\{[0-9a-f]+\s+#(\d+)""")

    /**
     * Packages of the real apps in the list, in order. `type=` separates apps from the launcher
     * itself (`type=home`) and the system's gesture handler (`type=recents`), neither of which is
     * something the user opened.
     */
    fun packages(dump: String): List<String> =
        recentLines(dump)
            .filter { TYPE_STANDARD in it }
            .mapNotNull { PACKAGE_IN_TASK.find(it)?.groupValues?.get(1) }
            .distinct()
            .toList()

    /** The task id hosting [packageName], or null when it is not in the list. */
    fun taskIdOf(dump: String, packageName: String): String? =
        recentLines(dump)
            .filter { TYPE_STANDARD in it }
            .firstOrNull { PACKAGE_IN_TASK.find(it)?.groupValues?.get(1) == packageName }
            ?.let { TASK_ID.find(it)?.groupValues?.get(1) }

    private fun recentLines(dump: String) =
        dump.lineSequence().filter { it.contains("Recent #") }
}

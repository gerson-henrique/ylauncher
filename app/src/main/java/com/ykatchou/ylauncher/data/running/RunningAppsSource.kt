package com.ykatchou.ylauncher.data.running

import com.ykatchou.ylauncher.data.model.AppInfo

/**
 * Where the home screen's left column gets its list of open apps.
 *
 * Android gives an ordinary app no way to see what is running or to end it — those doors were
 * closed on purpose. So this comes in two grades, and the column adapts to whichever is available:
 * a permission-only floor that can list and switch, and a privileged one that can also close.
 * The column must stay useful on the floor, because the privileged source is not always up.
 */
interface RunningAppsSource {

    /** Whether [close] does anything. When false the column omits the close affordance entirely. */
    val canClose: Boolean

    /**
     * Apps to show, most recent first, or null when this source could not read at all.
     *
     * The distinction matters: an empty list is a real answer meaning nothing is open, and it is
     * the answer you get precisely when the user has just closed everything. Collapsing it into
     * "no data" sends the column to a fallback that lists recently-used apps, resurrecting on
     * screen exactly the apps that were just killed.
     */
    suspend fun getRunningApps(limit: Int): List<AppInfo>?

    /** Ends [app]. Returns whether it actually died. Always false when [canClose] is false. */
    suspend fun close(app: AppInfo): Boolean
}

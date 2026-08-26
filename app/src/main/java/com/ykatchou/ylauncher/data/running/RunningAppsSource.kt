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

    /** Apps to show, most recently used first. */
    suspend fun getRunningApps(limit: Int): List<AppInfo>

    /** Ends [app]. Returns whether it actually died. Always false when [canClose] is false. */
    suspend fun close(app: AppInfo): Boolean
}

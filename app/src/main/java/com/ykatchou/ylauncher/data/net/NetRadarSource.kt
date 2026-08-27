package com.ykatchou.ylauncher.data.net

import android.content.Context
import android.content.pm.PackageManager
import com.ykatchou.ylauncher.data.running.ShizukuShell
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A live snapshot of the sockets this device has open, read through Shizuku. One shell round-trip
 * cats the four socket tables with a marker each; [ProcNetParser] turns them into connections and
 * this maps the owning uid back to an app the user recognises.
 */
@Singleton
class NetRadarSource @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val pm: PackageManager = context.packageManager
    private val labelCache = HashMap<Int, String>()

    /** Active remote connections, or null when Shizuku could not run the read. */
    fun snapshot(): List<ProcNetParser.Conn>? {
        val out = ShizukuShell.run(READ_CMD) ?: return null
        return ProcNetParser.parse(out)
    }

    /** A human name for the app behind [uid] — cached, since it never changes for a uid. */
    fun appLabel(uid: Int): String = labelCache.getOrPut(uid) {
        when {
            uid == 0 -> "root"
            uid < 10000 -> "sistema"
            else -> resolve(uid)
        }
    }

    // getPackagesForUid throws a SecurityException for uids that live in another user/profile
    // (cloned/dual apps): Shizuku sees every user's sockets, but our PackageManager runs as user 0
    // and may not cross that boundary. Fall back to the bare uid rather than crashing the loop.
    private fun resolve(uid: Int): String = try {
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull()
        if (pkg == null) "uid $uid"
        else pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        "uid $uid"
    }

    private companion object {
        // One round-trip for all four tables; the #marker keeps tcp/udp apart for the parser.
        const val READ_CMD =
            "for f in tcp6 udp6 tcp udp; do echo \"#\$f\"; cat /proc/net/\$f 2>/dev/null; done"
    }
}

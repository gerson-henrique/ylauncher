package com.ykatchou.ylauncher.data.running

import com.ykatchou.ylauncher.util.YLogger
import rikka.shizuku.Shizuku

/**
 * Runs a shell command with Shizuku's borrowed `shell` identity.
 *
 * Shizuku exposes `newProcess` only as a hidden method, so it is reached by reflection. That is
 * deliberate rather than lazy: the alternative is calling `IActivityTaskManager` and
 * `IActivityManager` through their hidden AIDL interfaces, which means shipping stub definitions
 * that have to track platform changes. The two commands this app needs were verified to work
 * under `shell` on the target device, and a command line is a far smaller surface to keep working
 * across Android versions than a pair of private binder interfaces.
 */
object ShizukuShell {

    private const val TAG = "ShizukuShell"

    /** Long enough to keep it out of hot paths, short enough to notice Shizuku coming up. */
    private const val READY_CACHE_MS = 2000L

    @Volatile private var readyCache: Boolean? = null
    @Volatile private var readyCheckedAt = 0L

    /**
     * Whether Shizuku is present, running, and has granted us permission.
     *
     * Cached briefly because this crosses a binder, and callers ask often — the answer changes
     * only when the service starts or stops, which is not something that happens between two
     * frames. Without the cache a caller in a hot path turns every check into an IPC, and when
     * Shizuku is down each one reaches for a service that is not there.
     */
    fun isReady(): Boolean {
        val now = System.currentTimeMillis()
        readyCache?.let { if (now - readyCheckedAt < READY_CACHE_MS) return it }
        val result = try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            // Shizuku not installed at all — its classes resolve but the binder never appears.
            false
        }
        readyCache = result
        readyCheckedAt = now
        return result
    }

    /** True when Shizuku is up but has not been asked for permission yet. */
    fun needsPermission(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
            .onFailure { YLogger.e(TAG, "requestPermission failed", it as? Exception ?: Exception(it)) }
    }

    /** Runs [command] and returns its stdout, or null if it could not run. */
    fun run(command: String): String? {
        if (!isReady()) return null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
            ).apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val process = method.invoke(
                null, arrayOf("sh", "-c", command), null, null,
            ) as Process

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (t: Throwable) {
            YLogger.e(TAG, "shell command failed: $command", t as? Exception ?: Exception(t))
            null
        }
    }
}

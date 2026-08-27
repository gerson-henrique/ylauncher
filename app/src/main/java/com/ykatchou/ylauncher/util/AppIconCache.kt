package com.ykatchou.ylauncher.util

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Process-wide LRU cache for app icon bitmaps.
 *
 * Keyed by "packageName|sizePx" so the same app appearing in favourites, a folder popup and the
 * drawer shares one ImageBitmap allocation.
 *
 * The cache also owns *loading*, which is the point. Drawables are expensive in native memory —
 * an adaptive icon carries a foreground and a background bitmap sized to screen density — so the
 * app must never hold a collection of them. Loading here means each Drawable lives only long
 * enough to be rasterised into the small cached bitmap, then becomes garbage.
 */
object AppIconCache {
    // 150 entries × ~7 KB (44×44 ARGB_8888) ≈ 1 MB upper bound
    private val cache = LruCache<String, ImageBitmap>(150)

    fun get(drawable: Drawable, packageName: String, sizePx: Int): ImageBitmap {
        val key = "$packageName|$sizePx"
        return cache[key] ?: drawable.toBitmap(width = sizePx, height = sizePx)
            .asImageBitmap()
            .also { cache.put(key, it) }
    }

    fun getIfCached(packageName: String, sizePx: Int): ImageBitmap? =
        cache["$packageName|$sizePx"]

    /**
     * Returns the cached bitmap, loading the icon from the system if this is the first request.
     *
     * Call off the main thread: resolving an icon hits the package manager and decodes bitmaps.
     */
    fun load(
        context: Context,
        packageName: String,
        activityClassName: String?,
        user: UserHandle,
        sizePx: Int,
    ): ImageBitmap? {
        getIfCached(packageName, sizePx)?.let { return it }
        val drawable = loadDrawable(context, packageName, activityClassName, user) ?: return null
        return get(drawable, packageName, sizePx)
    }

    private fun loadDrawable(
        context: Context,
        packageName: String,
        activityClassName: String?,
        user: UserHandle,
    ): Drawable? = runCatching {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activities = launcherApps.getActivityList(packageName, user)
        val match = activityClassName
            ?.let { name -> activities.firstOrNull { it.componentName.className == name } }
            ?: activities.firstOrNull()
        match?.getIcon(0)
    }.getOrNull()

    fun evict(packageName: String) {
        cache.snapshot().keys
            .filter { it.startsWith("$packageName|") }
            .forEach { cache.remove(it) }
    }
}

package com.ykatchou.ylauncher.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The launcher's own home background, stored inside the app. Android 15 refuses to let a launcher
 * read the *system* wallpaper without invasive permissions, and the window can no longer rely on
 * `windowShowWallpaper` (its translucent-window compositor path is what made the home vanish). So
 * the home draws its own background instead: the user picks an image through the system photo
 * picker (no permission needed), we copy the bytes here, and [com.ykatchou.ylauncher.ui.components.WallpaperBackground]
 * paints this file. Fully decoupled from the system wallpaper — the lock screen keeps its own.
 */
object LauncherBackground {
    /** Bumped whenever the image changes, so the background composable reloads. */
    val version = MutableStateFlow(0)

    fun file(context: Context): File = File(context.filesDir, "home_background.jpg")

    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    /** Copy the picked image into app storage. Returns true on success. */
    suspend fun set(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = file(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            version.value += 1
            true
        } catch (t: Throwable) {
            YLogger.e("LauncherBackground", "failed to store background", t as? Exception ?: Exception(t))
            false
        }
    }
}

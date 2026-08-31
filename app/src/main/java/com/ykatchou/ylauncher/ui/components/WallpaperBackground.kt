package com.ykatchou.ylauncher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.util.LauncherBackground
import com.ykatchou.ylauncher.util.YLogger

/**
 * Draws the launcher's own home background — an image the user picked, copied into app storage by
 * [LauncherBackground]. The window is opaque now (not `windowShowWallpaper`), because that
 * translucent-window path is what made the home vanish on this device's compositor. This paints the
 * background behind everything; if none is set yet, it draws nothing and the black window shows.
 */
@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by LauncherBackground.version.collectAsStateWithLifecycle()
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(version) {
        image = try {
            val f = LauncherBackground.file(context)
            if (f.exists() && f.length() > 0) {
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            } else {
                null
            }
        } catch (t: Throwable) {
            YLogger.d("WallpaperBackground", "decode failed: ${t.javaClass.simpleName}")
            null
        }
    }

    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

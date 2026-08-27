package com.ykatchou.ylauncher.ui.components

import android.os.UserHandle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.ykatchou.ylauncher.util.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app icon, loaded on demand.
 *
 * Every icon on screen goes through here so the loading rule lives in one place: check the cache
 * synchronously — a hit draws on the first frame with no flicker — and only on a miss go to the
 * system, off the main thread.
 *
 * Nothing is drawn until there is a real bitmap. A placeholder would flash on every cache hit,
 * which is most of them.
 */
@Composable
fun AppIcon(
    packageName: String,
    activityClassName: String?,
    user: UserHandle,
    size: Dp,
    sizePx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? by produceState(
        initialValue = AppIconCache.getIfCached(packageName, sizePx),
        key1 = packageName,
        key2 = sizePx,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppIconCache.load(context, packageName, activityClassName, user, sizePx)
            }
        }
    }

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
        )
    }
}

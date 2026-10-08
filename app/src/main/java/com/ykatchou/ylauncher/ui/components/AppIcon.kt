package com.ykatchou.ylauncher.ui.components

import android.os.Process
import android.os.UserHandle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.ui.theme.ProverbBrush
import com.ykatchou.ylauncher.ui.theme.Y
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
        Box(modifier = modifier.size(size)) {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
            )
            if (user != Process.myUserHandle()) {
                WorkSeal(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
            }
        }
    }
}

/**
 * The work-profile stamp: the notification seal's shape in indigo, carrying 職 (work, one's job).
 * It sits bottom-right, under where the count seal lands, so the two never collide. Drawn here so
 * every icon of a work app carries it — the search shows the personal and the work Slack side by
 * side, with the same icon and label, and this is the only thing telling them apart.
 */
@Composable
private fun WorkSeal(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .rotate(-5f)
            .background(Y.sealIndigo, RoundedCornerShape(4.dp))
            .border(1.5.dp, Y.paperTop, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "職",
            style = Y.type.caption.copy(fontFamily = ProverbBrush),
            color = Y.paperTop,
        )
    }
}

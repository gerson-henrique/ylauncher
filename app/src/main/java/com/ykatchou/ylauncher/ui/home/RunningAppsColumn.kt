package com.ykatchou.ylauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.model.AppNotification
import com.ykatchou.ylauncher.ui.components.AppIcon
import com.ykatchou.ylauncher.ui.theme.ProverbBrush
import com.ykatchou.ylauncher.ui.theme.Y

/**
 * The open apps as a "perch of ink": the icons roost along a vertical brushstroke, and dragging one
 * aside ends it — the app flies off, in keeping with the birds in the painting. No labels, no
 * notification preview text: just the icon and, when something is waiting, a seal stamped with the
 * count in a Chinese numeral. Tapping resumes the app, so the column doubles as a task switcher.
 *
 * Drag rather than a close button, and a generous threshold: force-stop has no undo, so ending an
 * app should take a decided gesture, never a stray tap.
 */
@Composable
fun RunningAppsColumn(
    apps: List<AppInfo>,
    canClose: Boolean,
    onOpen: (AppInfo) -> Unit,
    onClose: (AppInfo) -> Unit,
    notifications: Map<String, AppNotification>,
    @Suppress("UNUSED_PARAMETER") showNotifPreview: Boolean,
    showNotifBadge: Boolean,
    @Suppress("UNUSED_PARAMETER") onDismissNotification: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing open draws nothing at all — the quiet home is the point.
    if (apps.isEmpty()) return

    val stem = Y.inkStrong.copy(alpha = 0.55f)
    Column(
        modifier = modifier.drawBehind {
            // The branch: a vertical ink stroke the icons perch to the right of, fading at its ends.
            val x = 3.dp.toPx()
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(stem.copy(alpha = 0.15f), stem, stem.copy(alpha = 0.08f)),
                ),
                start = Offset(x, 6.dp.toPx()),
                end = Offset(x, size.height - 6.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        },
    ) {
        apps.forEach { app ->
            key(app.packageName) {
                val notif = notifications[app.packageName]
                val perch = @Composable { itemModifier: Modifier ->
                    Box(modifier = itemModifier.padding(start = 12.dp, top = 7.dp, bottom = 7.dp)) {
                        AppIcon(
                            packageName = app.packageName,
                            activityClassName = app.activityClassName,
                            user = app.userHandle,
                            size = 44.dp,
                            sizePx = 44,
                            contentDescription = app.appLabel,
                            modifier = Modifier.clickable { onOpen(app) },
                        )
                        if (showNotifBadge && notif != null && notif.count > 0) {
                            SealCount(
                                count = notif.count,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                            )
                        }
                    }
                }

                if (!canClose) {
                    perch(Modifier)
                } else {
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { distance -> distance * 0.5f },
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) onClose(app)
                            // Never confirm: the list is the source of truth. The row disappears
                            // when the app is gone from it, and a failed close springs back.
                            false
                        },
                    )
                    // No red "Close" backdrop — the drift + spring is the whole gesture, quiet like
                    // the rest of the painting.
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {},
                    ) {
                        perch(Modifier)
                    }
                }
            }
        }
    }
}

/** A seal stamped with the notification count, in a Chinese numeral. */
@Composable
private fun SealCount(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .rotate(-5f)
            .background(Y.seal, RoundedCornerShape(4.dp))
            .border(1.5.dp, Y.paperTop, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = chineseNumeral(count),
            style = Y.type.caption.copy(fontFamily = ProverbBrush),
            color = Y.paperTop,
        )
    }
}

/** 1–10 as 一…十; anything above as 十+ — a seal has no room for 二十七, and the exact count past ten rarely matters at a glance. */
private fun chineseNumeral(n: Int): String = when (n) {
    in 1..10 -> "一二三四五六七八九十"[n - 1].toString()
    else -> "十+"
}

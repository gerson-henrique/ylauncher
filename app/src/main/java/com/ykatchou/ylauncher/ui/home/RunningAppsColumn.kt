package com.ykatchou.ylauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.model.AppNotification
import com.ykatchou.ylauncher.ui.theme.HomeTextColorDim

/**
 * The left column: what is open right now. Tapping an entry resumes it, so the column doubles as
 * a task switcher; dragging one aside ends it.
 *
 * Drag rather than a close button, because the row is already narrow with a label in it, and
 * because a stray tap on a small target would kill an app with no undo — force-stop discards
 * unsaved state and the process is gone. Hence the deliberately generous drag threshold below.
 */
@Composable
fun RunningAppsColumn(
    apps: List<AppInfo>,
    canClose: Boolean,
    onOpen: (AppInfo) -> Unit,
    onClose: (AppInfo) -> Unit,
    notifications: Map<String, AppNotification>,
    showNotifPreview: Boolean,
    showNotifBadge: Boolean,
    onDismissNotification: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Nothing open",
                style = MaterialTheme.typography.bodyMedium,
                color = HomeTextColorDim,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        apps.forEach { app ->
            // Keyed by package so the dismiss state belongs to the app, not to a list position —
            // without this, closing one app hands its half-swiped state to whoever shifts up.
            key(app.packageName) {
                val item = @Composable { itemModifier: Modifier ->
                    FavoriteItem(
                        appInfo = app,
                        displayName = app.appLabel,
                        onClick = { onOpen(app) },
                        notification = notifications[app.packageName],
                        showNotifPreview = showNotifPreview,
                        showNotifBadge = showNotifBadge,
                        onDismissNotification = { onDismissNotification(app.packageName) },
                        modifier = itemModifier,
                    )
                }

                if (!canClose) {
                    item(Modifier)
                } else {
                    val dismissState = rememberSwipeToDismissBoxState(
                        // Half the width. Closing cannot be undone, so it should take a decided
                        // gesture — better to under-trigger than to kill a chat mid-message.
                        positionalThreshold = { distance -> distance * 0.5f },
                        confirmValueChange = { value ->
                            val dismissed = value != SwipeToDismissBoxValue.Settled
                            if (dismissed) onClose(app)
                            dismissed
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            // Only while a drag is actually under way. Drawn unconditionally it
                            // sits behind every row all the time, tinting the column red and
                            // printing "Close" across the app names.
                            val direction = dismissState.dismissDirection
                            if (direction != SwipeToDismissBoxValue.Settled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Red.copy(alpha = 0.25f)),
                                    contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                        Alignment.CenterStart
                                    } else {
                                        Alignment.CenterEnd
                                    },
                                ) {
                                    Text(
                                        text = "Close",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item(Modifier)
                    }
                }
            }
        }
    }
}

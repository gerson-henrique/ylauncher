package com.ykatchou.ylauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.ui.components.dragHandle
import com.ykatchou.ylauncher.ui.components.rememberDragDropListState
import com.ykatchou.ylauncher.ui.components.AppIcon
import com.ykatchou.ylauncher.data.model.FavoriteApp

@Composable
fun EditFavoritesSheet(
    favorites: List<FavoriteApp>,
    resolveApp: (String) -> AppInfo?,
    onSave: (List<FavoriteApp>) -> Unit,
    onAddFolder: () -> Unit,
    onAddApp: () -> Unit,
    onMoveToFolder: (FavoriteApp, Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editList = remember(favorites) { favorites.toMutableStateList() }
    var pendingRemove by remember { mutableStateOf<FavoriteApp?>(null) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Edit Favorites",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = {
                    val reordered = editList.mapIndexed { index, fav ->
                        fav.copy(position = index)
                    }
                    onSave(reordered)
                }) {
                    Text("Save")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val listState = rememberLazyListState()
            val dragDropState = rememberDragDropListState(
                listState = listState,
                canAcceptDrop = { idx -> editList.getOrNull(idx)?.isFolder == true },
                onDropOnTarget = { from, target ->
                    val moved = editList.getOrNull(from)
                    val folderId = editList.getOrNull(target)?.folderId
                    if (moved != null && folderId != null) {
                        onMoveToFolder(moved, folderId)
                        editList.remove(moved)
                    }
                },
            ) { from, to ->
                val item = editList.removeAt(from)
                editList.add(to, item)
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(editList, key = { _, it -> "${it.packageName}_${it.position}_${it.folderId}" }) { index, favorite ->
                    val isDragging = index == dragDropState.draggingItemIndex
                    val isDropHover = index == dragDropState.hoveredDropTargetIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { dragDropState.offsetForItem(index) }
                            .zIndex(if (isDragging) 1f else 0f)
                            .alpha(if (isDragging) 0.9f else 1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (isDropHover) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "☰",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(8.dp)
                                .dragHandle(dragDropState, index)
                                .semantics { contentDescription = "Drag to reorder ${favorite.displayName}" },
                        )

                        // Icon: emoji for folders, app icon for apps
                        if (favorite.isFolder) {
                            Text(
                                text = favorite.iconEmoji ?: "📁",
                                fontSize = 24.sp,
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            val appInfo = remember(favorite.packageName) { resolveApp(favorite.packageName) }
                            appInfo?.let { info ->
                                AppIcon(
                                    packageName = info.packageName,
                                    activityClassName = info.activityClassName,
                                    user = info.userHandle,
                                    size = 32.dp,
                                    sizePx = 32,
                                    contentDescription = favorite.displayName,
                                )
                            }
                        }

                        Text(
                            text = favorite.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        IconButton(
                            onClick = { pendingRemove = favorite },
                            modifier = Modifier.semantics { contentDescription = "Remove ${favorite.displayName}" },
                        ) {
                            Text("✕", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row {
                    TextButton(onClick = onAddApp) {
                        Text("+ Add app")
                    }
                    TextButton(onClick = onAddFolder) {
                        Text("+ Add folder")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }

    pendingRemove?.let { favorite ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove '${favorite.displayName}'?") },
            text = { Text("It won't be uninstalled, just removed from your favorites.") },
            confirmButton = {
                TextButton(onClick = {
                    editList.remove(favorite)
                    pendingRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            },
        )
    }
}

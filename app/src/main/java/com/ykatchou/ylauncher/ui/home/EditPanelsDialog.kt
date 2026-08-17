package com.ykatchou.ylauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.ykatchou.ylauncher.data.model.Panel
import com.ykatchou.ylauncher.ui.components.dragHandle
import com.ykatchou.ylauncher.ui.components.rememberDragDropListState

@Composable
fun EditPanelsDialog(
    panels: List<Panel>,
    onRename: (id: Long, name: String) -> Unit,
    onReorder: (List<Panel>) -> Unit,
    onDelete: (id: Long) -> Unit,
    onAdd: (name: String) -> Unit,
    onToggleEnabled: (id: Long, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val editList = remember(panels) { panels.toMutableStateList() }
    val names = remember(panels) {
        mutableStateMapOf<Long, String>().apply { panels.forEach { put(it.id, it.name) } }
    }
    var newPanelName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Panel?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Manage panels",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                val listState = rememberLazyListState()
                val dragDropState = rememberDragDropListState(listState) { from, to ->
                    val item = editList.removeAt(from)
                    editList.add(to, item)
                }

                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(editList, key = { _, it -> it.id }) { index, panel ->
                        val isDragging = index == dragDropState.draggingItemIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { dragDropState.offsetForItem(index) }
                                .zIndex(if (isDragging) 1f else 0f)
                                .alpha(if (isDragging) 0.9f else 1f)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "☰",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .dragHandle(dragDropState, index)
                                    .semantics { contentDescription = "Drag to reorder ${panel.name}" },
                            )
                            OutlinedTextField(
                                value = names[panel.id] ?: panel.name,
                                onValueChange = { names[panel.id] = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = panel.enabled,
                                onCheckedChange = { checked ->
                                    onToggleEnabled(panel.id, checked)
                                    val idxNow = editList.indexOf(panel)
                                    if (idxNow >= 0) editList[idxNow] = panel.copy(enabled = checked)
                                },
                                enabled = panel.enabled.not() || editList.count { it.enabled } > 1,
                                modifier = Modifier.semantics {
                                    contentDescription = if (panel.enabled) "Disable ${panel.name}" else "Enable ${panel.name}"
                                },
                            )
                            IconButton(
                                onClick = { pendingDelete = panel },
                                enabled = editList.size > 1,
                                modifier = Modifier.semantics { contentDescription = "Delete ${panel.name}" },
                            ) {
                                Text("✕", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (index < editList.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newPanelName,
                        onValueChange = { newPanelName = it },
                        placeholder = { Text("New panel name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val trimmed = newPanelName.trim()
                            if (trimmed.isNotEmpty()) {
                                onAdd(trimmed)
                                newPanelName = ""
                            }
                        },
                    ) {
                        Text("+ Add")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            editList.forEachIndexed { index, panel ->
                                val newName = (names[panel.id] ?: panel.name).trim().ifBlank { panel.name }
                                if (newName != panel.name) onRename(panel.id, newName)
                            }
                            if (editList.map { it.id } != panels.map { it.id }) onReorder(editList.toList())
                            onDismiss()
                        },
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete '${toDelete.name}'?") },
            text = { Text("Its favorites will be removed. The apps themselves won't be uninstalled.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        editList.remove(toDelete)
                        names.remove(toDelete.id)
                        onDelete(toDelete.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

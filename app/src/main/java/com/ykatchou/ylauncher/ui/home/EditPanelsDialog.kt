package com.ykatchou.ylauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ykatchou.ylauncher.data.model.Panel

@Composable
fun EditPanelsDialog(
    panels: List<Panel>,
    onRename: (id: Long, name: String) -> Unit,
    onReorder: (List<Panel>) -> Unit,
    onDelete: (id: Long) -> Unit,
    onAdd: (name: String) -> Unit,
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

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(editList, key = { it.id }) { panel ->
                        val idx = editList.indexOf(panel)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val item = editList.removeAt(idx)
                                        editList.add(idx - 1, item)
                                    }
                                },
                                enabled = idx > 0,
                            ) {
                                Text("▲", style = MaterialTheme.typography.bodyLarge)
                            }
                            IconButton(
                                onClick = {
                                    if (idx < editList.size - 1) {
                                        val item = editList.removeAt(idx)
                                        editList.add(idx + 1, item)
                                    }
                                },
                                enabled = idx < editList.size - 1,
                            ) {
                                Text("▼", style = MaterialTheme.typography.bodyLarge)
                            }
                            OutlinedTextField(
                                value = names[panel.id] ?: panel.name,
                                onValueChange = { names[panel.id] = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { pendingDelete = panel },
                                enabled = editList.size > 1,
                            ) {
                                Text("✕", color = MaterialTheme.colorScheme.error)
                            }
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

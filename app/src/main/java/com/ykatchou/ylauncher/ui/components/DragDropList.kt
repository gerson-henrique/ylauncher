package com.ykatchou.ylauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives a drag-to-reorder interaction started from a dedicated handle inside a [LazyColumn]
 * item. Item order changes optimistically as the dragged item's midpoint crosses a neighbor's
 * midpoint; [onMove] is expected to mutate the backing list in place (e.g. removeAt/add on a
 * SnapshotStateList).
 *
 * [canAcceptDrop] marks items (e.g. folders) that act as merge targets instead of reorder
 * slots: while the dragged item hovers over one, reordering pauses and [hoveredDropTargetIndex]
 * reports it for highlighting; releasing there fires [onDropOnTarget] instead of a reorder.
 */
class DragDropListState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val canAcceptDrop: (index: Int) -> Boolean = { false },
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDropOnTarget: (from: Int, target: Int) -> Unit = { _, _ -> },
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    var hoveredDropTargetIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemDraggedDelta by mutableStateOf(0f)
    private var draggingItemInitialOffset by mutableStateOf(0)

    private val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    private var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private val previousItemOffset = Animatable(0f)

    fun onDragStart(index: Int) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingItemIndex = index
        draggingItemInitialOffset = item.offset
    }

    fun onDragInterrupted() {
        val index = draggingItemIndex
        val dropTarget = hoveredDropTargetIndex
        if (index != null && dropTarget != null) {
            onDropOnTarget(index, dropTarget)
        }
        if (index != null) {
            previousIndexOfDraggedItem = index
            val startOffset = draggingItemOffset
            scope.launch {
                previousItemOffset.snapTo(startOffset)
                previousItemOffset.animateTo(0f, tween(300))
                previousIndexOfDraggedItem = null
            }
        }
        draggingItemDraggedDelta = 0f
        draggingItemIndex = null
        draggingItemInitialOffset = 0
        hoveredDropTargetIndex = null
    }

    fun onDrag(deltaY: Float) {
        draggingItemDraggedDelta += deltaY

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val targetItem = listState.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) &&
                draggingItem.index != item.index
        }

        if (targetItem == null) {
            hoveredDropTargetIndex = null
            return
        }

        if (canAcceptDrop(targetItem.index) && !canAcceptDrop(draggingItem.index)) {
            hoveredDropTargetIndex = targetItem.index
        } else {
            hoveredDropTargetIndex = null
            onMove(draggingItem.index, targetItem.index)
            draggingItemIndex = targetItem.index
        }
    }

    fun offsetForItem(index: Int): IntOffset = when (index) {
        draggingItemIndex -> IntOffset(0, draggingItemOffset.toInt())
        previousIndexOfDraggedItem -> IntOffset(0, previousItemOffset.value.toInt())
        else -> IntOffset.Zero
    }
}

@Composable
fun rememberDragDropListState(
    listState: LazyListState,
    canAcceptDrop: (index: Int) -> Boolean = { false },
    onDropOnTarget: (from: Int, target: Int) -> Unit = { _, _ -> },
    onMove: (from: Int, to: Int) -> Unit,
): DragDropListState {
    val scope = rememberCoroutineScope()
    return remember(listState) { DragDropListState(listState, scope, canAcceptDrop, onMove, onDropOnTarget) }
}

/**
 * Attach to a small drag-handle icon inside an item; [index] is that item's current position.
 *
 * Keyed only on [dragDropListState] (not [index]): the dragged item's own index keeps changing
 * mid-gesture as it swaps past neighbors, and keying on it would cancel/restart the active
 * gesture-detector coroutine on every swap, stalling the drag until the finger lifts and
 * re-touches. [rememberUpdatedState] keeps [onDragStart] reading the latest index without
 * needing to restart the gesture.
 */
@Composable
fun Modifier.dragHandle(dragDropListState: DragDropListState, index: Int): Modifier {
    val currentIndex = rememberUpdatedState(index)
    return this.pointerInput(dragDropListState) {
        detectDragGestures(
            onDragStart = { dragDropListState.onDragStart(currentIndex.value) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropListState.onDrag(dragAmount.y)
            },
            onDragEnd = { dragDropListState.onDragInterrupted() },
            onDragCancel = { dragDropListState.onDragInterrupted() },
        )
    }
}

package io.rank5.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing
import kotlin.math.abs

/**
 * The one drag-to-reorder list: immediate drag (no long-press), thresholds from
 * MEASURED row heights, multi-position moves in a single gesture, spring
 * placement animation, haptic ticks, and per-row accessibility actions.
 */
@Composable
fun DraggableRankList(
    items: List<String>,
    onReorder: (List<String>) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onRankCross: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    // Read the latest list inside the drag loop without restarting pointerInput
    // (a restart mid-gesture would cancel the drag on every reorder).
    val latestItems by rememberUpdatedState(items)
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnRankCross by rememberUpdatedState(onRankCross)
    var draggingItem by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }

    fun moveByAction(from: Int, to: Int): Boolean {
        val current = latestItems
        if (from !in current.indices || to !in current.indices || from == to) return false
        val mutated = current.toMutableList()
        mutated.add(to, mutated.removeAt(from))
        latestOnReorder(mutated)
        return true
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        itemsIndexed(items, key = { _, item -> item }) { index, item ->
            val isDragging = draggingItem == item
            val cardShape = MaterialTheme.shapes.medium
            RankCard(
                rank = index + 1,
                label = item,
                trailing = if (enabled) {
                    { DragHandle() }
                } else {
                    null
                },
                modifier = Modifier
                    .then(if (isDragging) Modifier else Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            visibilityThreshold = IntOffset.VisibilityThreshold,
                        ),
                    ))
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged { rowHeights[item] = it.height }
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffset
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = Sizes.dragElevation.toPx()
                            shape = cardShape
                        }
                    }
                    .semantics {
                        stateDescription = "Ranked ${index + 1} of ${items.size}"
                        if (enabled) {
                            customActions = listOf(
                                CustomAccessibilityAction("Move up") {
                                    moveByAction(latestItems.indexOf(item), latestItems.indexOf(item) - 1)
                                },
                                CustomAccessibilityAction("Move down") {
                                    moveByAction(latestItems.indexOf(item), latestItems.indexOf(item) + 1)
                                },
                            )
                        }
                    }
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(item) {
                                val spacingPx = Spacing.sm.toPx()
                                detectDragGestures(
                                    onDragStart = {
                                        draggingItem = item
                                        dragOffset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestOnDragStart()
                                    },
                                    onDragEnd = {
                                        draggingItem = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingItem = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        var order = latestItems
                                        var idx = order.indexOf(item)
                                        var changed = false
                                        // Cross as many neighbors as the accumulated
                                        // offset covers — multi-position in one gesture.
                                        while (idx >= 0) {
                                            val down = dragOffset > 0f
                                            val neighbor = if (down) idx + 1 else idx - 1
                                            if (neighbor !in order.indices) break
                                            val step =
                                                (rowHeights[order[neighbor]] ?: break) + spacingPx
                                            // Strict crossing: after a swap the residual offset is
                                            // exactly (old - step); a non-strict check at the exact
                                            // half-step boundary would swap straight back and spin
                                            // this loop forever.
                                            if (abs(dragOffset) <= step / 2f) break
                                            val mutated = order.toMutableList()
                                            mutated.add(neighbor, mutated.removeAt(idx))
                                            order = mutated
                                            idx = neighbor
                                            dragOffset += if (down) -step else step
                                            changed = true
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove,
                                            )
                                            latestOnRankCross()
                                        }
                                        if (changed) latestOnReorder(order)
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** Two rounded lines — the drag affordance, drawn instead of an icon dependency. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(Sizes.dragHandle)) {
        val stroke = Sizes.dragHandleStroke.toPx()
        val inset = size.width * 0.15f
        listOf(size.height * 0.4f, size.height * 0.6f).forEach { y ->
            drawLine(
                color = color,
                start = Offset(inset, y),
                end = Offset(size.width - inset, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

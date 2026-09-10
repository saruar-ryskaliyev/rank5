package io.rank5.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.zIndex
import io.rank5.app.ui.theme.Motion
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
    // rawDragOffset is the finger's true travel; dragOffset is what we draw
    // (rubber-banded at the first/last rank).
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    // Option text is the natural key, but a deck authored outside this client
    // could repeat an option; LazyColumn would then crash on the duplicate key.
    val uniqueLabels = remember(items) { items.distinct().size == items.size }

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
        itemsIndexed(
            items,
            key = { index, item -> if (uniqueLabels) item else "$index:$item" },
        ) { index, item ->
            val isDragging = draggingItem == item
            val cardShape = MaterialTheme.shapes.medium
            // Snap while the finger drives the card; spring home on release so
            // the drop carries momentum instead of teleporting.
            val presentationOffset by animateFloatAsState(
                targetValue = if (isDragging) dragOffset else 0f,
                animationSpec = if (isDragging) {
                    snap()
                } else {
                    spring(dampingRatio = Motion.momentumDamping, stiffness = Motion.standardStiffness)
                },
                label = "drag-release-offset",
            )
            val presentationScale by animateFloatAsState(
                targetValue = if (isDragging) Motion.liftedScale else 1f,
                animationSpec = if (isDragging) {
                    snap()
                } else {
                    spring(dampingRatio = Motion.momentumDamping, stiffness = Motion.quickStiffness)
                },
                label = "drag-release-scale",
            )
            RankCard(
                rank = index + 1,
                label = item,
                trailing = if (enabled) {
                    { DragHandle() }
                } else {
                    null
                },
                modifier = Modifier
                    .then(
                        if (isDragging) Modifier
                        else Modifier.animateItem(placementSpec = Motion.placementSpring()),
                    )
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged { rowHeights[item] = it.height }
                    .graphicsLayer {
                        translationY = presentationOffset
                        scaleX = presentationScale
                        scaleY = presentationScale
                        if (isDragging || presentationScale > 1.001f) {
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
                                        rawDragOffset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestOnDragStart()
                                    },
                                    onDragEnd = {
                                        draggingItem = null
                                        dragOffset = 0f
                                        rawDragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingItem = null
                                        dragOffset = 0f
                                        rawDragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        rawDragOffset += amount.y
                                        var order = latestItems
                                        var idx = order.indexOf(item)
                                        var changed = false
                                        // Cross as many neighbors as the accumulated
                                        // offset covers — multi-position in one gesture.
                                        while (idx >= 0) {
                                            val down = rawDragOffset > 0f
                                            val neighbor = if (down) idx + 1 else idx - 1
                                            if (neighbor !in order.indices) break
                                            val step =
                                                (rowHeights[order[neighbor]] ?: break) + spacingPx
                                            // Strict crossing: after a swap the residual offset is
                                            // exactly (old - step); a non-strict check at the exact
                                            // half-step boundary would swap straight back and spin
                                            // this loop forever.
                                            if (abs(rawDragOffset) <= step / 2f) break
                                            val mutated = order.toMutableList()
                                            mutated.add(neighbor, mutated.removeAt(idx))
                                            order = mutated
                                            idx = neighbor
                                            rawDragOffset += if (down) -step else step
                                            changed = true
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove,
                                            )
                                            latestOnRankCross()
                                        }
                                        val atSoftBoundary = when {
                                            rawDragOffset < 0f -> idx == 0
                                            rawDragOffset > 0f -> idx == order.lastIndex
                                            else -> false
                                        }
                                        val dimension = (rowHeights[item] ?: size.height).toFloat()
                                        dragOffset = if (atSoftBoundary) {
                                            rubberBand(rawDragOffset, dimension)
                                        } else {
                                            rawDragOffset
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

/** Progressive resistance keeps the card responsive at the first and last rank. */
private fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
    if (dimension <= 0f) return overshoot
    return (overshoot * dimension * constant) /
        (dimension + constant * abs(overshoot))
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

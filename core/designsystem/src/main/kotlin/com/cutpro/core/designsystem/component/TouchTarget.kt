package com.cutpro.core.designsystem.component

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Modifier ensuring that interactive elements satisfy the accessible touch target requirement of at least 48dp x 48dp.
 * If the inner content is smaller (such as a 4dp trim handle or narrow scrubber), this expands the layout bounds
 * to at least [minWidth] x [minHeight] and centers the child inside the hitbox.
 */
fun Modifier.accessibleTouchTarget(
    minWidth: Dp = 48.dp,
    minHeight: Dp = 48.dp
): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val minWidthPx = minWidth.roundToPx()
    val minHeightPx = minHeight.roundToPx()

    val targetWidth = max(placeable.width, minWidthPx)
    val targetHeight = max(placeable.height, minHeightPx)

    layout(targetWidth, targetHeight) {
        val xOffset = (targetWidth - placeable.width) / 2
        val yOffset = (targetHeight - placeable.height) / 2
        placeable.placeRelative(xOffset, yOffset)
    }
}

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.ceil
import kotlin.math.sqrt

/** Packs measured widths into rows, wrapping before the next item would overflow. */
internal fun choiceRows(widths: List<Int>, available: Int, gap: Int): List<IntRange> {
    if (widths.isEmpty()) return emptyList()
    if (available <= 0) return widths.indices.map { it..it }
    val result = mutableListOf<IntRange>()
    var start = 0
    var used = 0
    for (i in widths.indices) {
        val width = widths[i].coerceAtMost(available)
        val candidate = used + if (i == start) width else gap + width
        if (i > start && candidate > available) {
            result += start..(i - 1)
            start = i
            used = width
        } else used = candidate
    }
    result += start..widths.lastIndex
    return result
}

/** The resting corner radius; [CapsuleCorner] grows it towards half the shortest side. */
private val SmallCornerRadius = 12.dp

private data class CapsuleCorner(val roundness: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val small = with(density) { SmallCornerRadius.toPx() }
        return small + (shapeSize.minDimension / 2 - small) * roundness.coerceIn(0f, 1f)
    }
}

internal fun parameterChoiceShape(startRoundness: Float, endRoundness: Float) =
    RoundedCornerShape(
        topStart = CapsuleCorner(startRoundness), bottomStart = CapsuleCorner(startRoundness),
        topEnd = CapsuleCorner(endRoundness), bottomEnd = CapsuleCorner(endRoundness),
    )

// A rectangle inset by r * (1 - 1/sqrt(2)) stays inside a corner of radius r. The corner radius is
// half the shortest side here, so the safe inset is half of that fraction of the side.
private val SafeInset = (1f - 1f / sqrt(2f)) / 2f

private fun safeInset(width: Int, height: Int) = ceil(minOf(width, height) * SafeInset).toInt()

/** Content padding before the rounded corner starts cutting into the label. */
private val ChoicePaddingHorizontal = 12.dp
private val ChoicePaddingVertical = 11.dp

internal fun Density.minimumChoiceWidth(
    contentWidth: Int,
    contentHeight: Int,
    minHeight: Int,
    reservedWidth: Int,
    minimumInteractiveWidth: Int,
): Int {
    var horizontal = ChoicePaddingHorizontal.roundToPx()
    var vertical = ChoicePaddingVertical.roundToPx()
    while (true) {
        val width = maxOf(minimumInteractiveWidth, contentWidth + reservedWidth + horizontal * 2)
        val inset = safeInset(width, maxOf(minHeight, contentHeight + vertical * 2))
        val h = maxOf(horizontal, inset)
        val v = maxOf(vertical, inset)
        if (h == horizontal && v == vertical) return width
        horizontal = h
        vertical = v
    }
}

internal fun Modifier.safeChoicePadding() = layout { measurable, constraints ->
    var horizontal = ChoicePaddingHorizontal.roundToPx()
    var vertical = ChoicePaddingVertical.roundToPx()
    if (constraints.hasBoundedWidth) {
        while (true) {
            val contentHeight = measurable.minIntrinsicHeight((constraints.maxWidth - horizontal * 2).coerceAtLeast(0))
            val inset = safeInset(constraints.maxWidth,
                maxOf(constraints.minHeight, contentHeight + vertical * 2))
            val h = maxOf(horizontal, inset)
            val v = maxOf(vertical, inset)
            if (h == horizontal && v == vertical) break
            horizontal = h; vertical = v
        }
    }
    val child = measurable.measure(constraints.offset(-horizontal * 2, -vertical * 2))
    layout(constraints.constrainWidth(child.width + horizontal * 2),
        constraints.constrainHeight(child.height + vertical * 2)) {
        child.placeRelative(horizontal, vertical)
    }
}

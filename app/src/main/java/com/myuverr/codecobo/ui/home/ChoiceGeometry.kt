// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.ceil

/** Packs options in order using Row's rounded pixel dimensions. */
internal fun choiceRows(widths: List<Int>, available: Int, gap: Int): List<IntRange> {
    if (widths.isEmpty()) return emptyList()
    if (available <= 0) return widths.indices.map { it..it }
    val result = mutableListOf<IntRange>()
    var start = 0
    var used = 0L
    for (i in widths.indices) {
        val width = widths[i].coerceAtMost(available).toLong()
        val candidate = used + if (i == start) width else gap + width
        if (i > start && candidate > available) {
            result += start until i
            start = i
            used = width
        } else used = candidate
    }
    result += start..widths.lastIndex
    return result
}

private data class CapsuleCorner(val roundness: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val small = with(density) { 12.dp.toPx() }
        return small + (shapeSize.minDimension / 2 - small) * roundness.coerceIn(0f, 1f)
    }
}

internal fun parameterChoiceShape(startRoundness: Float, endRoundness: Float) =
    RoundedCornerShape(
        topStart = CapsuleCorner(startRoundness), bottomStart = CapsuleCorner(startRoundness),
        topEnd = CapsuleCorner(endRoundness), bottomEnd = CapsuleCorner(endRoundness),
    )

// A rectangle inset by r*(1-1/sqrt(2)) is wholly inside a rounded corner of radius r.
// Use resting bounds so corner animation cannot reflow text.
private const val SafeInset = 0.14644661f // radius is half minDimension
private fun safeInset(width: Int, height: Long) = ceil(minOf(width.toLong(), height) * SafeInset).toInt()

internal fun Density.minimumChoiceWidth(contentWidth: Int, contentHeight: Int, minHeight: Int): Int {
    var horizontal = 12.dp.roundToPx()
    var vertical = 11.dp.roundToPx()
    while (true) {
        val width = maxOf(48.dp.roundToPx(), contentWidth + 18.dp.roundToPx() + horizontal * 2)
        val inset = safeInset(width, maxOf(minHeight.toLong(), contentHeight.toLong() + vertical * 2))
        val h = maxOf(horizontal, inset)
        val v = maxOf(vertical, inset)
        if (h == horizontal && v == vertical) return width
        horizontal = h; vertical = v
    }
}

internal fun Modifier.safeChoicePadding() = layout { measurable, constraints ->
    var horizontal = 12.dp.roundToPx()
    var vertical = 11.dp.roundToPx()
    if (constraints.hasBoundedWidth) {
        while (true) {
            val contentHeight = measurable.minIntrinsicHeight((constraints.maxWidth - horizontal * 2).coerceAtLeast(0))
            val inset = safeInset(constraints.maxWidth,
                maxOf(constraints.minHeight.toLong(), contentHeight.toLong() + vertical * 2))
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

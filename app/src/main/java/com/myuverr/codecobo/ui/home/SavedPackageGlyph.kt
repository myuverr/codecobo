// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.vectorResource
import com.myuverr.codecobo.R

/** Artboard the Icon Monks glyphs are drawn on; the guides below use the same units. */
private const val Artboard = 256f

/** Brush head radius in artboard units, wide enough to ink the glyph's stroke as it passes. */
private const val BrushRadius = 15f

/** The receipt inks the box, then the check, then settles the ink. */
internal const val BodyDrawMillis = 400f
internal const val CheckDrawMillis = 220f
internal const val TintMillis = 300f
internal const val RevealMillis = BodyDrawMillis + CheckDrawMillis + TintMillis

/** How much of the receipt is inked, and how far the colour has settled, [millis] into the reveal. */
internal data class ReceiptReveal(val box: Float, val check: Float, val cooling: Float)

internal fun receiptReveal(millis: Float): ReceiptReveal = ReceiptReveal(
    box = (millis / BodyDrawMillis).coerceIn(0f, 1f),
    check = ((millis - BodyDrawMillis) / CheckDrawMillis).coerceIn(0f, 1f),
    cooling = ((millis - BodyDrawMillis - CheckDrawMillis) / TintMillis).coerceIn(0f, 1f),
)

/**
 * Centre lines of the box and its check in the 256-unit Icon Monks artboard. The brush is intersected
 * with the filled contour, so walking these lines inks the glyph instead of stamping it.
 */
private val BoxGuide = listOf(
    listOf(Offset(128f, 32f), Offset(40f, 80f), Offset(128f, 128f), Offset(216f, 80f), Offset(128f, 32f)),
    listOf(Offset(40f, 80f), Offset(40f, 176f), Offset(128f, 224f), Offset(141f, 217f)),
    listOf(Offset(216f, 80f), Offset(216f, 143f)),
    listOf(Offset(128f, 128f), Offset(128f, 224f)),
    listOf(Offset(172f, 56f), Offset(84f, 104f), Offset(84f, 144f)),
)

private val CheckGuide = listOf(listOf(Offset(162f, 190f), Offset(180f, 208f), Offset(214f, 172f)))

/**
 * The saved receipt: the box is inked along its own lines, the check follows, and the ink settles from
 * the button's active colour to its resting one. Both contours come from one Icon Monks glyph, split
 * into two drawables so the check can start on its own.
 *
 * [walk] is the reveal position from 0 to 1, owned by the caller so a receipt already on screen stays still.
 */
@Composable
internal fun SavedPackage(walk: State<Float>, modifier: Modifier) {
    val box = rememberGlyph(R.drawable.ic_action_package, BoxGuide)
    val check = rememberGlyph(R.drawable.ic_action_package_check, CheckGuide)
    val active = MaterialTheme.colorScheme.primary
    val resting = LocalContentColor.current
    Canvas(modifier) {
        val reveal = receiptReveal(walk.value * RevealMillis)
        val ink = lerp(active, resting, reveal.cooling)
        val unit = size.minDimension / Artboard
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            ink(box, reveal.box, ink)
            ink(check, reveal.check, ink)
        }
    }
}

/**
 * Inks [glyph] up to [progress] of its brush walk: the covered part of the contour while the brush is
 * still walking, the whole contour once it is done.
 */
private fun DrawScope.ink(glyph: InkableGlyph, progress: Float, ink: Color) {
    when {
        progress >= 1f -> drawPath(glyph.contour, ink)
        progress > 0f -> drawPath(glyph.revealed(progress), ink)
    }
}

/** One glyph, the brush that inks it, and the scratch the two are intersected in. */
private class InkableGlyph(val contour: Path, private val brush: GuideBrush) {
    private val uncovered = Path()

    /** What the brush has uncovered after [fraction] of its walk. */
    fun revealed(fraction: Float): Path {
        uncovered.reset()
        uncovered.op(brush.region(fraction), contour, PathOperation.Intersect)
        return uncovered
    }
}

@Composable
private fun rememberGlyph(icon: Int, guide: List<List<Offset>>): InkableGlyph {
    val image = ImageVector.vectorResource(icon)
    return remember(image) {
        InkableGlyph(Path().apply { addContours(image.root) }, GuideBrush(guide))
    }
}

private fun Path.addContours(node: VectorNode) {
    when (node) {
        is VectorPath -> addPath(PathParser().addPathNodes(node.pathData).toPath())
        is VectorGroup -> node.forEach { addContours(it) }
    }
}

/** A round brush that walks a glyph's centre lines and reports the area it has covered. */
private class GuideBrush(private val lines: List<List<Offset>>) {
    private val lengths = lines.map { line -> line.zipWithNext { from, to -> (to - from).getDistance() }.sum() }
    private val total = lengths.sum()
    private val area = Path()
    private val capsule = Path()
    private val merged = Path()

    /** The area covered after [fraction] of the walk, in artboard units. */
    fun region(fraction: Float): Path {
        area.reset()
        var budget = total * fraction.coerceIn(0f, 1f)
        for ((line, length) in lines.zip(lengths)) {
            if (budget <= 0f) break
            val walked = minOf(budget, length)
            var travelled = 0f
            for (index in 1 until line.size) {
                val from = line[index - 1]
                val to = line[index]
                val step = (to - from).getDistance()
                val part = if (step == 0f) 1f else ((walked - travelled) / step).coerceIn(0f, 1f)
                if (part > 0f) cover(from, from + (to - from) * part)
                if (travelled + step >= walked) break
                travelled += step
            }
            budget -= walked
        }
        return area
    }

    /** Adds one round-capped brush stroke; unioned, so overlapping dabs read as one shape. */
    private fun cover(from: Offset, to: Offset) {
        capsule.reset()
        capsule.addOval(Rect(from, BrushRadius))
        capsule.addOval(Rect(to, BrushRadius))
        val along = to - from
        val length = along.getDistance()
        if (length > 0f) {
            val across = Offset(-along.y, along.x) / length * BrushRadius
            capsule.moveTo((from + across).x, (from + across).y)
            capsule.lineTo((to + across).x, (to + across).y)
            capsule.lineTo((to - across).x, (to - across).y)
            capsule.lineTo((from - across).x, (from - across).y)
            capsule.close()
        }
        merged.reset()
        merged.op(area, capsule, PathOperation.Union)
        area.reset()
        area.addPath(merged)
    }
}

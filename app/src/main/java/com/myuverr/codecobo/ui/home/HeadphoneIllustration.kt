// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors

/** Drawing space of the paths below; callers keep this ratio to avoid stretching. */
private const val DrawingWidth = 180f
private const val DrawingHeight = 152f

/** Vertical bounds of the drawn ink, which sits above the centre of the drawing space. */
private const val InkTop = 12f
private const val InkBottom = 127f

internal const val HeadphoneArtworkAspectRatio = DrawingWidth / DrawingHeight

/** Headband outline in drawing space, built once and only read while drawing. */
private val bandOutline = Path().apply {
    moveTo(35f, 79f)
    lineTo(35f, 64f)
    cubicTo(35f, 31f, 58f, 12f, 90f, 12f)
    cubicTo(122f, 12f, 145f, 31f, 145f, 64f)
    lineTo(145f, 79f)
    lineTo(135f, 79f)
    lineTo(135f, 64f)
    cubicTo(135f, 37f, 117f, 22f, 90f, 22f)
    cubicTo(63f, 22f, 45f, 37f, 45f, 64f)
    lineTo(45f, 79f)
    close()
}

/**
 * @param centerInk drops the ink to the drawing centre, for containers that centre the artwork box.
 */
@Composable
internal fun HeadphoneIllustration(modifier: Modifier = Modifier, centerInk: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val accent = LocalWorkbenchColors.current.accent
    Canvas(modifier) {
        withTransform({
            scale(size.width / DrawingWidth, size.height / DrawingHeight, Offset.Zero)
            if (centerInk) translate(0f, DrawingHeight / 2f - (InkTop + InkBottom) / 2f)
        }) {
            drawPath(bandOutline, colors.primary)
            for (x in listOf(25f, 119f)) {
                val topLeft = Offset(x, 65f)
                val cup = Size(36f, 62f)
                drawRoundRect(accent, topLeft, cup, CornerRadius(17f))
                drawRoundRect(colors.primary, topLeft, cup, CornerRadius(17f), style = Stroke(3.5f))
            }
            for (x in listOf(47f, 120f)) {
                drawRoundRect(colors.primary, Offset(x, 73f), Size(13f, 46f), CornerRadius(6.5f))
            }
            for (x in listOf(33f, 147f)) {
                drawLine(colors.surface.copy(alpha = .7f), Offset(x, 83f), Offset(x, 105f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }
    }
}

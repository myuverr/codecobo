// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

@Composable
internal fun HeadphoneIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = LocalWorkbenchColors.current.accent
    val band = remember {
        Path().apply {
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
    }
    Canvas(modifier) {
        withTransform({ scale(size.width / 180f, size.height / 152f, Offset.Zero) }) {
            drawPath(band, colors.primary)
            for (x in listOf(25f, 119f)) {
                drawRoundRect(accent, Offset(x, 65f), Size(36f, 62f), CornerRadius(17f))
                drawRoundRect(colors.primary, Offset(x, 65f), Size(36f, 62f), CornerRadius(17f), style = Stroke(3.5f))
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

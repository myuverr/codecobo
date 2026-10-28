// SPDX-License-Identifier: MPL-2.0 AND ISC AND MIT

package com.myuverr.codecobo.ui.home

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.core.graphics.PathParser
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** Original Lucide package-check geometry; finite contour reveal is project authored. */
@Composable
internal fun SavedPackageGlyph(bodyProgress: Float, checkProgress: Float, tint: Color, modifier: Modifier) {
    val body = remember {
        Path().apply {
            for (data in listOf(
                "M21 11.127V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.729l7 4a2 2 0 0 0 2 .001l1.32-.753",
                "M3.29 7 12 12l8.71-5", "M12 22V12", "m7.5 4.27 8.997 5.148",
            )) addPath(PathParser.createPathFromPathData(data)!!)
        }
    }
    val check = remember { PathParser.createPathFromPathData("m16 17 2 2 4-4")!! }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
    }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }
    Canvas(modifier) {
        paint.color = tint.toArgb()
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            try {
                native.scale(size.width / 24f, size.height / 24f)
                for ((path, progress) in listOf(body to bodyProgress, check to checkProgress)) {
                    if (progress >= 1f) native.drawPath(path, paint)
                    else if (progress > 0f) {
                        measure.setPath(path, false)
                        var length = 0f
                        do { length += measure.length } while (measure.nextContour())
                        measure.setPath(path, false)
                        var remaining = length * progress
                        segment.reset()
                        do {
                            if (remaining <= 0f) break
                            measure.getSegment(0f, minOf(remaining, measure.length), segment, true)
                            remaining -= measure.length
                        } while (measure.nextContour())
                        native.drawPath(segment, paint)
                    }
                }
            } finally { native.restore() }
        }
    }
}

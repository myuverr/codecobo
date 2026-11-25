// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.coroutineContext

/** Scales the glyph once when the tab becomes selected. */
@Composable
internal fun Modifier.navigationPulse(selected: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    val motion = MaterialTheme.motionScheme
    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        val pulse = selected && !wasSelected
        wasSelected = selected
        // Nothing may stay scaled once the system turns animations off.
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) scale.snapTo(1f)
        else if (pulse) {
            // A quick squeeze acknowledges the tap; the return rides the theme's spring.
            scale.animateTo(1.12f, tween(70))
            scale.animateTo(1f, motion.fastSpatialSpec())
        } else if (scale.value != 1f) scale.animateTo(1f, motion.fastSpatialSpec())
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

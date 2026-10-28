// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
internal fun NavigationSelectionIndicator(slot: Rect?, viewport: IntSize) {
    if (slot == null || viewport.width <= 0 || slot.width <= 0 || slot.height <= 0) return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // Layout changes must not replay selection motion.
    key(viewport, density, direction) {
        val x by animateFloatAsState(slot.left, MotionScheme.expressive().fastSpatialSpec(), label = "navigation-capsule-position")
        val color by animateColorAsState(MaterialTheme.colorScheme.surfaceContainerHigh,
            MotionScheme.expressive().fastEffectsSpec(), label = "navigation-capsule-color")
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
            Box(Modifier.absoluteOffset {
                // Slot bounds are physical coordinates.
                IntOffset(x.coerceIn(0f, (viewport.width - slot.width).coerceAtLeast(0f)).roundToInt(), slot.top.roundToInt())
            }.size(with(density) { slot.width.toDp() }, with(density) { slot.height.toDp() })
                .background(color, RoundedCornerShape(50)).testTag("navigation-indicator"))
        }
    }
}

@Composable
internal fun NavigationPulsingGlyph(page: MainNavigation.Page, selected: Boolean, content: @Composable (Modifier) -> Unit) {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        var pulse = previous != selected && selected
        previous = selected
        val duration = coroutineContext[MotionDurationScale]
        // Disabling motion must also cancel the return phase.
        snapshotFlow { duration?.scaleFactor ?: 1f }.collectLatest { factor ->
            val startPulse = pulse
            pulse = false
            if (factor == 0f) scale.snapTo(1f)
            else if (startPulse) {
                scale.animateTo(1.12f, tween(70))
                scale.animateTo(1f, MotionScheme.expressive().fastSpatialSpec())
            } else if (scale.value != 1f) scale.animateTo(1f, MotionScheme.expressive().fastSpatialSpec())
        }
    }
    content(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .testTag("navigation-glyph-" + page.name))
}

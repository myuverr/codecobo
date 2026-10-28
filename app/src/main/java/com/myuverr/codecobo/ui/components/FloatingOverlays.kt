// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shares overlay bounds between page and navigation compositions. */
class FloatingOverlayState {
    internal var navigationHeightPixels by mutableIntStateOf(0)
        private set
    internal fun navigationMeasured(height: Int) { navigationHeightPixels = height.coerceAtLeast(0) }
}

internal val LocalFloatingNavigationClearance = staticCompositionLocalOf { 0.dp }

/** Limits focus scrolling to the viewport not covered by overlays. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FloatingScrollContent(clearance: Dp, content: @Composable () -> Unit) {
    val original = LocalBringIntoViewSpec.current
    val pixels = with(LocalDensity.current) { clearance.toPx() }
    val adjusted = remember(original, pixels) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                original.calculateScrollDistance(offset, size, (containerSize - pixels).coerceAtLeast(0f))
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides adjusted, content = content)
}

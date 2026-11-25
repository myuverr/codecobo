// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.core.Spring
import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class SlideSpringTest {
    /** The strip reads the theme's stiffness by casting its spec, which only works while it is one. */
    @Test
    fun bothSchemesExposeAReadableSpatialSpring() {
        for (motion in listOf(MotionScheme.standard(), MotionScheme.expressive())) {
            val spec = motion.slideSpec
            assertEquals(Spring.DampingRatioNoBouncy, spec.dampingRatio, 0f)
            assertEquals(0.001f, spec.visibilityThreshold ?: 0f, 0f)
        }
    }
}

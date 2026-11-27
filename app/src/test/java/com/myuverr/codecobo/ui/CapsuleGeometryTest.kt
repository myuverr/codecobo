// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The capsule and the indicator inside it are both full pills, so their corner radii differ by
 * exactly the air between them. These pin the air, which is what keeps them concentric when the
 * touch target or the label changes.
 */
class CapsuleGeometryTest {
    /** labelMedium's line height, below the glyph, so the indicator is glyph-driven. */
    private val labelLine = 16.dp

    @Test
    fun theCapsuleReservesOneGapAroundTheIndicatorUntilTheTargetOutgrowsIt() {
        // 24dp glyph + 8dp padding + 8dp gap, mirrored. Below the indicator the target stops mattering,
        // and 56dp is where the touch target itself starts to set the height.
        for (target in listOf(32, 48, 56)) {
            assertEquals("touch target ${target}dp", 56.dp, capsuleGeometry(labelLine, target.dp).height)
        }
    }

    @Test
    fun aTallerTargetAddsCapsuleHeightRatherThanPadding() {
        val tall = capsuleGeometry(labelLine, 72.dp)
        assertEquals(72.dp, tall.height)
        assertEquals(0.dp, tall.verticalInset)
    }

    @Test
    fun aScaledLabelGrowsTheIndicatorAndKeepsTheSameGap() {
        // The label outgrows the glyph, so the indicator and the capsule grow with it, and the 8dp
        // air around the indicator is unchanged.
        val scaled = capsuleGeometry(labelLine = 40.dp, touchTarget = 48.dp)
        assertEquals(72.dp, scaled.height)
        assertEquals(8.dp, scaled.verticalInset)
    }
}

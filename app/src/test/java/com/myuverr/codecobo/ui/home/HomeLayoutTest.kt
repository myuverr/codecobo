// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.WindowTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun anExpandedWindowKeepsOneColumnWhenThePaneIsTooNarrow() {
        assertFalse(showsWideHome(WindowTier.EXPANDED, 839.dp, 900.dp))
        assertTrue(showsWideHome(WindowTier.EXPANDED, 840.dp, 900.dp))
    }

    @Test
    fun anExpandedWindowKeepsOneColumnWhenThePaneIsTooShort() {
        assertFalse(showsWideHome(WindowTier.EXPANDED, 1000.dp, 479.dp))
        assertTrue(showsWideHome(WindowTier.EXPANDED, 1000.dp, 480.dp))
    }

    @Test
    fun aWidePaneAloneDoesNotEarnTwoColumns() {
        assertFalse(showsWideHome(WindowTier.MEDIUM, 1180.dp, 900.dp))
        assertFalse(showsWideHome(WindowTier.COMPACT, 1180.dp, 900.dp))
    }
}

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowTierTest {
    @Test
    fun mediumWidthStartsAtTheAndroidXBreakpoint() {
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(599f, 800f))
        assertEquals(WindowTier.MEDIUM, WindowTier.forWindow(600f, 800f))
    }

    @Test
    fun expandedWidthStartsAtTheAndroidXBreakpoint() {
        assertEquals(WindowTier.MEDIUM, WindowTier.forWindow(839f, 800f))
        assertEquals(WindowTier.EXPANDED, WindowTier.forWindow(840f, 800f))
    }

    @Test
    fun mediumHeightStartsAtTheAndroidXBreakpoint() {
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(1000f, 479f))
        assertEquals(WindowTier.EXPANDED, WindowTier.forWindow(1000f, 480f))
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(600f, 479f))
        assertEquals(WindowTier.MEDIUM, WindowTier.forWindow(600f, 480f))
    }

    @Test
    fun largeAndExtraLargeWidthsStillUseTheExpandedTier() {
        assertEquals(WindowTier.EXPANDED, WindowTier.forWindow(1200f, 900f))
        assertEquals(WindowTier.EXPANDED, WindowTier.forWindow(1600f, 900f))
    }

    @Test
    fun aWideButShortWindowStaysCompact() {
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(1400f, 300f))
    }

    @Test
    fun degenerateWindowSizesFallBackToCompact() {
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(Float.NaN, 800f))
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(1000f, Float.NaN))
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(Float.POSITIVE_INFINITY, 800f))
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(-1f, 800f))
        assertEquals(WindowTier.COMPACT, WindowTier.forWindow(1000f, -1f))
    }
}

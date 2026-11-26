// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceGeometryTest {
    @Test
    fun emptyChoicesHaveNoRows() {
        assertEquals(emptyList<IntRange>(), choiceRows(emptyList(), available = 200, gap = 8))
    }

    @Test
    fun choicesThatFitStayInOrderOnOneRow() {
        assertEquals(listOf(0..2), choiceRows(listOf(40, 50, 60), available = 200, gap = 8))
    }

    @Test
    fun wrappingKeepsWholeChoices() {
        // 40 + 8 + 50 = 98 fits; adding 8 + 60 would exceed 150.
        assertEquals(listOf(0..1, 2..2), choiceRows(listOf(40, 50, 60), available = 150, gap = 8))
    }

    @Test
    fun zeroAvailableWidthPlacesEveryChoiceAlone() {
        assertEquals(listOf(0..0, 1..1), choiceRows(listOf(40, 50), available = 0, gap = 8))
    }

    @Test
    fun anOversizedChoiceTakesItsOwnRow() {
        assertEquals(listOf(0..0, 1..1), choiceRows(listOf(300, 40), available = 200, gap = 8))
    }
}

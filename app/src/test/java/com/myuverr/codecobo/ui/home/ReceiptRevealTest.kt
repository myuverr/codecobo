// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The receipt is inked box first, then check, then the colour settles; these pin that order. */
class ReceiptRevealTest {
    @Test
    fun theBoxIsInkedBeforeTheCheckStarts() {
        val halfway = receiptReveal(BodyDrawMillis / 2)
        assertEquals(0.5f, halfway.box, 0.001f)
        assertEquals(0f, halfway.check, 0f)
        assertEquals(0f, halfway.cooling, 0f)
    }

    @Test
    fun theCheckStartsOnlyWhenTheBoxIsDone() {
        val atBoxEnd = receiptReveal(BodyDrawMillis)
        assertEquals(1f, atBoxEnd.box, 0f)
        assertEquals(0f, atBoxEnd.check, 0f)
    }

    @Test
    fun theInkCoolsOnlyAfterTheCheckIsDone() {
        val atCheckEnd = receiptReveal(BodyDrawMillis + CheckDrawMillis)
        assertEquals(1f, atCheckEnd.check, 0f)
        assertEquals(0f, atCheckEnd.cooling, 0f)
        assertEquals(1f, receiptReveal(RevealMillis).cooling, 0f)
    }

    @Test
    fun theRevealNeverRunsPastItsEnds() {
        assertEquals(ReceiptReveal(0f, 0f, 0f), receiptReveal(-50f))
        assertEquals(ReceiptReveal(1f, 1f, 1f), receiptReveal(RevealMillis * 2))
    }
}

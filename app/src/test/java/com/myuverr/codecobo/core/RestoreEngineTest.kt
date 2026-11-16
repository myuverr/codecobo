// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreEngineTest {
    private val device = FakeDevice()
    private var current = true

    private fun restore(pause: ApplyEngine.Pause = ApplyEngine.Pause {}) =
        RestoreEngine(pause).restore(aac48Request, device) { current }

    @Test
    fun waitsForReadinessThenRestoresAndVerifies() {
        device.actual = null
        var waits = 0

        val result = restore {
            device.actual = if (++waits == 1) aac44State else aac48State
        }

        assertEquals(RestoreEngine.Status.VERIFIED, result.status())
        assertEquals(aac48State, result.actual())
        assertEquals(listOf(aac48Request), device.writes)
    }

    @Test
    fun matchingOutputDoesNotNeedAnotherWrite() {
        device.actual = aac48State

        assertEquals(RestoreEngine.Status.MATCHED, restore().status())
        assertTrue(device.writes.isEmpty())
    }

    @Test
    fun missingAssociationDoesNotAuthorizeBackgroundWrites() {
        device.isAssociated = false

        assertEquals(RestoreEngine.Status.FAILED, restore().status())
        assertTrue(device.writes.isEmpty())
    }

    @Test
    fun unsupportedSavedCombinationIsNotApplied() {
        device.capabilities = listOf(CodecCapability(1, "AAC", 1, 1, 2))

        assertEquals(RestoreEngine.Status.FAILED, restore().status())
        assertTrue(device.writes.isEmpty())
    }

    @Test
    fun readinessWaitingIsBounded() {
        device.actual = null
        var waits = 0

        val result = restore {
            assertTrue("Restore must stop waiting", ++waits <= 20)
        }

        assertEquals(RestoreEngine.Status.FAILED, result.status())
        assertTrue(device.writes.isEmpty())
    }

    @Test
    fun expiredRestoreStopsWaitingWithoutWriting() {
        device.actual = null

        val result = restore { current = false }

        assertEquals(RestoreEngine.Status.CANCELLED, result.status())
        assertTrue(device.writes.isEmpty())
    }
}

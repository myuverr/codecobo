// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSubmissionTest {
    private val device = FakeDevice()
    private val gate = OperationGate()
    private val token = gate.issue()

    private fun submit(
        intent: SubmissionIntent,
        pause: ApplyEngine.Pause = ApplyEngine.Pause {},
    ) = ManualSubmission(pause).execute(device, aac48Request, intent, gate, token)

    @Test
    fun saveVerifiesActualOutputWithoutWritingBluetooth() {
        device.actual = aac48State

        val result = submit(SubmissionIntent.SAVE)

        assertEquals(ApplyEngine.Status.VERIFIED, result.status())
        assertEquals(aac48State, result.actual())
        assertTrue(device.writes.isEmpty())
    }

    @Test
    fun saveDoesNotApplyWhenActualOutputHasChanged() {
        val result = submit(SubmissionIntent.SAVE)

        assertEquals(ApplyEngine.Status.DIFFERENT, result.status())
        assertTrue(device.writes.isEmpty())
    }

    @Test
    fun applyWaitsForMatchingReadback() {
        var waits = 0
        val result = submit(SubmissionIntent.APPLY) {
            if (++waits == 2) device.actual = aac48State
        }

        assertEquals(ApplyEngine.Status.VERIFIED, result.status())
        assertEquals(aac48State, result.actual())
        assertEquals(listOf(aac48Request), device.writes)
    }

    @Test
    fun mismatchedReadbackDoesNotClaimSuccessOrRewrite() {
        val result = submit(SubmissionIntent.APPLY)

        assertEquals(ApplyEngine.Status.DIFFERENT, result.status())
        assertEquals(aac44State, result.actual())
        assertEquals(listOf(aac48Request), device.writes)
    }

    @Test
    fun missingReadbackIsNotVerification() {
        device.actual = null

        val result = submit(SubmissionIntent.APPLY)

        assertEquals(ApplyEngine.Status.UNVERIFIED, result.status())
        assertEquals(listOf(aac48Request), device.writes)
    }

    @Test
    fun expiredOperationDoesNotWrite() {
        gate.invalidate()

        assertEquals(ApplyEngine.Status.CANCELLED, submit(SubmissionIntent.APPLY).status())
        assertTrue(device.writes.isEmpty())
    }

    @Test
    fun invalidationDuringReadbackStopsTheOperation() {
        val result = submit(SubmissionIntent.APPLY) { gate.invalidate() }

        assertEquals(ApplyEngine.Status.CANCELLED, result.status())
        assertEquals(listOf(aac48Request), device.writes)
    }
}

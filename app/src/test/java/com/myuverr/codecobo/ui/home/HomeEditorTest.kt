// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.aac44State
import com.myuverr.codecobo.core.aac48Request
import com.myuverr.codecobo.core.aacCapabilities
import com.myuverr.codecobo.platform.BluetoothRepository.DeviceInfo
import com.myuverr.codecobo.platform.BluetoothRepository.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeEditorTest {
    private val address = "A1:B2:C3:D4:E5:F6"
    private val draft = HomeEditor().reconcile(screen()).selectRate(2, aacCapabilities)

    @Test
    fun refreshKeepsAValidDraftInsteadOfReplacingItWithActualOutput() {
        val refreshed = draft.reconcile(screen())

        assertEquals(aac48Request, refreshed.request(aacCapabilities))
    }

    @Test
    fun changingDevicesStartsFromTheNewDevicesActualOutput() {
        val nextAddress = "A1:B2:C3:D4:E5:F7"

        val switched = draft.reconcile(screen(address = nextAddress))

        assertEquals(nextAddress, switched.deviceAddress)
        assertEquals(CodecRequest(1, 1, 1, 2, 0), switched.request(aacCapabilities))
    }

    @Test
    fun invalidatingTheEditorReplacesItsOldDraftWithActualOutput() {
        val invalidated = draft.reconcile(screen(revision = 2))

        assertEquals(CodecRequest(1, 1, 1, 2, 0), invalidated.request(aacCapabilities))
    }

    @Test
    fun clearingTheSelectedDeviceRemovesTheSubmittableDraft() {
        val cleared = draft.reconcile(screen(address = null))

        assertNull(cleared.deviceAddress)
        assertNull(cleared.request(aacCapabilities))
    }

    private fun screen(address: String? = this.address, revision: Long = 1) = ScreenState(
        address?.let { listOf(DeviceInfo(it, "Headphones", true)) }.orEmpty(),
        address,
        address?.let { Snapshot(aac44State, "AAC", aacCapabilities) },
        false, true, ScreenState.AccessMode.SYSTEM, false, 0,
        ScreenState.OperationFailure.NONE, "", revision, false, null,
    )
}

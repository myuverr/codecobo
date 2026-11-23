// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.CodecCapability
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.CodecState
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

    @Test
    fun switchingCodecsAndBackKeepsTheSelectedValuesBeforeUsingTheFirstOption() {
        val v5 = CodecCapability(8, "LHDC V5", 2 or 8 or 32, 1 or 2, 1 or 2)
        val v3 = CodecCapability(7, "LHDC V3/V4", 2 or 8, 1 or 2, 1 or 2)
        val snapshot = Snapshot(CodecState(8, 8, 2, 2, -1), "LHDC V5", listOf(v5, v3))
        val initial = HomeState().reconcile(screen(snapshot = snapshot), null, false)
        assertEquals(CodecRequest(8, 8, 2, 2, 0), initial.editor.request(initial.capabilities))
        val draft = initial.editParameters { it.selectRate(32, initial.capabilities) }
            .editParameters { it.selectBits(1, initial.capabilities) }

        val switched = draft.editParameters { it.selectCodec(7, draft.capabilities) }
        assertEquals(CodecRequest(7, 2, 1, 2, 0), switched.editor.request(switched.capabilities))

        val returned = switched.editParameters { it.selectCodec(8, switched.capabilities) }
        assertEquals(CodecRequest(8, 32, 1, 2, 0), returned.editor.request(returned.capabilities))
        assertEquals(8, returned.actual?.sampleRate())
        assertEquals(2, returned.actual?.bitsPerSample())
    }

    @Test
    fun temporaryBitDepthFallbackDoesNotOverwriteTheDeviceSeedOrManualChoice() {
        val both = CodecCapability(8, "LHDC V5", 2, 1 or 2, 2)
        val only16 = CodecCapability(7, "LHDC V3/V4", 2, 1, 2)
        val only24 = CodecCapability(4, "LDAC", 2, 2, 2)
        val capabilities = listOf(both, only16, only24)
        val snapshot = Snapshot(CodecState(8, 2, 2, 2, -1), "LHDC V5", capabilities)
        val initial = HomeState().reconcile(screen(snapshot = snapshot), null, false)
        fun HomeState.switchTo(type: Int) = editParameters { it.selectCodec(type, capabilities) }
        fun HomeState.targetBits() = editor.request(capabilities)?.bitsPerSample()

        val fallback16 = initial.switchTo(7)
        assertEquals(1, fallback16.targetBits())
        val returned = fallback16.switchTo(8)
        assertEquals(2, returned.targetBits())

        val manual = returned.editParameters { it.selectBits(1, capabilities) }
        val fallback24 = manual.switchTo(4)
        assertEquals(2, fallback24.targetBits())
        assertEquals(1, fallback24.switchTo(8).targetBits())
    }

    @Test
    fun changedOptionsKeepValidDraftValuesAndReplaceInvalidOnes() {
        val actual = CodecState(4, 2, 2, 2, 1002)
        val initial = Snapshot(actual, "LDAC", listOf(CodecCapability(4, "LDAC", 2 or 8, 1 or 2, 1 or 2)))
        val editor = HomeEditor().reconcile(screen(snapshot = initial))
            .selectRate(8, initial.capabilities())
            .selectBits(1, initial.capabilities())
        val updated = Snapshot(actual, "LDAC", listOf(CodecCapability(4, "LDAC", 2 or 8, 2, 1)))

        val reconciled = editor.reconcile(screen(snapshot = updated))

        assertEquals(CodecRequest(4, 8, 2, 1, 1002), reconciled.request(updated.capabilities()))
        val restored = reconciled.reconcile(screen(snapshot = initial))
        assertEquals(CodecRequest(4, 8, 1, 2, 1002), restored.request(initial.capabilities()))
    }

    private fun screen(
        address: String? = this.address,
        revision: Long = 1,
        snapshot: Snapshot = Snapshot(aac44State, "AAC", aacCapabilities),
    ) = ScreenState(
        address?.let { listOf(DeviceInfo(it, "Headphones", true)) }.orEmpty(),
        address,
        snapshot.takeIf { address != null },
        false, true, ScreenState.AccessMode.SYSTEM, false, 0,
        ScreenState.OperationFailure.NONE, "", revision, false, null,
    )
}

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.lifecycle.SavedStateHandle
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.controller.SubmissionFeedback
import com.myuverr.codecobo.core.CodecCapability
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.core.aac44State
import com.myuverr.codecobo.core.aac48Request
import com.myuverr.codecobo.core.aacCapabilities
import com.myuverr.codecobo.platform.BluetoothRepository.DeviceInfo
import com.myuverr.codecobo.platform.BluetoothRepository.Snapshot
import com.myuverr.codecobo.platform.ProfileFacts
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    private val first = "A1:B2:C3:D4:E5:F6"
    private val second = "A1:B2:C3:D4:E5:F7"
    private val noProfiles = ProfileFacts(emptyList(), paused = false)
    private val enabled = DeviceProfile(first, "Headphones", "AAC", aac48Request, true, 1)
    private val profiles = ProfileFacts(listOf(enabled), paused = false)
    private val actual = Snapshot(aac44State, "AAC", aacCapabilities)
    private val limited = Snapshot(aac44State, "AAC", listOf(CodecCapability(1, "AAC", 1, 1, 2)))

    @Test
    fun restoredEditsWaitForDevicesAndProfilesAndUseCurrentCapabilities() {
        val handle = SavedStateHandle()
        val original = HomeViewModel(handle)
        original.updateSources(screen(), true, profiles)
        original.selectParameter(ParameterKind.RATE, aac48Request.sampleRate())
        original.setRestore(false)

        val restored = HomeViewModel(handle.restored())
        restored.updateSources(screen(address = null), false, null)
        assertNull(restored.submission())
        restored.updateSources(screen(snapshot = limited), true, null)
        assertFalse(restored.state.canSubmit)
        restored.updateSources(screen(snapshot = limited), true, profiles)
        assertEquals(aac44State.sampleRate(), restored.state.selection?.sampleRate)
        assertFalse(restored.state.restoreEnabled)
        assertTrue(restored.state.restorePending)

        restored.updateSources(screen(), true, profiles)
        assertEquals(aac48Request, (restored.submission() as HomeSubmission.Submit).request)
        assertNull(restored.state.currentSubmission)
    }

    @Test
    fun authoritativeDeviceChangeDiscardsDraftInsteadOfReapplyingItToAnotherDevice() {
        val handle = SavedStateHandle()
        val original = HomeViewModel(handle)
        original.updateSources(screen(), true, noProfiles)
        original.selectParameter(ParameterKind.RATE, aac48Request.sampleRate())
        original.setRestore(true)

        val restored = HomeViewModel(handle.restored())
        restored.updateSources(screen(second), true, noProfiles)
        assertEquals(aac44State.sampleRate(), restored.state.selection?.sampleRate)
        assertFalse(restored.state.restoreEnabled)
        restored.updateSources(screen(), true, noProfiles)
        assertEquals(aac44State.sampleRate(), restored.state.selection?.sampleRate)
    }

    @Test
    fun libraryTargetWaitsForCapabilitiesAndKeepsTheExactSavedRequestUntilValid() {
        val handle = SavedStateHandle()
        val original = HomeViewModel(handle)
        original.updateSources(screen(snapshot = null), true, profiles)
        assertEquals(EditSavedResult.OpenHome(deviceToSelect = null), original.editSaved(first))

        val restored = HomeViewModel(handle.restored())
        restored.updateSources(screen(address = null), false, profiles)
        restored.updateSources(screen(snapshot = limited), true, noProfiles)
        assertTrue(restored.state.savedDraftUnavailable)
        assertNull(restored.submission())
        restored.updateSources(screen(), true, noProfiles)
        assertFalse(restored.state.savedDraftUnavailable)
        assertEquals(aac48Request, (restored.submission() as HomeSubmission.Submit).request)
    }

    @Test
    fun editingASavedProfileForAnotherDeviceOnlyEnablesSubmitAfterThatDeviceIsRead() {
        val viewModel = HomeViewModel(SavedStateHandle())
        val otherProfile = DeviceProfile(second, "Other headphones", "AAC", aac48Request, false, 2)
        val facts = ProfileFacts(listOf(otherProfile), paused = false)
        viewModel.updateSources(screen(connected = listOf(first, second)), true, facts)

        assertEquals(EditSavedResult.OpenHome(deviceToSelect = second), viewModel.editSaved(second))
        assertTrue(viewModel.state.changingDevice)
        assertNull(viewModel.submission())

        viewModel.updateSources(screen(second, connected = listOf(first, second)), true, facts)
        assertFalse(viewModel.state.changingDevice)
        assertEquals(aac48Request, (viewModel.submission() as HomeSubmission.Submit).request)
    }

    @Test
    fun replacingTheActivityControllerKeepsEditsButUsesTheNewRevisionAndNoOldFeedback() {
        val viewModel = HomeViewModel(SavedStateHandle())
        val receipt = SubmissionFeedback(1, first, aac48Request, SubmissionIntent.APPLY, SubmissionFeedback.Status.APPLIED)
        viewModel.updateSources(screen(revision = 4, feedback = receipt), true, profiles)
        viewModel.selectParameter(ParameterKind.RATE, aac48Request.sampleRate())
        assertEquals(receipt, viewModel.state.currentSubmission)

        viewModel.attachController()
        viewModel.updateSources(screen(snapshot = null, revision = 0), false, profiles)
        viewModel.updateSources(screen(revision = 0), true, profiles)
        assertEquals(aac48Request.sampleRate(), viewModel.state.selection?.sampleRate)
        assertNull(viewModel.state.currentSubmission)
        viewModel.updateSources(screen(revision = 1), true, profiles)
        assertEquals(aac44State.sampleRate(), viewModel.state.selection?.sampleRate)
    }

    @Test
    fun interruptedSubmissionIsNotRestoredAsAnUnsubmittedDraft() {
        val handle = SavedStateHandle()
        val original = HomeViewModel(handle)
        original.updateSources(screen(), true, noProfiles)
        original.selectParameter(ParameterKind.RATE, aac48Request.sampleRate())
        original.setRestore(true)
        val running = SubmissionFeedback(1, first, aac48Request, SubmissionIntent.APPLY_AND_SAVE, SubmissionFeedback.Status.RUNNING)
        original.updateSources(screen(feedback = running), true, noProfiles)
        assertTrue(original.state.submitting)
        assertNull(original.submission())

        val restored = HomeViewModel(handle.restored())
        restored.updateSources(screen(), true, noProfiles)
        assertEquals(aac44State.sampleRate(), restored.state.selection?.sampleRate)
        assertFalse(restored.state.restoreEnabled)
        assertNull(restored.state.currentSubmission)
    }

    private fun SavedStateHandle.restored(): SavedStateHandle = SavedStateHandle(keys().associateWith { key ->
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(get<Any>(key)) }
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    })

    private fun screen(
        address: String? = first,
        snapshot: Snapshot? = actual,
        revision: Long = 1,
        feedback: SubmissionFeedback? = null,
        connected: List<String> = listOfNotNull(address),
    ): ScreenState {
        val submitting = feedback?.status() == SubmissionFeedback.Status.RUNNING
        return ScreenState(
            connected.map { DeviceInfo(it, "Headphones", it == address) }, address,
            snapshot.takeIf { address != null }, submitting, true, ScreenState.AccessMode.SYSTEM,
            false, 0, ScreenState.OperationFailure.NONE, "", revision, submitting, feedback,
        )
    }
}

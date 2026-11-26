// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.platform.ProfileFacts

internal class HomeViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var state by mutableStateOf(HomeState(draft = savedState[DraftKey] ?: EmptyDraft))
        private set

    private var editorRevision: Long? = null

    fun attachController() {
        // Revisions and in-flight operations belong to the Activity's controller instance.
        editorRevision = null
        val draft = if (state.screen?.savePending() == true) EmptyDraft else state.draft
        publish(HomeState(draft = draft))
    }

    fun updateSources(screen: ScreenState, devicesKnown: Boolean, facts: ProfileFacts?) {
        // An empty list before A2DP discovery is not a confirmed device change.
        val draft = if (devicesKnown) {
            val revision = screen.editorRevision()
            val invalidated = editorRevision != null && editorRevision != revision
            editorRevision = revision
            state.draft.reconcile(screen, facts, invalidated)
        } else state.draft
        publish(HomeState(screen, facts, draft))
    }

    fun selectDevice(address: String): Boolean {
        val screen = state.connection ?: return false
        if (screen.busy() || address == screen.selectedAddress() || screen.devices().none { it.address() == address }) {
            return false
        }
        changeDraft(HomeDraft(editor = HomeEditor(deviceAddress = address)))
        return true
    }

    fun editSaved(address: String): EditSavedResult {
        val screen = state.screen ?: return EditSavedResult.Ignored
        if (screen.busy()) return EditSavedResult.Ignored
        val target = screen.devices().firstOrNull { it.address().equals(address, ignoreCase = true) }?.address()
            ?: return EditSavedResult.Disconnected
        val facts = state.facts ?: return EditSavedResult.Ignored
        val pending = facts.profile(target)?.request()?.let { SavedDraft(target, it) }
        val sameDevice = target == screen.selectedAddress()
        val draft = if (sameDevice) {
            state.draft.copy(pendingSaved = pending).reconcile(screen, facts, editorInvalidated = false)
        } else HomeDraft(editor = HomeEditor(deviceAddress = target), pendingSaved = pending)
        changeDraft(draft)
        return EditSavedResult.OpenHome(deviceToSelect = target.takeUnless { sameDevice })
    }

    fun selectParameter(kind: ParameterKind, value: Int) {
        if (!state.canEditDraft) return
        val editor = state.editor
        val capabilities = state.capabilities
        val edited = when (kind) {
            ParameterKind.CODEC -> editor.selectCodec(value, capabilities)
            ParameterKind.RATE -> editor.selectRate(value, capabilities)
            ParameterKind.BITS -> editor.selectBits(value, capabilities)
            ParameterKind.CHANNEL -> editor.selectChannel(value, capabilities)
            ParameterKind.QUALITY -> editor.selectQuality(value, capabilities)
        }
        changeDraft(state.draft.copy(editor = edited, pendingSaved = null))
    }

    fun setRestore(enabled: Boolean) {
        if (state.busy || state.changingDevice) return
        val address = state.selectedAddress ?: return
        val savedEnabled = state.saved?.enabled() == true
        val restore = if (enabled == savedEnabled) null else RestoreDraft(address, enabled)
        changeDraft(state.draft.copy(restore = restore))
    }

    fun submission(): HomeSubmission? = when {
        state.canPrepare -> HomeSubmission.Prepare
        state.canSubmit -> state.request?.let { HomeSubmission.Submit(it, state.submission, state.restoreEnabled) }
        else -> null
    }

    private fun changeDraft(draft: HomeDraft) = publish(state.copy(draft = draft))

    private fun publish(next: HomeState) {
        val previousCheckpoint = state.recoveryDraft()
        state = next
        val checkpoint = next.recoveryDraft()
        if (checkpoint != previousCheckpoint) savedState[DraftKey] = checkpoint
    }

    // The in-memory draft drives the UI; an interrupted operation starts from readback on recreation.
    private fun HomeState.recoveryDraft() = draft.takeUnless { it == EmptyDraft || screen?.savePending() == true }

    private companion object {
        const val DraftKey = "home.draft"
        val EmptyDraft = HomeDraft()
    }
}

internal sealed interface EditSavedResult {
    data class OpenHome(val deviceToSelect: String?) : EditSavedResult
    data object Disconnected : EditSavedResult
    data object Ignored : EditSavedResult
}

internal sealed interface HomeSubmission {
    data object Prepare : HomeSubmission
    data class Submit(val request: CodecRequest, val intent: SubmissionIntent, val enableRestore: Boolean) : HomeSubmission
}

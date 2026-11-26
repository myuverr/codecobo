// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.controller.SubmissionFeedback
import com.myuverr.codecobo.core.CodecRules
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.platform.ProfileFacts

internal data class HomeState(
    val screen: ScreenState? = null,
    val facts: ProfileFacts? = null,
    val draft: HomeDraft = HomeDraft(),
) {
    val connection get() = screen.takeIf { facts != null }
    val editor get() = draft.editor
    val saved get() = facts?.profile(selectedAddress)
    val restorePaused get() = facts?.paused == true
    val savedDraftUnavailable get() = capabilities.isNotEmpty() &&
        draft.pendingSaved?.let { it.address == selectedAddress } == true

    val actual get() = connection?.snapshot()?.actual()
    val capabilities get() = connection?.snapshot()?.capabilities().orEmpty()
    private val selectedCapability get() = editor.capability(capabilities)
    val selectedCodecType get() = selectedCapability?.type()
    val selection get() = selectedCapability?.let(editor::selection)
    val rates get() = selectedCapability?.rates.orEmpty()
    val bits get() = selectedCapability?.bits.orEmpty()
    val channels get() = selectedCapability?.channels.orEmpty()
    val qualities get() = selectedCapability?.qualities.orEmpty()
    val busy get() = connection?.busy() == true
    val selectedAddress get() = connection?.selectedAddress()
    val selectedDevice get() = connection?.devices()?.firstOrNull { it.address() == selectedAddress }
    val canShowEditor get() = connection?.let { it.bluetoothPermission() && it.devices().isNotEmpty() } == true
    val changingDevice get() = editor.deviceAddress != null && editor.deviceAddress != selectedAddress
    val canEditDraft get() = !changingDevice && connection?.canEdit() == true
    val canPrepare get() = !changingDevice && capabilities.isEmpty() && connection?.canPrepareEdit() == true
    private val restoreDraft get() = draft.restore?.takeIf { it.address == selectedAddress }?.enabled
    val restoreEnabled get() = restoreDraft ?: (saved?.enabled() == true)
    val restorePending get() = restoreDraft != null && restoreDraft != (saved?.enabled() == true)
    val request get() = editor.request(capabilities)
    val submission get() = SubmissionIntent.choose(request, actual, saved?.request(), restoreEnabled, restorePending)
    val canSubmit get() = canPrepare || (canEditDraft && submission != SubmissionIntent.NONE)

    private val draftAttached get() = !changingDevice && !savedDraftUnavailable
    private val selectedDeviceAvailable get() = connection?.let {
        it.bluetoothPermission() && it.modeAvailable() && selectedDevice != null
    } == true

    val savedTargetMatches get() = !restorePending && submission == SubmissionIntent.NONE &&
        draftAttached && selectedDeviceAvailable && capabilities.isNotEmpty() && connection?.savePending() != true &&
        saved?.request()?.let { CodecRules.savedMatches(request, it) && CodecRules.matches(it, actual) } == true

    private fun matchesReceipt(receipt: SubmissionFeedback) =
        CodecRules.savedMatches(request, receipt.request()) ||
            receipt.status() != SubmissionFeedback.Status.RUNNING && capabilities.isEmpty() &&
            editor.codecType == null && editor.sampleRate == null

    val currentSubmission get() = connection?.submissionFeedback()?.takeIf {
        draftAttached && selectedDeviceAvailable && it.address() == selectedAddress && matchesReceipt(it)
    }
    val completedTargetMatches get() = currentSubmission?.let { receipt ->
        !restorePending && connection?.savePending() != true && submission == SubmissionIntent.NONE &&
            CodecRules.matches(receipt.request(), actual) &&
            when (receipt.status()) {
                SubmissionFeedback.Status.SAVED -> receipt.intent().saves() && saved?.let {
                    it.address() == receipt.address() && it.request() == receipt.request()
                } == true
                SubmissionFeedback.Status.APPLIED -> receipt.intent().applies() && !receipt.intent().saves()
                else -> false
            }
    } == true
    val showCompletedPackage get() = savedTargetMatches || completedTargetMatches
    val submitting get() = connection?.savePending() == true && currentSubmission?.status() == SubmissionFeedback.Status.RUNNING
}

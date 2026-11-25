// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import android.os.Bundle
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.controller.SubmissionFeedback
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.CodecRules
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.platform.ProfileStore

internal data class HomeState(
    val connection: ScreenState? = null,
    val editor: HomeEditor = HomeEditor(),
    val saved: DeviceProfile? = null,
    val restorePaused: Boolean = false,
    val savedDraftUnavailable: Boolean = false,
    val restoreDraft: Boolean? = null,
    private val pendingSaved: SavedDraft? = null,
    private val restoreAddress: String? = connection?.selectedAddress(),
) {
    data class SavedDraft(val address: String, val request: CodecRequest)

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
    val canShowEditor get() = connection?.let { it.bluetoothPermission() && it.devices().isNotEmpty() } == true
    val changingDevice get() = editor.deviceAddress != null && editor.deviceAddress != connection?.selectedAddress()
    val canEditDraft get() = !changingDevice && connection?.canEdit() == true
    val canPrepare get() = !changingDevice && capabilities.isEmpty() && connection?.canPrepareEdit() == true
    val restoreEnabled get() = restoreDraft ?: (saved?.enabled() == true)
    val restorePending get() = restoreDraft != null && restoreDraft != (saved?.enabled() == true)
    private val request get() = editor.request(capabilities)
    val submission get() = SubmissionIntent.choose(request, connection?.snapshot()?.actual(),
        saved?.request(), restoreEnabled, restorePending)
    val canSubmit get() = canPrepare || (canEditDraft && submission != SubmissionIntent.NONE)

    private val draftAttached get() = !changingDevice && !savedDraftUnavailable
    private val selectedDeviceAvailable get() = connection?.let {
        it.bluetoothPermission() && it.modeAvailable() && it.devices().any { device -> device.address() == it.selectedAddress() }
    } == true

    val savedTargetMatches get() = !restorePending && submission == SubmissionIntent.NONE &&
        draftAttached && selectedDeviceAvailable && capabilities.isNotEmpty() && connection?.savePending() != true &&
        saved?.let {
            it.address() == connection?.selectedAddress() && CodecRules.savedMatches(request, it.request()) &&
                CodecRules.matches(it.request(), connection?.snapshot()?.actual())
        } == true

    private fun matchesReceipt(receipt: SubmissionFeedback) =
        CodecRules.savedMatches(request, receipt.request()) ||
            receipt.status() != SubmissionFeedback.Status.RUNNING && capabilities.isEmpty() &&
            editor.codecType == null && editor.sampleRate == null

    val currentSubmission get() = connection?.submissionFeedback()?.takeIf {
        draftAttached && selectedDeviceAvailable && it.address() == connection.selectedAddress() && matchesReceipt(it)
    }
    val completedTargetMatches get() = currentSubmission?.let { receipt ->
        !restorePending && connection?.savePending() != true && submission == SubmissionIntent.NONE &&
            CodecRules.matches(receipt.request(), connection?.snapshot()?.actual()) &&
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
    val matchesActual get() = request?.let { CodecRules.matches(it, connection?.snapshot()?.actual()) } == true

    fun reconcile(value: ScreenState, profile: DeviceProfile?, paused: Boolean): HomeState {
        val restore = restoreDraft.takeIf {
            restoreAddress == value.selectedAddress() && it != (profile?.enabled() == true)
        }
        val sameEditor = editor.deviceAddress == value.selectedAddress() && editor.revision == value.editorRevision()
        var unavailable = savedDraftUnavailable && sameEditor
        var nextEditor = if (unavailable) editor else editor.reconcile(value)
        var pending = pendingSaved
        val snapshot = value.snapshot()
        pending?.let { draft ->
            if (draft.address != value.selectedAddress()) pending = null
            else if (snapshot != null && snapshot.capabilities().isNotEmpty()) {
                if (CodecRules.valid(draft.request, snapshot.capabilities())) {
                    pending = null
                    nextEditor = nextEditor.copy(codecType = draft.request.type(), sampleRate = draft.request.sampleRate(),
                        bitsPerSample = draft.request.bitsPerSample(), channelMode = draft.request.channelMode(),
                        ldacQuality = draft.request.ldacQuality())
                    unavailable = false
                } else {
                    nextEditor = nextEditor.clearParameters()
                    unavailable = true
                }
            }
        }
        return copy(connection = value, editor = nextEditor, saved = profile, restorePaused = paused,
            savedDraftUnavailable = unavailable, pendingSaved = pending, restoreDraft = restore,
            restoreAddress = restoreAddress.takeIf { restore != null })
    }

    fun selectDevice(address: String): HomeState {
        if (busy || address == connection?.selectedAddress()) return this
        return copy(editor = HomeEditor(deviceAddress = address), pendingSaved = null,
            savedDraftUnavailable = false, restoreDraft = null, restoreAddress = null)
    }

    fun editSaved(address: String, request: CodecRequest?): HomeState {
        if (busy) return this
        val sameDevice = connection?.selectedAddress() == address
        return copy(pendingSaved = request?.let { SavedDraft(address, it) },
            editor = if (sameDevice) editor else HomeEditor(deviceAddress = address),
            savedDraftUnavailable = sameDevice && savedDraftUnavailable)
    }

    fun editParameters(change: (HomeEditor) -> HomeEditor): HomeState =
        if (!canEditDraft) this else copy(editor = change(editor), pendingSaved = null, savedDraftUnavailable = false)

    fun setRestore(enabled: Boolean): HomeState {
        if (busy || changingDevice) return this
        val address = connection?.selectedAddress() ?: return this
        val draft = enabled.takeUnless { it == (saved?.enabled() == true) }
        return copy(restoreDraft = draft, restoreAddress = address.takeIf { draft != null })
    }

    fun save(out: Bundle) {
        listOf("targetDevice", "targetCodec", "targetRate", "targetBits", "targetChannel", "targetQuality",
            "restoreDraft.device", "restoreDraft.enabled", "savedDraft.device", "savedDraft.codec",
            "savedDraft.rate", "savedDraft.bits", "savedDraft.channel", "savedDraft.quality").forEach(out::remove)
        // Submitted targets are replaced by readback after recreation.
        if (connection?.savePending() == true) return
        if (restoreAddress != null && restoreDraft != null) {
            out.putString("restoreDraft.device", restoreAddress)
            out.putBoolean("restoreDraft.enabled", restoreDraft)
        }
        pendingSaved?.let {
            out.putString("savedDraft.device", it.address)
            out.putInt("savedDraft.codec", it.request.type())
            out.putInt("savedDraft.rate", it.request.sampleRate())
            out.putInt("savedDraft.bits", it.request.bitsPerSample())
            out.putInt("savedDraft.channel", it.request.channelMode())
            out.putLong("savedDraft.quality", it.request.ldacQuality())
        }
        out.putString("targetDevice", editor.deviceAddress)
        editor.codecType?.let { out.putInt("targetCodec", it) }
        editor.sampleRate?.let { out.putInt("targetRate", it) }
        editor.bitsPerSample?.let { out.putInt("targetBits", it) }
        editor.channelMode?.let { out.putInt("targetChannel", it) }
        editor.ldacQuality?.let { out.putLong("targetQuality", it) }
    }

    fun restore(bundle: Bundle): HomeState {
        val address = ProfileStore.address(bundle.getString("restoreDraft.device"))
        val pending = ProfileStore.address(bundle.getString("savedDraft.device"))?.let {
            if (bundle.containsKey("savedDraft.codec") && bundle.containsKey("savedDraft.rate"))
                SavedDraft(it, CodecRequest(bundle.getInt("savedDraft.codec"), bundle.getInt("savedDraft.rate"),
                    bundle.getInt("savedDraft.bits"), bundle.getInt("savedDraft.channel"), bundle.getLong("savedDraft.quality")))
            else null
        }
        return copy(restoreAddress = address,
            restoreDraft = if (address != null && bundle.containsKey("restoreDraft.enabled")) bundle.getBoolean("restoreDraft.enabled") else null,
            pendingSaved = pending, editor = HomeEditor(
                deviceAddress = bundle.getString("targetDevice"),
                codecType = if (bundle.containsKey("targetCodec")) bundle.getInt("targetCodec") else null,
                sampleRate = if (bundle.containsKey("targetRate")) bundle.getInt("targetRate") else null,
                bitsPerSample = if (bundle.containsKey("targetBits")) bundle.getInt("targetBits") else null,
                channelMode = if (bundle.containsKey("targetChannel")) bundle.getInt("targetChannel") else null,
                ldacQuality = if (bundle.containsKey("targetQuality")) bundle.getLong("targetQuality") else null))
    }
}

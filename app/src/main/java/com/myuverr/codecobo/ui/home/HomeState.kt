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
    val selectedAddress get() = connection?.selectedAddress()
    val selectedDevice get() = connection?.devices()?.firstOrNull { it.address() == selectedAddress }
    val canShowEditor get() = connection?.let { it.bluetoothPermission() && it.devices().isNotEmpty() } == true
    val changingDevice get() = editor.deviceAddress != null && editor.deviceAddress != selectedAddress
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
        it.bluetoothPermission() && it.modeAvailable() && selectedDevice != null
    } == true

    val savedTargetMatches get() = !restorePending && submission == SubmissionIntent.NONE &&
        draftAttached && selectedDeviceAvailable && capabilities.isNotEmpty() && connection?.savePending() != true &&
        saved?.let {
            it.address() == selectedAddress && CodecRules.savedMatches(request, it.request()) &&
                CodecRules.matches(it.request(), connection?.snapshot()?.actual())
        } == true

    private fun matchesReceipt(receipt: SubmissionFeedback) =
        CodecRules.savedMatches(request, receipt.request()) ||
            receipt.status() != SubmissionFeedback.Status.RUNNING && capabilities.isEmpty() &&
            editor.codecType == null && editor.sampleRate == null

    val currentSubmission get() = connection?.submissionFeedback()?.takeIf {
        draftAttached && selectedDeviceAvailable && it.address() == selectedAddress && matchesReceipt(it)
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
        if (busy || address == selectedAddress) return this
        return copy(editor = HomeEditor(deviceAddress = address), pendingSaved = null,
            savedDraftUnavailable = false, restoreDraft = null, restoreAddress = null)
    }

    fun editSaved(address: String, request: CodecRequest?): HomeState {
        if (busy) return this
        val sameDevice = selectedAddress == address
        return copy(pendingSaved = request?.let { SavedDraft(address, it) },
            editor = if (sameDevice) editor else HomeEditor(deviceAddress = address),
            savedDraftUnavailable = sameDevice && savedDraftUnavailable)
    }

    fun editParameters(change: (HomeEditor) -> HomeEditor): HomeState =
        if (!canEditDraft) this else copy(editor = change(editor), pendingSaved = null, savedDraftUnavailable = false)

    fun setRestore(enabled: Boolean): HomeState {
        if (busy || changingDevice) return this
        val address = selectedAddress ?: return this
        val draft = enabled.takeUnless { it == (saved?.enabled() == true) }
        return copy(restoreDraft = draft, restoreAddress = address.takeIf { draft != null })
    }

    fun save(out: Bundle) {
        Keys.All.forEach(out::remove)
        // Submitted targets are replaced by readback after recreation.
        if (connection?.savePending() == true) return
        if (restoreAddress != null && restoreDraft != null) {
            out.putString(Keys.RestoreDevice, restoreAddress)
            out.putBoolean(Keys.RestoreEnabled, restoreDraft)
        }
        pendingSaved?.let {
            out.putString(Keys.SavedDevice, it.address)
            out.putInt(Keys.SavedCodec, it.request.type())
            out.putInt(Keys.SavedRate, it.request.sampleRate())
            out.putInt(Keys.SavedBits, it.request.bitsPerSample())
            out.putInt(Keys.SavedChannel, it.request.channelMode())
            out.putLong(Keys.SavedQuality, it.request.ldacQuality())
        }
        out.putString(Keys.TargetDevice, editor.deviceAddress)
        out.putOptionalInt(Keys.TargetCodec, editor.codecType)
        out.putOptionalInt(Keys.TargetRate, editor.sampleRate)
        out.putOptionalInt(Keys.TargetBits, editor.bitsPerSample)
        out.putOptionalInt(Keys.TargetChannel, editor.channelMode)
        out.putOptionalLong(Keys.TargetQuality, editor.ldacQuality)
    }

    fun restore(bundle: Bundle): HomeState {
        val address = ProfileStore.address(bundle.getString(Keys.RestoreDevice))
        val pending = ProfileStore.address(bundle.getString(Keys.SavedDevice))?.let {
            if (bundle.containsKey(Keys.SavedCodec) && bundle.containsKey(Keys.SavedRate))
                SavedDraft(it, CodecRequest(bundle.getInt(Keys.SavedCodec), bundle.getInt(Keys.SavedRate),
                    bundle.getInt(Keys.SavedBits), bundle.getInt(Keys.SavedChannel),
                    bundle.getLong(Keys.SavedQuality)))
            else null
        }
        return copy(restoreAddress = address,
            restoreDraft = if (address != null && bundle.containsKey(Keys.RestoreEnabled))
                bundle.getBoolean(Keys.RestoreEnabled) else null,
            pendingSaved = pending, editor = HomeEditor(
                deviceAddress = bundle.getString(Keys.TargetDevice),
                codecType = bundle.getOptionalInt(Keys.TargetCodec),
                sampleRate = bundle.getOptionalInt(Keys.TargetRate),
                bitsPerSample = bundle.getOptionalInt(Keys.TargetBits),
                channelMode = bundle.getOptionalInt(Keys.TargetChannel),
                ldacQuality = bundle.getOptionalLong(Keys.TargetQuality)))
    }
}

/** Bundle keys are listed once so save, restore and cleanup cannot drift apart. */
private object Keys {
    const val RestoreDevice = "restoreDraft.device"
    const val RestoreEnabled = "restoreDraft.enabled"
    const val SavedDevice = "savedDraft.device"
    const val SavedCodec = "savedDraft.codec"
    const val SavedRate = "savedDraft.rate"
    const val SavedBits = "savedDraft.bits"
    const val SavedChannel = "savedDraft.channel"
    const val SavedQuality = "savedDraft.quality"
    const val TargetDevice = "targetDevice"
    const val TargetCodec = "targetCodec"
    const val TargetRate = "targetRate"
    const val TargetBits = "targetBits"
    const val TargetChannel = "targetChannel"
    const val TargetQuality = "targetQuality"

    val All = listOf(RestoreDevice, RestoreEnabled, SavedDevice, SavedCodec, SavedRate, SavedBits,
        SavedChannel, SavedQuality, TargetDevice, TargetCodec, TargetRate, TargetBits, TargetChannel,
        TargetQuality)
}

private fun Bundle.putOptionalInt(key: String, value: Int?) {
    if (value != null) putInt(key, value)
}

private fun Bundle.getOptionalInt(key: String): Int? = if (containsKey(key)) getInt(key) else null

private fun Bundle.putOptionalLong(key: String, value: Long?) {
    if (value != null) putLong(key, value)
}

private fun Bundle.getOptionalLong(key: String): Long? = if (containsKey(key)) getLong(key) else null

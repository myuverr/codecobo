// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.AudioParameters
import com.myuverr.codecobo.core.CodecCapability
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.CodecRules

internal val CodecCapability.rates get() = CodecRules.rates(sampleRateMask())
internal val CodecCapability.bits get() = AudioParameters.bits(bitsPerSampleMask())
internal val CodecCapability.channels get() = AudioParameters.channels(channelModeMask())
internal val CodecCapability.qualities get() = AudioParameters.policies(type())

internal data class ParameterSelection(
    val sampleRate: Int?,
    val bitsPerSample: Int?,
    val channelMode: Int?,
    val ldacQuality: Long?,
)

/** Keeps preferred audio parameters, not the temporary fallbacks shown by the picker. */
internal data class HomeEditor(
    val deviceAddress: String? = null,
    val codecType: Int? = null,
    val sampleRate: Int? = null,
    val revision: Long = -1,
    val bitsPerSample: Int? = null,
    val channelMode: Int? = null,
    val ldacQuality: Long? = null,
) {
    fun reconcile(screen: ScreenState): HomeEditor {
        val address = screen.selectedAddress()
        val invalidated = deviceAddress != address ||
            (revision >= 0 && revision != screen.editorRevision())
        val current = if (invalidated) HomeEditor(deviceAddress = address, revision = screen.editorRevision())
            else copy(revision = screen.editorRevision())
        if (address == null) return current.clearParameters()
        if (current.codecType != null) return current
        val snapshot = screen.snapshot() ?: return current
        if (snapshot.capabilities().isEmpty()) return current
        val actual = snapshot.actual()
        return current.copy(
            codecType = actual.type(),
            sampleRate = actual.sampleRate(),
            bitsPerSample = actual.bitsPerSample(),
            channelMode = actual.channelMode(),
            ldacQuality = actual.ldacQuality(),
        )
    }

    fun capability(capabilities: List<CodecCapability>): CodecCapability? =
        capabilities.firstOrNull { it.type() == codecType } ?: capabilities.firstOrNull()

    fun selection(capability: CodecCapability): ParameterSelection = ParameterSelection(
        capability.rates.select(sampleRate),
        capability.bits.select(bitsPerSample),
        capability.channels.select(channelMode),
        capability.qualities.select(ldacQuality?.takeIf(AudioParameters::policyKnown)?.toInt())?.toLong(),
    )

    fun clearParameters() = copy(codecType = null, sampleRate = null,
        bitsPerSample = null, channelMode = null, ldacQuality = null)

    fun selectCodec(type: Int, capabilities: List<CodecCapability>): HomeEditor {
        if (type == codecType || capabilities.none { it.type() == type }) return this
        return copy(codecType = type)
    }
    fun selectRate(rate: Int, capabilities: List<CodecCapability>): HomeEditor {
        val capability = capability(capabilities) ?: return this
        return if (rate in capability.rates) copy(sampleRate = rate) else this
    }
    fun selectBits(bits: Int, capabilities: List<CodecCapability>): HomeEditor {
        val capability = capability(capabilities) ?: return this
        return if (bits in capability.bits) copy(bitsPerSample = bits) else this
    }
    fun selectChannel(channel: Int, capabilities: List<CodecCapability>): HomeEditor {
        val capability = capability(capabilities) ?: return this
        return if (channel in capability.channels) copy(channelMode = channel) else this
    }
    fun selectQuality(quality: Int, capabilities: List<CodecCapability>): HomeEditor {
        val capability = capability(capabilities) ?: return this
        return if (quality in capability.qualities) copy(ldacQuality = quality.toLong()) else this
    }

    fun request(capabilities: List<CodecCapability>): CodecRequest? {
        val capability = capability(capabilities) ?: return null
        val selected = selection(capability)
        val rate = selected.sampleRate ?: return null
        return CodecRequest(capability.type(), rate, selected.bitsPerSample ?: 0,
            selected.channelMode ?: 0, selected.ldacQuality ?: 0)
            .takeIf { CodecRules.valid(it, capabilities) }
    }
}

private fun <T> List<T>.select(preferred: T?): T? =
    preferred?.takeIf { it in this } ?: firstOrNull()

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.AudioParameters
import com.myuverr.codecobo.core.CodecCapability
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.CodecRules

internal data class HomeEditor(
    val deviceAddress: String? = null,
    val codecType: Int? = null,
    val sampleRate: Int? = null,
    val revision: Long = -1,
    // Null is unreconciled; zero is explicitly unspecified.
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
        val snapshot = screen.snapshot() ?: return current
        val capabilities = snapshot.capabilities()
        if (capabilities.isEmpty()) return current
        val capability = capabilities.find { it.type() == current.codecType }
            ?: capabilities.find { it.type() == snapshot.actual().type() }
            ?: return current.clearParameters()
        val sameCodec = current.codecType == capability.type()
        val actual = snapshot.actual().takeIf { it.type() == capability.type() }
        val rate = (if (sameCodec) current.sampleRate else actual?.sampleRate())?.takeIf {
            CodecRules.valid(CodecRequest(capability.type(), it), capabilities)
        }
        val bits = (if (sameCodec) current.bitsPerSample else null) ?: actual?.bitsPerSample() ?: 0
        val channel = (if (sameCodec) current.channelMode else null) ?: actual?.channelMode() ?: 0
        val quality = (if (sameCodec) current.ldacQuality else null) ?: actual?.ldacQuality() ?: 0
        return current.copy(codecType = capability.type(), sampleRate = rate,
            bitsPerSample = bits.takeIf { AudioParameters.single(it, capability.bitsPerSampleMask() and 7) } ?: 0,
            channelMode = channel.takeIf { AudioParameters.single(it, capability.channelModeMask() and 3) } ?: 0,
            ldacQuality = quality.takeIf { capability.type() == 4 && AudioParameters.policyKnown(it) } ?: 0)
    }

    fun clearParameters() = copy(codecType = null, sampleRate = null,
        bitsPerSample = null, channelMode = null, ldacQuality = null)

    fun selectCodec(type: Int, capabilities: List<CodecCapability>): HomeEditor {
        if (type == codecType) return this
        val capability = capabilities.find { it.type() == type } ?: return this
        return copy(codecType = type, sampleRate = CodecRules.rates(capability.sampleRateMask()).firstOrNull(),
            bitsPerSample = AudioParameters.bits(capability.bitsPerSampleMask()).singleOrNull() ?: 0,
            channelMode = AudioParameters.channels(capability.channelModeMask()).singleOrNull() ?: 0,
            ldacQuality = 0)
    }
    fun selectRate(rate: Int, capabilities: List<CodecCapability>): HomeEditor {
        if (rate == sampleRate || codecType == null) return this
        if (!CodecRules.valid(CodecRequest(codecType, rate), capabilities)) return this
        return copy(sampleRate = rate)
    }
    fun selectBits(bits: Int, capabilities: List<CodecCapability>): HomeEditor {
        val capability = capabilities.find { it.type() == codecType } ?: return this
        return if (bits in AudioParameters.bits(capability.bitsPerSampleMask())) copy(bitsPerSample = bits) else this
    }
    fun selectChannel(channel: Int, capabilities: List<CodecCapability>): HomeEditor {
        val capability = capabilities.find { it.type() == codecType } ?: return this
        return if (channel in AudioParameters.channels(capability.channelModeMask())) copy(channelMode = channel) else this
    }
    fun selectQuality(quality: Int): HomeEditor =
        if (quality in AudioParameters.policies(codecType ?: -1)) copy(ldacQuality = quality.toLong()) else this

    fun request(capabilities: List<CodecCapability>): CodecRequest? {
        val type = codecType ?: return null
        val rate = sampleRate ?: return null
        return CodecRequest(type, rate, bitsPerSample ?: 0, channelMode ?: 0, ldacQuality ?: 0)
            .takeIf { CodecRules.valid(it, capabilities) }
    }
}

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.CodecRules
import com.myuverr.codecobo.platform.ProfileFacts
import java.io.Serializable

internal data class HomeDraft(
    val editor: HomeEditor = HomeEditor(),
    val restore: RestoreDraft? = null,
    val pendingSaved: SavedDraft? = null,
) : Serializable {
    fun reconcile(screen: ScreenState, facts: ProfileFacts?, editorInvalidated: Boolean): HomeDraft {
        val address = screen.selectedAddress()
        val restoreOverride = restore?.takeIf {
            it.address == address && (facts == null || it.enabled != (facts.profile(address)?.enabled() == true))
        }
        val pending = pendingSaved?.takeIf { it.address == address }
        val capabilities = screen.snapshot()?.capabilities().orEmpty()
        return when {
            pending == null || capabilities.isEmpty() -> {
                val preferred = if (editorInvalidated) HomeEditor() else editor
                HomeDraft(preferred.reconcile(screen), restoreOverride, pending)
            }
            CodecRules.valid(pending.request, capabilities) -> HomeDraft(pending.target, restoreOverride)
            else -> HomeDraft(HomeEditor(deviceAddress = address), restoreOverride, pending)
        }
    }
}

internal data class RestoreDraft(val address: String, val enabled: Boolean) : Serializable

/** Validate the stored request before loading it; the editor may still fill in unspecified fields. */
internal data class SavedDraft(
    val address: String,
    private val codec: Int,
    private val rate: Int,
    private val bits: Int,
    private val channel: Int,
    private val quality: Long,
) : Serializable {
    constructor(address: String, request: CodecRequest) : this(address, request.type(), request.sampleRate(),
        request.bitsPerSample(), request.channelMode(), request.ldacQuality())

    val request get() = CodecRequest(codec, rate, bits, channel, quality)
    val target get() = HomeEditor(address, codec, rate, bits, channel, quality)
}

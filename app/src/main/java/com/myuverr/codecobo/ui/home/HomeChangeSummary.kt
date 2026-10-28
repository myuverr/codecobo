// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import com.myuverr.codecobo.presentation.AudioText

import com.myuverr.codecobo.R

internal fun homePendingSummary(state: HomeState, resources: android.content.res.Resources): String? {
    val audio = AudioText(resources)
    val parts = mutableListOf<String>()
    val request = state.editor.request(state.capabilities)
    val actual = state.connection?.snapshot()?.actual()
    val saved = state.saved?.request()
    val intent = state.submission
    if (request != null && (intent.applies() || intent.saves())) {
        fun changed(target: Long, current: Long?, stored: Long?): Boolean =
            intent.applies() && current != target ||
                intent.saves() && stored != null && stored != target
        if (changed(request.type().toLong(), actual?.type()?.toLong(), saved?.type()?.toLong()))
            parts += resources.getString(R.string.notice_pending_codec, state.capabilities.find { it.type() == request.type() }?.name() ?: resources.getString(R.string.notice_unknown))
        if (changed(request.sampleRate().toLong(), actual?.sampleRate()?.toLong(), saved?.sampleRate()?.toLong()))
            parts += resources.getString(R.string.notice_pending_rate, audio.rate(request.sampleRate()))
        // Unspecified fields do not own a parameter change.
        fun extended(target: Long, current: Long?, stored: Long?): Boolean =
            target != 0L && (intent.applies() && current != target ||
                intent.saves() && stored != null && stored != 0L && stored != target)
        if (extended(request.bitsPerSample().toLong(), actual?.bitsPerSample()?.toLong(), saved?.bitsPerSample()?.toLong()))
            parts += resources.getString(R.string.notice_pending_bits, audio.bitsLabel(request.bitsPerSample()))
        if (extended(request.channelMode().toLong(), actual?.channelMode()?.toLong(), saved?.channelMode()?.toLong()))
            parts += resources.getString(R.string.notice_pending_channel, audio.channelLabel(request.channelMode()))
        if (extended(request.ldacQuality(), actual?.ldacQuality(), saved?.ldacQuality()))
            parts += resources.getString(R.string.notice_pending_quality, audio.policyLabel(request.ldacQuality()))
        if (parts.isEmpty() && intent.saves() && !state.restorePending) parts += resources.getString(R.string.notice_pending_save_current)
    }
    if (state.restorePending) parts += resources.getString(
        if (state.restoreEnabled) R.string.notice_pending_restore_enable else R.string.notice_pending_restore_disable)
    val pending = parts.takeIf { it.isNotEmpty() }?.joinToString(resources.getString(R.string.audio_field_separator))?.let { resources.getString(R.string.notice_pending_changes, it) }
    val paused = if (state.restorePaused) resources.getString(R.string.notice_restore_paused) else null
    return if (pending != null && paused != null) resources.getString(R.string.notice_summary_with_pause, pending, paused)
        else pending ?: paused
}

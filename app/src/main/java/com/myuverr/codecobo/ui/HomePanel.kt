// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.ui.home.HomeActions
import com.myuverr.codecobo.ui.home.HomeScreen
import com.myuverr.codecobo.ui.home.HomeState
import com.myuverr.codecobo.ui.home.ParameterKind
import com.myuverr.codecobo.ui.home.SubmitTarget

internal class HomePanel(private val actions: Actions) {
    interface Actions {
        fun selectDevice(address: String)
        fun submit(request: CodecRequest, intent: SubmissionIntent, enableRestore: Boolean)
        fun prepareEdit()
        fun openBluetooth()
    }

    private var state by mutableStateOf(HomeState())

    val canShowEditor: Boolean get() = state.canShowEditor

    /**
     * Kept beside the navigation so it stays put while the page scrolls. [shown] is false while the
     * bar animates the action away, so a tap aimed at the next tab cannot submit from the exit.
     */
    @Composable
    fun PrimaryAction(shown: Boolean) {
        // Another device starts from a clean button instead of the previous one's save animation.
        key(state.connection?.selectedAddress(), state.editor.deviceAddress) {
            SubmitTarget(state, ::submit, shown)
        }
    }

    @Composable
    fun Content(onOpenSettings: () -> Unit) {
        val currentOpenSettings by rememberUpdatedState(onOpenSettings)
        // Actions stay stable; openSettings is read through rememberUpdatedState.
        val homeActions = remember {
            HomeActions(
                onDeviceSelected = ::selectDevice,
                onParameterSelected = ::selectParameter,
                onOpenBluetooth = actions::openBluetooth,
                onOpenSettings = { currentOpenSettings() },
                onRestoreChanged = ::setRestore,
            )
        }
        HomeScreen(state, homeActions)
    }

    fun render(value: ScreenState, saved: DeviceProfile?, paused: Boolean) {
        state = state.reconcile(value, saved, paused)
    }

    private fun selectDevice(address: String) {
        val next = state.selectDevice(address)
        if (next === state) return
        state = next
        actions.selectDevice(address)
    }

    fun editSaved(address: String, request: CodecRequest?) {
        val next = state.editSaved(address, request)
        if (next === state) return
        state = next
        if (state.connection?.selectedAddress() == address)
            state.connection?.let { render(it, state.saved, state.restorePaused) }
        else actions.selectDevice(address)
    }

    private fun selectParameter(kind: ParameterKind, value: Int) {
        val capabilities = state.capabilities
        state = state.editParameters {
            when (kind) {
                ParameterKind.CODEC -> it.selectCodec(value, capabilities)
                ParameterKind.RATE -> it.selectRate(value, capabilities)
                ParameterKind.BITS -> it.selectBits(value, capabilities)
                ParameterKind.CHANNEL -> it.selectChannel(value, capabilities)
                ParameterKind.QUALITY -> it.selectQuality(value, capabilities)
            }
        }
    }

    private fun setRestore(enabled: Boolean) {
        state = state.setRestore(enabled)
    }

    private fun submit() {
        val current = state
        if (current.canPrepare) actions.prepareEdit()
        else if (current.canSubmit) current.editor.request(current.capabilities)?.let {
            actions.submit(it, current.submission, current.restoreEnabled)
        }
    }

    fun save(out: Bundle) = state.save(out)
    fun restore(saved: Bundle) { state = state.restore(saved) }
}

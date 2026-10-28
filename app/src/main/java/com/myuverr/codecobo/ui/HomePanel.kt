// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.ui.components.FloatingOverlayState
import com.myuverr.codecobo.ui.home.HomeEditor
import com.myuverr.codecobo.ui.home.HomeScreen
import com.myuverr.codecobo.ui.home.HomeState
import com.myuverr.codecobo.ui.theme.CodecTheme

class HomePanel(context: Context, private val actions: Actions,
    private val navigationOverlay: FloatingOverlayState) {
    interface Actions {
        fun selectDevice(address: String)
        fun submit(request: CodecRequest, intent: SubmissionIntent, enableRestore: Boolean)
        fun prepareEdit()
        fun openBluetooth()
        fun openMode()
    }

    internal var state by mutableStateOf(HomeState())
        private set
    private val root = ComposeView(context).apply {
        id = R.id.home_compose
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            CodecTheme(navigationOverlay = navigationOverlay) {
                HomeScreen(
                    state,
                    onDeviceSelected = ::selectDevice,
                    onCodecSelected = { type -> editParameters { it.selectCodec(type, state.capabilities) } },
                    onRateSelected = { rate -> editParameters { it.selectRate(rate, state.capabilities) } },
                    onSubmit = ::submit,
                    onOpenBluetooth = actions::openBluetooth,
                    onOpenSettings = actions::openMode,
                    onRestoreChanged = ::setRestore,
                    onBitsSelected = { value -> editParameters { it.selectBits(value, state.capabilities) } },
                    onChannelSelected = { value -> editParameters { it.selectChannel(value, state.capabilities) } },
                    onQualitySelected = { value -> editParameters { it.selectQuality(value) } },
                )
            }
        }
    }

    fun view(): View = root

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

    private fun editParameters(change: (HomeEditor) -> HomeEditor) {
        state = state.editParameters(change)
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

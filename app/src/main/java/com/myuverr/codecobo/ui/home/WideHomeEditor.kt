// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.platform.BluetoothRepository.DeviceInfo
import com.myuverr.codecobo.ui.components.FloatingScrollContent
import com.myuverr.codecobo.ui.components.LocalFloatingNavigationClearance
import com.myuverr.codecobo.ui.theme.LocalAppTypeScale

@Composable
internal fun WideHomeEditor(
    state: HomeState,
    selected: DeviceInfo?,
    modifier: Modifier,
    onChooseDevice: () -> Unit,
    onCodecSelected: (Int) -> Unit,
    onRateSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onRestoreChanged: (Boolean) -> Unit,
    onBitsSelected: (Int) -> Unit,
    onChannelSelected: (Int) -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    val gutter = LocalAppTypeScale.current.gutter
    val navigationClearance = LocalFloatingNavigationClearance.current
    Column(modifier.fillMaxWidth().testTag("wide-editor")) {
        Box(Modifier.padding(horizontal = gutter).padding(top = 7.dp, bottom = 11.dp)) {
            DeviceSelector(state, selected, onChooseDevice)
        }
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FloatingScrollContent(navigationClearance + 16.dp) {
                Column(Modifier.weight(.36f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(bottom = navigationClearance + 16.dp).testTag("readout-pane")) {
                    if (selected != null) ActualOutputCard(state)
                    PendingTarget(state)
                }
            }
            Box(Modifier.weight(.64f).fillMaxHeight().testTag("parameter-pane")) {
                FloatingScrollContent(navigationClearance) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(bottom = navigationClearance)) {
                        if (state.capabilities.isEmpty()) ReadPreparation(onOpenSettings)
                        else key(state.connection?.selectedAddress(), state.editor.deviceAddress) {
                            TargetConfiguration(state, onCodecSelected, onRateSelected, onBitsSelected, onChannelSelected, onQualitySelected)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (selected != null) RestorePreference(state, onRestoreChanged)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

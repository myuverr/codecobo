// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.components.FloatingScrollContent
import com.myuverr.codecobo.ui.components.LocalFloatingNavigationClearance
import com.myuverr.codecobo.ui.theme.LocalAppTypeScale

@Composable
internal fun WideHomeEditor(
    state: HomeState,
    modifier: Modifier,
    onChooseDevice: () -> Unit,
    actions: HomeActions,
) {
    val gutter = LocalAppTypeScale.current.gutter
    val navigationClearance = LocalFloatingNavigationClearance.current
    Column(modifier.fillMaxWidth().testTag("wide-editor")) {
        Box(Modifier.padding(horizontal = gutter).padding(top = 7.dp, bottom = 11.dp)) {
            DeviceSelector(state, onChooseDevice)
        }
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FloatingScrollContent(navigationClearance + 16.dp) {
                Column(Modifier.weight(.36f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(bottom = navigationClearance + 16.dp).testTag("readout-pane")) {
                    if (state.selectedDevice != null) ActualOutputCard(state)
                    PendingTarget(state)
                }
            }
            Box(Modifier.weight(.64f).fillMaxHeight().testTag("parameter-pane")) {
                FloatingScrollContent(navigationClearance) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(bottom = navigationClearance)) {
                        ParameterPane(state, actions)
                    }
                }
            }
        }
    }
}

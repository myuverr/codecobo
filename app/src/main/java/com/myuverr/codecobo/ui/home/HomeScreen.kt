// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.adaptiveSp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors
import com.myuverr.codecobo.ui.theme.LocalAppTypeScale
import com.myuverr.codecobo.ui.theme.LocalPageSurface
import com.myuverr.codecobo.ui.theme.WindowTier
import com.myuverr.codecobo.ui.components.FloatingScrollContent
import com.myuverr.codecobo.ui.components.LocalFloatingNavigationClearance

@Composable
internal fun HomeScreen(
    state: HomeState,
    modifier: Modifier = Modifier,
    onDeviceSelected: (String) -> Unit,
    onCodecSelected: (Int) -> Unit,
    onRateSelected: (Int) -> Unit,
    onSubmit: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
    onRestoreChanged: (Boolean) -> Unit,
    onBitsSelected: (Int) -> Unit,
    onChannelSelected: (Int) -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    val connection = state.connection
    val devices = connection?.devices().orEmpty()
    val selected = devices.find { it.address() == connection?.selectedAddress() }
    val showEditor = connection?.bluetoothPermission() == true && devices.isNotEmpty()
    val type = LocalAppTypeScale.current
    val navigationClearance = LocalFloatingNavigationClearance.current
    var choosingDevice by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxSize(), color = LocalPageSurface.current.value) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            BoxWithConstraints(Modifier.widthIn(max = type.homeMaxWidth).fillMaxSize()) {
                // A capped pane may be narrower than the window typography tier.
                val wide = type.tier == WindowTier.EXPANDED && maxWidth >= 840.dp && maxHeight >= 480.dp
                Column {
                    Text(
                        stringResource(R.string.app_name),
                        Modifier.fillMaxWidth().heightIn(min = 62.dp).wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = type.gutter, vertical = 7.dp),
                        fontSize = 25.adaptiveSp, lineHeight = 32.adaptiveSp, fontWeight = FontWeight.SemiBold,
                    )
                    when {
                        showEditor && wide -> WideHomeEditor(
                            state, selected, Modifier.weight(1f), { choosingDevice = true },
                            onCodecSelected, onRateSelected, onOpenSettings, onRestoreChanged, onSubmit,
                            onBitsSelected, onChannelSelected, onQualitySelected,
                        )
                        showEditor -> Box(Modifier.weight(1f).fillMaxWidth()) {
                            val contentClearance = navigationClearance + 88.dp
                            FloatingScrollContent(contentClearance) {
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        .padding(horizontal = type.gutter).padding(top = 7.dp, bottom = contentClearance),
                                ) {
                                    DeviceSelector(state, selected) { choosingDevice = true }
                                    Spacer(Modifier.height(11.dp))
                                    if (selected != null) ActualOutputCard(state)
                                    PendingTarget(state)
                                    if (state.capabilities.isEmpty()) ReadPreparation(onOpenSettings)
                                    else {
                                        Spacer(Modifier.height(3.dp))
                                        key(connection?.selectedAddress(), state.editor.deviceAddress) {
                                            TargetConfiguration(state, onCodecSelected, onRateSelected, onBitsSelected, onChannelSelected, onQualitySelected)
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    if (selected != null) RestorePreference(state, onRestoreChanged)
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                            key(connection?.selectedAddress(), state.editor.deviceAddress) {
                                SubmitTarget(state, onSubmit, Modifier.align(Alignment.BottomEnd)
                                    .padding(end = type.gutter, bottom = navigationClearance + 16.dp))
                            }
                        }
                        else -> OfflineHome(state, Modifier.weight(1f), onOpenBluetooth, onOpenSettings)
                    }
                }
            }
        }
    }
    if (choosingDevice) DevicePickerDialog(state, onDismiss = { choosingDevice = false }) { address ->
        choosingDevice = false
        if (address != connection?.selectedAddress()) onDeviceSelected(address)
    }
}

@Composable
private fun OfflineHome(state: HomeState, modifier: Modifier, onOpenBluetooth: () -> Unit, onOpenSettings: () -> Unit) {
    val connection = state.connection
    val type = LocalAppTypeScale.current
    val clearance = LocalFloatingNavigationClearance.current
    Box(modifier.fillMaxWidth().testTag("offline-viewport"), contentAlignment = Alignment.TopCenter) {
        FloatingScrollContent(clearance + 12.dp) {
            Column(
                Modifier.widthIn(max = type.offlineMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = type.gutter).padding(top = 7.dp, bottom = clearance + 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when {
                    connection == null || (state.busy && connection.bluetoothPermission()) ->
                        ConnectionStage(stringResource(R.string.home_loading_title), stringResource(R.string.home_loading_hint))
                    !connection.bluetoothPermission() ->
                        ConnectionStage(stringResource(R.string.home_permission_title), stringResource(R.string.home_permission_hint), stringResource(R.string.home_permissions), onOpenSettings)
                    else -> {
                        ConnectionStage(stringResource(R.string.home_disconnected), stringResource(R.string.home_disconnected_hint), stringResource(R.string.home_bluetooth), onOpenBluetooth, external = true)
                        ConnectionGuide()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionGuide() {
    Column(Modifier.fillMaxWidth().testTag("connection-guide"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.home_guide_heading),
            modifier = Modifier.semantics { heading() },
            fontSize = 14.adaptiveSp, lineHeight = 20.adaptiveSp, fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ConnectionGuideStep(1, R.string.home_guide_connect_title, R.string.home_guide_connect_hint)
            ConnectionGuideStep(2, R.string.home_guide_parameters_title, R.string.home_guide_parameters_hint)
            ConnectionGuideStep(3, R.string.home_guide_save_title, R.string.home_guide_save_hint)
        }
    }
}

@Composable
private fun ConnectionGuideStep(number: Int, @StringRes title: Int, @StringRes description: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
            Box(
                Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.home_guide_step_number, number), fontSize = 12.adaptiveSp, lineHeight = 16.adaptiveSp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(title), fontSize = 13.adaptiveSp, lineHeight = 20.adaptiveSp, fontWeight = FontWeight.Medium)
            Text(stringResource(description), fontSize = 11.adaptiveSp, lineHeight = 17.adaptiveSp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReadPreparation(onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_prepare_heading), fontSize = 14.adaptiveSp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.home_prepare_hint), fontSize = 12.adaptiveSp, lineHeight = 19.adaptiveSp)
            TextButton(onOpenSettings) { Text(stringResource(R.string.home_permissions)) }
        }
    }
}

@Composable
private fun ConnectionStage(title: String, description: String, action: String? = null, onAction: () -> Unit = {}, external: Boolean = false) {
    val tint = LocalWorkbenchColors.current
    val visual = LocalAppTypeScale.current.factor
    Surface(modifier = Modifier.testTag("connection-stage"), color = tint.tint, contentColor = tint.onTint, shape = RoundedCornerShape(32.dp, 32.dp, 46.dp, 17.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(160.dp * visual, 123.dp * visual), contentAlignment = Alignment.Center) {
                HeadphoneIllustration(Modifier.requiredSize(160.dp * visual, 135.dp * visual))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 22.adaptiveSp, lineHeight = 29.adaptiveSp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(description, fontSize = 12.adaptiveSp, lineHeight = 21.adaptiveSp, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(22.dp))
                Button(onAction, Modifier.heightIn(min = 48.dp)) {
                    Text(action, modifier = if (external) Modifier.weight(1f, fill = false) else Modifier, fontSize = 14.adaptiveSp)
                    if (external) {
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(painterResource(R.drawable.ic_home_external), null,
                            Modifier.size(ButtonDefaults.IconSize).testTag("bluetooth-settings-external"))
                    }
                }
            }
        }
    }
}

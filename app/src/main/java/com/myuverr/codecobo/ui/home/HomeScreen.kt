// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
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
    val permitted = connection?.bluetoothPermission() == true
    // Still starting up, or nothing can be read without nearby-device access.
    val loading = connection == null || (state.busy && permitted)
    if (permitted && !state.busy) {
        DisconnectedHome(modifier, onOpenBluetooth)
        return
    }
    Box(modifier.fillMaxWidth().testTag("offline-viewport"), contentAlignment = Alignment.TopCenter) {
        FloatingScrollContent(clearance + 12.dp) {
            Column(
                Modifier.widthIn(max = type.offlineMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = type.gutter).padding(top = 7.dp, bottom = clearance + 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (loading) ConnectionStage(
                    stringResource(R.string.home_loading_title), stringResource(R.string.home_loading_hint)
                ) else ConnectionStage(
                    stringResource(R.string.home_permission_title), stringResource(R.string.home_permission_hint),
                    stringResource(R.string.home_permissions), onOpenSettings,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DisconnectedHome(modifier: Modifier, onOpenBluetooth: () -> Unit) {
    val type = LocalAppTypeScale.current
    // Barely-there brand tint: the artwork should read before the backdrop does.
    val backdrop = lerp(LocalPageSurface.current.value, MaterialTheme.colorScheme.primary, .12f)
    BoxWithConstraints(
        modifier.fillMaxWidth().testTag("offline-viewport")
            .padding(bottom = LocalFloatingNavigationClearance.current),
        contentAlignment = Alignment.TopCenter,
    ) {
        val midpoint = maxHeight / 2
        // Square backdrop spanning two thirds of the column.
        val backdropSize = (minOf(maxWidth, type.offlineMaxWidth) - type.gutter * 2) * 2f / 3f
        Column(
            Modifier.widthIn(max = type.offlineMaxWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = type.gutter).padding(bottom = type.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(type.gutter),
        ) {
            // The artwork closes the upper half; the label and action start the lower half.
            Box(Modifier.heightIn(min = midpoint), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.size(backdropSize).background(backdrop, MaterialShapes.Cookie4Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    HeadphoneIllustration(
                        Modifier.padding(type.gutter).aspectRatio(HeadphoneArtworkAspectRatio),
                        centerInk = true,
                    )
                }
            }
            Text(
                stringResource(R.string.home_disconnected),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Button(onOpenBluetooth, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_bluetooth), Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(painterResource(R.drawable.ic_home_external), null, Modifier.size(ButtonDefaults.IconSize))
            }
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
private fun ConnectionStage(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
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
                    Text(action, fontSize = 14.adaptiveSp)
                }
            }
        }
    }
}

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.ChoiceSheetItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicePickerDialog(state: HomeState, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.home_connected_devices), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (device in state.connection?.devices().orEmpty()) key(device.address()) {
                    ChoiceSheetItem(
                        title = device.name(),
                        selected = device.address() == state.selectedAddress,
                        tag = "device-choice-${device.address()}",
                        description = device.address(),
                        enabled = !state.busy,
                        leading = { Icon(painterResource(R.drawable.ic_home_headphones), null) },
                        onClick = { onSelected(device.address()) },
                    )
                }
            }
            TextButton({ scope.launch { sheetState.hide(); onDismiss() } }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_close))
            }
        }
    }
}

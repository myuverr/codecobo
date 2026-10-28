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
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.ChoiceSheetShapes
import com.myuverr.codecobo.ui.components.choiceSheetColors

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
                val devices = state.connection?.devices().orEmpty()
                for (device in devices) key(device.address()) {
                    val selected = device.address() == state.connection?.selectedAddress()
                    ListItem(
                        selected = selected,
                        onClick = { onSelected(device.address()) },
                        enabled = !state.busy,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        shapes = ChoiceSheetShapes, colors = choiceSheetColors(selected),
                        modifier = Modifier.fillMaxWidth().testTag("device-choice-" + device.address())
                            .semantics { role = Role.RadioButton },
                        leadingContent = { Icon(painterResource(R.drawable.ic_home_headphones), null) },
                        supportingContent = {
                            Text(device.address(), style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = if (selected) {
                            {
                                Icon(painterResource(R.drawable.ic_choice_check), null,
                                    Modifier.testTag("device-check-" + device.address()))
                            }
                        } else null,
                    ) {
                        Text(device.name(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            TextButton({ scope.launch { sheetState.hide(); onDismiss() } }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_close))
            }
        }
    }
}

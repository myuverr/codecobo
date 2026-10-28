// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.ui.platform.LocalResources
import com.myuverr.codecobo.presentation.AudioText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.adaptiveSp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors
import com.myuverr.codecobo.platform.BluetoothRepository.DeviceInfo

@Composable
internal fun DeviceSelector(state: HomeState, selected: DeviceInfo?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    val supporting: (@Composable () -> Unit)? = selected?.let {
        {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(4.dp).background(colors.primary, CircleShape))
                Text(stringResource(R.string.home_connected))
            }
        }
    }
    ListItem(
        onClick = onClick, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().testTag("device").semantics { role = Role.Button },
        shapes = deviceSelectorShapes(),
        colors = deviceSelectorColors(),
                leadingContent = {
            DeviceLeadingIcon(R.drawable.ic_home_headphones, Modifier.testTag("device-artwork"))
        },
        supportingContent = supporting,
        trailingContent = {
            Icon(painterResource(R.drawable.ic_home_expand), stringResource(R.string.home_select_connected), Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        },
        content = {
            Text(selected?.name() ?: stringResource(R.string.home_select_device))
        },
    )
}

@Composable
internal fun RestorePreference(state: HomeState, onRestoreChanged: (Boolean) -> Unit) {
    val audio = AudioText(LocalResources.current)
    val saved = state.saved
    val colors = MaterialTheme.colorScheme
    // Compare the draft, not new readings, against the saved configuration.
    val pending = state.restorePending
    val caption = if (saved != null)
        stringResource(R.string.home_saved_caption, saved.codecName(), audio.rate(saved.request().sampleRate())) + audio.suffix(saved.request())
    else stringResource(R.string.home_restore_no_saved)
    val storedStatus = stringResource(if (saved?.enabled() == true)
        R.string.home_restore_stored_on else R.string.home_restore_stored_off)
    val status = if (state.restorePending) stringResource(
        if (state.restoreEnabled) R.string.home_restore_pending else R.string.home_restore_pending_off) else storedStatus
    val description = stringResource(R.string.home_restore)
    val stateDescription = if (pending) stringResource(
        R.string.home_restore_pending_description, storedStatus, status,
        stringResource(R.string.home_restore_save_to_commit), caption,
    ) else stringResource(R.string.home_restore_state_description, status, caption)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).background(LocalWorkbenchColors.current.tint, RoundedCornerShape(13.dp, 9.dp, 14.dp, 9.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_home_restore), null, Modifier.size(20.dp).testTag("restore-icon"), tint = colors.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(description, fontSize = 12.adaptiveSp, lineHeight = 17.adaptiveSp, fontWeight = FontWeight.Medium)
            if (saved != null) Text(caption, modifier = Modifier.testTag("restore-saved"),
                fontSize = 11.adaptiveSp, lineHeight = 17.adaptiveSp, color = colors.onSurfaceVariant)
        }

        Switch(
            checked = state.restoreEnabled, enabled = !state.busy && !state.changingDevice,
            onCheckedChange = onRestoreChanged,
            modifier = Modifier.testTag("auto-restore").semantics {
                contentDescription = description
                this.stateDescription = stateDescription
            },
            colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
            thumbContent = {
                Icon(painterResource(if (state.restoreEnabled) R.drawable.ic_choice_check else R.drawable.ic_restore_off),
                    null, Modifier.size(SwitchDefaults.IconSize).testTag("preference-thumb"))
            },
        )
    }
}

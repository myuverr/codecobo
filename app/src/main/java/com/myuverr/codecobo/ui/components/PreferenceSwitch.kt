// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.myuverr.codecobo.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun PreferenceSwitch(
    checked: Boolean, enabled: Boolean, label: String, onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(checked = checked, onCheckedChange = onChanged, enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
        thumbContent = {
            Icon(painterResource(if (checked) R.drawable.ic_choice_check else R.drawable.ic_restore_off),
                null, Modifier.size(SwitchDefaults.IconSize))
        })
}

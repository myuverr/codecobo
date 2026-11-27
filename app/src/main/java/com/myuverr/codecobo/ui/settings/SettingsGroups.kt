// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.myuverr.codecobo.ui.components.SegmentedRow
import com.myuverr.codecobo.ui.components.SettingRow

/** One row of a settings group. The group derives each row's corner position from the list. */
internal class SettingsItem(
    val title: String,
    val description: String = "",
    val icon: Int? = null,
    val value: String = "",
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
    /** Identity for rows that move between groups; defaults to the list position. */
    val key: Any? = null,
    val modifier: Modifier = Modifier,
    val trailing: (@Composable () -> Unit)? = null,
)

@Composable
internal fun SettingsGroup(items: List<SettingsItem>) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        items.forEachIndexed { index, item ->
            key(item.key ?: index) {
                SettingRow(
                    title = item.title,
                    modifier = item.modifier,
                    description = item.description,
                    icon = item.icon,
                    value = item.value,
                    enabled = item.enabled,
                    onClick = item.onClick,
                    group = SegmentedRow(index, items.size),
                    trailing = item.trailing,
                )
            }
        }
    }
}

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.management.OwnAssociation

@Composable
internal fun RecordsScreen(state: SettingsState, onBack: () -> Unit, onRefresh: () -> Unit, onOpen: (OwnAssociation) -> Unit) {
    val data = state.data
    PageLayout(stringResource(R.string.settings_records), onBack, onRefresh) {
        when {
            data.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            data.associationError != null -> PageNote(stringResource(R.string.notice_association_read_failed))
            data.readError != null -> PageNote(stringResource(R.string.notice_read_failed))
            data.associations.isEmpty() -> PageNote(stringResource(R.string.settings_no_associations))
        }
        Column(Modifier.fillMaxWidth()) {
            data.associations.forEachIndexed { index, record ->
                key(record.id) {
                    if (index > 0) Spacer(Modifier.height(2.dp))
                    SettingRow(
                        record.name?.takeIf { it.isNotBlank() } ?: record.address?.let { stringResource(R.string.notice_unnamed_device, it.takeLast(5)) }
                            ?: stringResource(R.string.library_association_id, record.id),
                        stringResource(if (data.profiles.any { it.address().equals(record.address, true) }) R.string.settings_record_saved else R.string.library_no_saved) +
                            if (data.associations.count { it.name == record.name } > 1)
                                " · ··· " + (record.address?.takeLast(5) ?: record.id.toString()) else "",
                        R.drawable.ic_home_headphones, onClick = { onOpen(record) },
                        shapes = settingsGroupShapes(index, data.associations.size),
                    )
                }
            }
        }
    }
}

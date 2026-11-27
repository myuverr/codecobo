// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.management.ManagementData
import com.myuverr.codecobo.ui.management.OwnAssociation

@Composable
internal fun RecordsScreen(
    data: ManagementData,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (OwnAssociation) -> Unit,
) {
    val rows = remember(data.associations, data.profiles) { associationRows(data.associations, data.profiles) }
    PageLayout(stringResource(R.string.settings_records), onBack, onRefresh) {
        when {
            data.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            data.associationError != null -> PageNote(stringResource(R.string.notice_association_read_failed))
            data.readError != null -> PageNote(stringResource(R.string.notice_read_failed))
            rows.isEmpty() -> PageNote(stringResource(R.string.settings_no_associations))
        }
        AssociationGroup(rows.filterNot { it.saved }, R.string.settings_records_unsaved, onOpen)
        AssociationGroup(rows.filter { it.saved }, R.string.settings_records_saved, onOpen)
    }
}

/** The group heading already says whether a configuration exists, so rows carry only the name. */
@Composable
private fun AssociationGroup(
    rows: List<AssociationRow>,
    @StringRes heading: Int,
    onOpen: (OwnAssociation) -> Unit,
) {
    if (rows.isEmpty()) return
    SectionLabel(stringResource(heading))
    SettingsGroup(
        rows.map { row ->
            SettingsItem(
                title = associationTitle(row),
                icon = R.drawable.ic_home_headphones,
                key = row.association.id,
                onClick = { onOpen(row.association) },
            )
        },
    )
}

@Composable
private fun associationTitle(row: AssociationRow): String {
    val label = when (val label = row.label) {
        is AssociationLabel.Named -> label.name
        is AssociationLabel.AddressSuffix -> stringResource(R.string.notice_unnamed_device, label.suffix)
        is AssociationLabel.Identifier -> stringResource(R.string.library_association_id, label.id)
    }
    val disambiguator = row.disambiguator ?: return label
    return label + " · ··· " + disambiguator
}

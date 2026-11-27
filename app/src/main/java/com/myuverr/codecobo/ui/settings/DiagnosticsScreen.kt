// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.management.ManagementData

@Composable
internal fun DiagnosticsScreen(
    data: ManagementData,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onClear: () -> Unit,
) {
    val diagnostic = data.diagnostics
    PageLayout(stringResource(R.string.settings_diagnostics), onBack, onRefresh) {
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.restore_diagnostics_copy),
                    icon = R.drawable.ic_settings_info,
                    enabled = !data.loading && diagnostic.isNotBlank(),
                    onClick = { onCopy(diagnostic) },
                ),
                SettingsItem(
                    title = stringResource(R.string.restore_diagnostics_clear),
                    icon = R.drawable.ic_settings_info,
                    enabled = !data.loading,
                    onClick = onClear,
                ),
            ),
        )
        Spacer(Modifier.height(20.dp))
        data.readError?.let { PageNote(stringResource(R.string.notice_read_failed)) }
        if (data.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else SelectionContainer {
            Text(
                diagnostic.ifBlank { stringResource(R.string.settings_trace_empty) },
                Modifier.testTag("diagnostics-text"),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

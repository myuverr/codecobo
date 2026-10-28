// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.presentation.AudioText
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.home.HeadphoneIllustration
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceDetailsScreen(
    state: LibraryState,
    detail: DeviceDetail,
    onBack: () -> Unit,
    onRestore: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val audio = AudioText(LocalResources.current)
    val colors = MaterialTheme.colorScheme
    val name = detail.name ?: detail.key.address?.let { stringResource(R.string.notice_unnamed_device, it.takeLast(5)) }
        ?: stringResource(R.string.notice_unnamed_association, detail.key.associationId!!)
    val profile = detail.saved
    val busy = state.connection?.busy() == true
    PageLayout(name, onBack = onBack) {
        state.data.readError?.let { PageNote(stringResource(R.string.notice_profile_read_failed), Modifier.padding(bottom = 12.dp)) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(122.dp, 110.dp), contentAlignment = Alignment.Center) {
                HeadphoneIllustration(Modifier.size(122.dp, 103.dp).testTag("detail-artwork"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(if (detail.connected) R.string.library_connected else R.string.library_offline),
                    style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                detail.key.address?.let { address ->
                    SelectionContainer {
                        Text(address, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                            color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        Card(
            Modifier.fillMaxWidth(), shape = LibraryCardShape,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(stringResource(R.string.library_saved_target), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                if (profile == null) Text(stringResource(R.string.library_no_saved), style = MaterialTheme.typography.bodyMedium)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(profile.codecName(), Modifier.alignByBaseline(), style = MaterialTheme.typography.headlineSmall)
                    Text(audio.rate(profile.request().sampleRate()), Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                val savedDetails = profile?.request()?.let { audio.details(it) }.orEmpty()
                if (savedDetails.isNotEmpty()) Text(savedDetails, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
        }
        if (detail.connected) {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(), shape = LibraryCardShape,
                colors = CardDefaults.cardColors(containerColor =
                    lerp(colors.surfaceContainerLow, LocalWorkbenchColors.current.tint, .55f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(stringResource(if (busy && detail.actual != null) R.string.library_actual_last else R.string.library_actual),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    val actual = detail.actual
                    if (actual == null) PageNote(stringResource(R.string.library_actual_unknown))
                    else Text(actual.codecName() + " · " + audio.rate(actual.actual().sampleRate()) + audio.suffix(actual.actual()),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = LibraryCardShape,
            color = if (state.data.restorePaused) colors.surfaceContainerHighest else colors.surfaceContainerLow) {
            LibraryRestoreControl(profile, state.data.restorePaused, busy,
                { enabled -> profile?.let { onRestore(it.address(), enabled) } },
                Modifier.testTag("detail-restore"))
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { detail.key.address?.let(onEdit) },
            shapes = ButtonDefaults.shapes(shape = LibraryCardShape),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("edit-profile"),
            enabled = detail.connected && detail.key.address != null && !busy,
        ) {
            Icon(painterResource(R.drawable.ic_home_target), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (detail.connected) R.string.library_edit else R.string.library_edit_offline),
                style = MaterialTheme.typography.bodyMedium)
        }
        if (!detail.connected) Text(stringResource(R.string.library_offline_edit_hint),
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
        if (profile != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDelete, shapes = ButtonDefaults.shapes(shape = LibraryCardShape),
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete-profile"),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) {
                Text(stringResource(R.string.profile_delete), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

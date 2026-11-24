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
import com.myuverr.codecobo.ui.home.HeadphoneArtworkAspectRatio
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors

/** Illustration width; the box height follows the drawing's aspect ratio. */
private val DetailArtworkWidth = 122.dp

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
    val name = detail.name
        ?: detail.key.address?.let { stringResource(R.string.notice_unnamed_device, it.takeLast(5)) }
        ?: detail.key.associationId?.let { stringResource(R.string.notice_unnamed_association, it) }
        ?: stringResource(R.string.notification_unnamed_device)
    val profile = detail.saved
    val busy = state.connection?.busy() == true
    PageLayout(name, onBack = onBack) {
        state.data.readError?.let {
            PageNote(stringResource(R.string.notice_profile_read_failed), Modifier.padding(bottom = LibraryBlockGap))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = LibraryBlockGap),
            horizontalArrangement = Arrangement.spacedBy(LibraryBlockGap), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(DetailArtworkWidth).aspectRatio(HeadphoneArtworkAspectRatio)) {
                HeadphoneIllustration(Modifier.fillMaxSize().testTag("detail-artwork"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LibraryTightGap)) {
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
            Column(Modifier.fillMaxWidth().padding(LibraryInset),
                verticalArrangement = Arrangement.spacedBy(LibraryTightGap)) {
                Text(stringResource(R.string.library_saved_target), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                if (profile == null) Text(stringResource(R.string.library_no_saved), style = MaterialTheme.typography.bodyMedium)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(LibraryTightGap)) {
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
            Spacer(Modifier.height(LibraryBlockGap))
            Card(
                Modifier.fillMaxWidth(), shape = LibraryCardShape,
                colors = CardDefaults.cardColors(containerColor =
                    lerp(colors.surfaceContainerLow, LocalWorkbenchColors.current.tint, .55f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(LibraryInset),
                    verticalArrangement = Arrangement.spacedBy(LibraryTightGap)) {
                    Text(stringResource(if (busy && detail.actual != null) R.string.library_actual_last else R.string.library_actual),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    val actual = detail.actual
                    if (actual == null) PageNote(stringResource(R.string.library_actual_unknown))
                    else Text(audio.codecLine(actual.codecName(), actual.actual()),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(LibraryBlockGap))
        Surface(shape = LibraryCardShape,
            color = if (state.data.restorePaused) colors.surfaceContainerHighest else colors.surfaceContainerLow) {
            LibraryRestoreControl(profile, state.data.restorePaused, busy,
                { enabled -> profile?.let { onRestore(it.address(), enabled) } },
                Modifier.testTag("detail-restore"))
        }
        Spacer(Modifier.height(LibraryBlockGap))
        Button(
            onClick = { detail.key.address?.let(onEdit) },
            modifier = Modifier.fillMaxWidth().testTag("edit-profile"),
            enabled = detail.connected && detail.key.address != null && !busy,
        ) {
            Icon(painterResource(R.drawable.ic_home_target), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(if (detail.connected) R.string.library_edit else R.string.library_edit_offline),
                style = MaterialTheme.typography.bodyMedium)
        }
        if (profile != null) {
            Spacer(Modifier.height(LibraryTightGap))
            TextButton(
                onClick = onDelete,
                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("delete-profile"),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) {
                Text(stringResource(R.string.profile_delete), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(LibraryBlockGap))
    }
}

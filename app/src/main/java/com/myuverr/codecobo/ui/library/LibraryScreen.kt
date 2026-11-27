// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.presentation.AudioText
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors

@Composable
internal fun LibraryScreen(
    state: LibraryState,
    onOpen: (DeviceKey) -> Unit,
    onRestore: (String, Boolean) -> Unit,
    onPaused: (Boolean) -> Unit,
) {
    val resources = LocalResources.current
    val colors = MaterialTheme.colorScheme
    val data = state.data
    val busy = state.connection?.busy() == true
    val paused = data.restorePaused
    val summaryColor = if (paused) LocalWorkbenchColors.current.tint else colors.surfaceContainerLow
    val enabledCount = data.profiles.count { it.enabled() }
    PageLayout(stringResource(R.string.home_nav_headphones)) {
        if (data.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (data.readError == null) {
            Card(
                shape = LibraryCardShape,
                colors = CardDefaults.cardColors(containerColor = summaryColor),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = LibraryInset, vertical = LibraryBlockGap),
                    horizontalArrangement = Arrangement.spacedBy(LibraryBlockGap, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(LibraryTightGap, Alignment.CenterVertically),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LibraryBlockGap)) {
                        if (paused) Icon(painterResource(R.drawable.ic_restore_pause), null,
                            Modifier.size(24.dp), tint = colors.primary)
                        Text(
                            if (paused) stringResource(R.string.library_paused)
                            else resources.getQuantityString(R.plurals.library_enabled_count, enabledCount, enabledCount),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start, color = if (paused) colors.primary else colors.onSurface,
                        )
                    }
                    TextButton(
                        { onPaused(!paused) }, enabled = !busy,
                        modifier = Modifier.testTag("pause-all"), shape = LibraryCardShape,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (paused) colors.primary else Color.Transparent,
                            contentColor = if (paused) colors.onPrimary else colors.primary),
                    ) {
                        Text(stringResource(if (paused) R.string.library_resume_all else R.string.library_pause_all),
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        data.readError?.let {
            PageNote(stringResource(R.string.notice_profile_read_failed), Modifier.padding(top = LibraryBlockGap))
        }
        SectionLabel(stringResource(R.string.saved_profiles_section))
        if (data.profiles.isEmpty() && !data.loading && data.readError == null) {
            Card(shape = LibraryCardShape,
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                SettingRow(
                    title = stringResource(R.string.library_empty),
                    description = stringResource(R.string.library_empty_hint),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(LibraryBlockGap)) {
            for (profile in data.profiles) key(profile.address()) {
                SavedProfileCard(
                    profile = profile,
                    connected = state.connection?.devices()?.any { sameAddress(it.address(), profile.address()) } == true,
                    busy = busy, paused = paused,
                    onOpen = { onOpen(DeviceKey(profile.address())) },
                    onRestore = { onRestore(profile.address(), it) },
                )
            }
        }
    }
}

@Composable
private fun SavedProfileCard(
    profile: DeviceProfile,
    connected: Boolean,
    busy: Boolean,
    paused: Boolean,
    onOpen: () -> Unit,
    onRestore: (Boolean) -> Unit,
) {
    val audio = AudioText(LocalResources.current)
    val colors = MaterialTheme.colorScheme
    val upperColor = if (connected) LocalWorkbenchColors.current.tint else colors.surfaceContainerLow
    val cardColor = if (paused) colors.surfaceContainerHighest else colors.surfaceContainer
    Card(
        Modifier.fillMaxWidth().testTag("profile-card-" + profile.address()), shape = LibraryCardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        // The restore switch is not a descendant of the navigation target.
        ListItem(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().testTag("profile-" + profile.address()).semantics { role = Role.Button },
            shapes = libraryConfigurationShapes(),
            colors = ListItemDefaults.colors(containerColor = upperColor),
            contentPadding = PaddingValues(LibraryInset),
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(LibraryBlockGap),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        Modifier.size(42.dp), shape = RoundedCornerShape(12.dp),
                        color = lerp(colors.surface, colors.primaryContainer, .4f), contentColor = colors.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_home_headphones), null)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(profile.name(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(LibraryTightGap),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (connected) Surface(Modifier.size(4.dp), shape = CircleShape, color = colors.primary) {}
                                Text(
                                    stringResource(if (connected) R.string.library_connected else R.string.library_offline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (connected) colors.primary else colors.onSurfaceVariant,
                                )
                            }
                            Text(profile.address(),
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                                color = colors.onSurfaceVariant)
                        }
                    }
                    Icon(painterResource(R.drawable.ic_home_expand), null,
                        Modifier.size(16.dp).rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 90f else -90f),
                        tint = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(LibraryBlockGap))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LibraryTightGap),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile.codecName(), Modifier.alignByBaseline(), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text(audio.rate(profile.request().sampleRate()), Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), color = colors.onSurfaceVariant)
                }
                val details = audio.details(profile.request())
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(details, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = colors.onSurfaceVariant)
                }
            }
        }
        LibraryRestoreControl(profile, paused, busy, onRestore,
            Modifier.testTag("library-restore-" + profile.address()), explainPause = false)
    }
}

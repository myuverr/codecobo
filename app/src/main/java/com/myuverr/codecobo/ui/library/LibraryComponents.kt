// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.core.DeviceProfile

internal val LibraryCardShape = RoundedCornerShape(20.dp)

/** Gap between lines that describe the same value. */
internal val LibraryTightGap = 8.dp

/** Gap between blocks in a card, and between cards. */
internal val LibraryBlockGap = 12.dp

/** Padding inside a card; page gutters come from the window layout. */
internal val LibraryInset = 16.dp

private val LibraryConfigurationShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

@Composable
internal fun libraryConfigurationShapes(): ListItemShapes {
    val native = ListItemDefaults.shapes()
    val straight = CornerSize(0.dp)
    // Fix only the shared lower edge; upper corners retain the native state shapes.
    fun topOnly(shape: Shape): Shape = if (shape is CornerBasedShape)
        shape.copy(bottomStart = straight, bottomEnd = straight) else LibraryConfigurationShape
    return native.copy(
        shape = LibraryConfigurationShape,
        selectedShape = topOnly(native.selectedShape),
        pressedShape = topOnly(native.pressedShape),
        focusedShape = topOnly(native.focusedShape),
        hoveredShape = topOnly(native.hoveredShape),
        draggedShape = topOnly(native.draggedShape),
    )
}

/** The modifier targets the native switch, not its non-interactive label. */
@Composable
internal fun LibraryRestoreControl(
    profile: DeviceProfile?,
    paused: Boolean,
    busy: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    explainPause: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val checked = profile?.enabled() == true
    val namesPause = paused && explainPause
    val mutedHeading = paused && !explainPause
    val label = if (profile == null) stringResource(R.string.home_restore_description)
        else stringResource(R.string.library_restore_label, profile.name())
    val pausedDescription = if (paused) stringResource(R.string.library_restore_paused_description,
        stringResource(if (checked) R.string.library_restore_paused_on_hint else R.string.library_restore_paused_off_hint)) else null
    // No explicit minimum height: the switch already provides the touch target.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LibraryInset, vertical = LibraryTightGap),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LibraryBlockGap),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LibraryTightGap)) {
            if (namesPause) Icon(painterResource(R.drawable.ic_restore_pause), null, Modifier.size(16.dp))
            // The heading stays the same when paused; only the tone changes.
            Text(stringResource(if (namesPause) R.string.library_restore_paused_title else R.string.home_restore),
                style = MaterialTheme.typography.bodyMedium,
                // Muted enough to read as inactive, still legible on the paused card.
                color = if (mutedHeading) colors.onSurfaceVariant.copy(alpha = .8f) else LocalContentColor.current)
        }
        Switch(
            checked = checked, onCheckedChange = onChanged,
            enabled = profile != null && !paused && !busy,
            modifier = modifier.semantics {
                contentDescription = label
                if (pausedDescription != null) stateDescription = pausedDescription
            },
            colors = SwitchDefaults.colors(checkedIconColor = colors.primary),
            thumbContent = {
                Icon(painterResource(if (checked) R.drawable.ic_choice_check else R.drawable.ic_restore_off),
                    null, Modifier.size(SwitchDefaults.IconSize))
            },
        )
    }
}

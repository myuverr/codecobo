// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import com.myuverr.codecobo.R

private val ChoiceSheetShape = RoundedCornerShape(16.dp)
internal val ChoiceSheetShapes = ListItemShapes(
    shape = ChoiceSheetShape,
    selectedShape = ChoiceSheetShape,
    pressedShape = ChoiceSheetShape,
    focusedShape = ChoiceSheetShape,
    hoveredShape = ChoiceSheetShape,
    draggedShape = ChoiceSheetShape,
)

@Composable
internal fun choiceSheetColors(selected: Boolean): ListItemColors {
    val native = ListItemDefaults.colors()
    return native.copy(
        containerColor = Color.Transparent,
        disabledContainerColor = if (selected) native.disabledContainerColor else Color.Transparent,
    )
}

/** The row owns selection; its check has no separate action or label. */
@Composable
internal fun ChoiceSheetItem(
    title: String, selected: Boolean, tag: String, description: String = "",
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    ListItem(
        selected = selected, onClick = onClick, enabled = enabled,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        shapes = ChoiceSheetShapes, colors = choiceSheetColors(selected),
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics { role = Role.RadioButton },
        leadingContent = leading,
        supportingContent = if (description.isBlank()) null else {
            { Text(description, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = if (selected) {
            { Icon(painterResource(R.drawable.ic_choice_check), null) }
        } else null,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

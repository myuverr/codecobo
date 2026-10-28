// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.SettingRow
import com.myuverr.codecobo.ui.components.settingRowColors
import com.myuverr.codecobo.ui.components.settingRowTitleStyle
import com.myuverr.codecobo.ui.components.settingRowValueStyle
import androidx.compose.ui.unit.dp

internal val SettingsGroupShape = RoundedCornerShape(20.dp)

@Composable
internal fun SettingsValueRow(
    title: String,
    description: String = "",
    icon: Int,
    value: String,
    shapes: ListItemShapes,
    colors: ListItemColors = settingRowColors(),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val titleStyle = settingRowTitleStyle()
    val valueStyle = settingRowValueStyle()
    val padding = ListItemDefaults.ContentPadding
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Native slots: two 24dp icons, two 16dp gaps, and the value/chevron's 8dp gap.
        val slotWidth = with(density) {
            (padding.calculateLeftPadding(direction) + padding.calculateRightPadding(direction) + 88.dp).toPx()
        }
        val textWidth = measurer.measure(title, titleStyle, softWrap = false).size.width +
            measurer.measure(value, valueStyle, softWrap = false).size.width
        val inline = value.isEmpty() || textWidth + slotWidth <= constraints.maxWidth
        val supporting = when {
            inline -> description
            description.isBlank() -> value
            else -> stringResource(R.string.settings_row_detail_with_value, description, value)
        }
        SettingRow(title, supporting, icon, if (inline) value else "", enabled, onClick,
            shapes = shapes, colors = colors)
    }
}

@Composable
internal fun settingsGroupShapes(index: Int, count: Int): ListItemShapes {
    val native = ListItemDefaults.shapes()
    val zero = CornerSize(0.dp)
    fun joined(shape: Shape): Shape = if (shape is CornerBasedShape) {
        shape.copy(
            topStart = if (index == 0) shape.topStart else zero,
            topEnd = if (index == 0) shape.topEnd else zero,
            bottomStart = if (index == count - 1) shape.bottomStart else zero,
            bottomEnd = if (index == count - 1) shape.bottomEnd else zero,
        )
    } else shape

    // Only shared edges are fixed; native outer interaction shapes remain intact.
    return native.copy(
        shape = SettingsGroupShape.copy(
            topStart = if (index == 0) SettingsGroupShape.topStart else zero,
            topEnd = if (index == 0) SettingsGroupShape.topEnd else zero,
            bottomStart = if (index == count - 1) SettingsGroupShape.bottomStart else zero,
            bottomEnd = if (index == count - 1) SettingsGroupShape.bottomEnd else zero,
        ),
        selectedShape = joined(native.selectedShape),
        pressedShape = joined(native.pressedShape),
        focusedShape = joined(native.focusedShape),
        hoveredShape = joined(native.hoveredShape),
        draggedShape = joined(native.draggedShape),
    )
}

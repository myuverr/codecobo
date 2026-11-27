// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.theme.LocalAppTypeScale
import com.myuverr.codecobo.ui.theme.LocalPageSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageLayout(
    title: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val type = LocalAppTypeScale.current
    val clearance = LocalFloatingNavigationClearance.current
    Surface(Modifier.fillMaxSize(), color = LocalPageSurface.current.value) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = type.pageMaxWidth).fillMaxSize()) {
                TopAppBar(
                    title = { PageTitle(title) },
                    // The app shell already applies system-bar and cutout insets.
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        if (onBack != null) IconButton(onBack) {
                            Icon(painterResource(R.drawable.ic_home_expand), stringResource(R.string.page_back),
                                Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Ltr) 90f else -90f))
                        }
                    },
                    actions = {
                        if (onRefresh != null) IconButton(onRefresh) {
                            Icon(painterResource(R.drawable.ic_home_refresh), stringResource(R.string.page_refresh))
                        }
                    },
                )
                FloatingScrollContent(clearance + 12.dp) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = type.gutter).padding(bottom = clearance + 24.dp), content = content)
                }
            }
        }
    }
}

@Composable
internal fun PageTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PageNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun settingRowColors(): ListItemColors = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    leadingContentColor = MaterialTheme.colorScheme.primary,
)

/** Where a row sits inside a segmented group; Material 3 derives the group's corners from it. */
@Immutable
internal data class SegmentedRow(val index: Int, val count: Int)

@Composable
internal fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String = "",
    icon: Int? = null,
    value: String = "",
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    group: SegmentedRow? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val supportingStyle = MaterialTheme.typography.bodySmall
    val valueStyle = MaterialTheme.typography.labelMedium
    val leadingContent: (@Composable () -> Unit)? = icon?.let { resource ->
        { Icon(painterResource(resource), null) }
    }
    val supportingContent: (@Composable () -> Unit)? = description.takeIf(String::isNotBlank)?.let { text ->
        { Text(text, style = supportingStyle) }
    }
    val trailingContent: (@Composable () -> Unit)? = if (value.isNotBlank() || trailing != null || onClick != null) {
        {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotBlank()) Text(value, style = valueStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (trailing != null) trailing()
                else if (onClick != null) Icon(painterResource(R.drawable.ic_home_expand), null,
                    Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 90f else -90f))
            }
        }
    } else null
    val shapes = group?.let { ListItemDefaults.segmentedShapes(it.index, it.count) } ?: ListItemDefaults.shapes()
    // The segmented defaults paint the page colour, so every row keeps its own surface tint.
    val colors = settingRowColors()
    val rowModifier = if (onClick == null) modifier.fillMaxWidth()
        else modifier.fillMaxWidth().semantics { role = Role.Button }
    val label: @Composable () -> Unit = { Text(title, style = titleStyle) }
    if (group == null) {
        if (onClick == null) ListItem(rowModifier, enabled = enabled, leadingContent = leadingContent,
            trailingContent = trailingContent, supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically, shapes = shapes, colors = colors, content = label)
        else ListItem(onClick, rowModifier, enabled = enabled, leadingContent = leadingContent,
            trailingContent = trailingContent, supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically, shapes = shapes, colors = colors, content = label)
    } else {
        if (onClick == null) SegmentedListItem(shapes, rowModifier, enabled = enabled,
            leadingContent = leadingContent, trailingContent = trailingContent,
            supportingContent = supportingContent, verticalAlignment = Alignment.CenterVertically,
            colors = colors, content = label)
        else SegmentedListItem(onClick, shapes, rowModifier, enabled = enabled,
            leadingContent = leadingContent, trailingContent = trailingContent,
            supportingContent = supportingContent, verticalAlignment = Alignment.CenterVertically,
            colors = colors, content = label)
    }
}

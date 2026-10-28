// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import com.myuverr.codecobo.ui.theme.adaptiveSp
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
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            Column(Modifier.widthIn(max = type.pageMaxWidth).fillMaxSize()) {
                TopAppBar(
                    title = { Text(title, Modifier.semantics { heading() }, fontSize = 25.adaptiveSp,
                        lineHeight = 32.adaptiveSp, fontWeight = FontWeight.SemiBold) },
                    // MainActivity owns system-bar and cutout insets.
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
internal fun SectionLabel(text: String) {
    Text(text, Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
        fontSize = 11.adaptiveSp, lineHeight = 17.adaptiveSp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PageNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 12.adaptiveSp, lineHeight = 21.adaptiveSp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun settingRowTitleStyle() = MaterialTheme.typography.bodyLarge.copy(
    fontSize = 15.adaptiveSp, lineHeight = 20.adaptiveSp, fontWeight = FontWeight.SemiBold,
)

@Composable
internal fun settingRowValueStyle() = MaterialTheme.typography.labelSmall.copy(
    fontSize = 12.adaptiveSp, lineHeight = 18.adaptiveSp,
)

@Composable
internal fun settingRowColors(): ListItemColors = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    leadingContentColor = MaterialTheme.colorScheme.primary,
)

@Composable
internal fun SettingRow(
    title: String,
    description: String = "",
    icon: Int? = null,
    value: String = "",
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = settingRowColors(),
    trailing: (@Composable () -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? = icon?.let { resource ->
        { Icon(painterResource(resource), null) }
    }
    val supporting: (@Composable () -> Unit)? = description.takeIf(String::isNotBlank)?.let { text ->
        { Text(text, fontSize = 11.adaptiveSp, lineHeight = 18.adaptiveSp) }
    }
    val end: (@Composable () -> Unit)? = if (value.isNotBlank() || trailing != null || onClick != null) {
        {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (value.isNotBlank()) Text(value, style = settingRowValueStyle())
                if (trailing != null) trailing()
                else if (onClick != null) Icon(painterResource(R.drawable.ic_home_expand), null,
                    Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 90f else -90f))
            }
        }
    } else null
    if (onClick == null) {
        ListItem(modifier = Modifier.fillMaxWidth(), enabled = enabled, shapes = shapes, colors = colors,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, leadingContent = leading,
            supportingContent = supporting, trailingContent = end) { Text(title, style = settingRowTitleStyle()) }
    } else {
        ListItem(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
            enabled = enabled, shapes = shapes, colors = colors,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, leadingContent = leading,
            supportingContent = supporting, trailingContent = end) { Text(title, style = settingRowTitleStyle()) }
    }
}

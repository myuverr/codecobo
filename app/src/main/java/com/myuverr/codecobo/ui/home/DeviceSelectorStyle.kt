// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors

internal val DeviceArtworkShape = RoundedCornerShape(16.dp, 12.dp, 19.dp, 12.dp)
@Composable
internal fun deviceSelectorShapes(): ListItemShapes = ListItemDefaults.shapes(shape = CircleShape)

@Composable
internal fun deviceSelectorColors() =
    ListItemDefaults.colors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        leadingContentColor = MaterialTheme.colorScheme.primary,
    )

@Composable
internal fun DeviceLeadingIcon(resource: Int, modifier: Modifier = Modifier, iconSize: Dp = 26.dp) {
    Box(modifier.size(43.dp).background(LocalWorkbenchColors.current.tint, DeviceArtworkShape),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(resource), null, Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
    }
}

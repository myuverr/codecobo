// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.NavigationGlyph

internal enum class TopLevelPage(val title: Int, val icon: Int, val selectedIcon: Int) {
    CONTROL(R.string.home_nav_control, R.drawable.ic_nav_home, R.drawable.ic_nav_home_selected),
    HEADPHONES(R.string.home_nav_headphones, R.drawable.ic_nav_library, R.drawable.ic_nav_library_selected),
    SETTINGS(R.string.home_nav_settings, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_selected),
}

/**
 * Floating tab capsule with one contextual action beside it. Only the selected tab shows its
 * label, so the capsule stays about two icons plus one label wide.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppNavigationBar(
    selected: TopLevelPage,
    showAction: Boolean,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onSelected: (TopLevelPage) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val capsule = capsuleMetrics()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Surface(
            // The capsule yields width before the action does when a long label needs room.
            Modifier.weight(1f, fill = false).testTag("navigation-capsule"),
            shape = CircleShape,
            color = ShortNavigationBarDefaults.containerColor,
            shadowElevation = 1.dp,
        ) {
            // Tabs size to their own content; the bar's own layout would force equal slots.
            Row(
                modifier = Modifier.selectableGroup()
                    .padding(horizontal = capsule.horizontalInset, vertical = capsule.verticalInset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                for (page in TopLevelPage.entries) {
                    val pageSelected = selected == page
                    val title = stringResource(page.title)
                    ShortNavigationBarItem(
                        selected = pageSelected,
                        onClick = { if (!pageSelected) onSelected(page) },
                        icon = {
                            NavigationGlyph(
                                page.icon, page.selectedIcon,
                                selected = pageSelected, tint = LocalContentColor.current,
                                modifier = Modifier.size(GlyphSize).testTag("navigation-glyph-" + page.name),
                            )
                        },
                        label = {
                            AnimatedVisibility(
                                visible = pageSelected,
                                enter = expandHorizontally(animationSpec = motion.fastSpatialSpec()) +
                                    fadeIn(motion.fastEffectsSpec()),
                                exit = shrinkHorizontally(animationSpec = motion.fastSpatialSpec()) +
                                    fadeOut(motion.fastEffectsSpec()),
                            ) {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        iconPosition = NavigationItemIconPosition.Start,
                        // The label is only composed when selected; unselected tabs need a name.
                        modifier = Modifier.testTag("navigation-" + page.name)
                            .semantics { if (!pageSelected) contentDescription = title },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showAction,
            enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = motion.fastSpatialSpec()) +
                fadeIn(motion.fastEffectsSpec()),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = motion.fastSpatialSpec()) +
                fadeOut(motion.fastEffectsSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(IndicatorGap))
                CompositionLocalProvider(LocalNavigationActionSize provides capsule.height) { action() }
            }
        }
    }
}

/** Material's tab icon size, which also sets the indicator height it sits in. */
private val GlyphSize = 24.dp

/** What Material pads above and below a tab's content inside its indicator. */
private val IndicatorPadding = 8.dp

/** Air the indicator keeps from the capsule edge, on the same rhythm as the padding inside it. */
private val IndicatorGap = IndicatorPadding

/** Height the bar gives its contextual action, so both read as one row. */
internal val LocalNavigationActionSize = staticCompositionLocalOf<Dp> {
    error("LocalNavigationActionSize is provided by AppNavigationBar")
}

internal data class CapsuleMetrics(val height: Dp, val verticalInset: Dp, val horizontalInset: Dp)

/**
 * Every gap between the indicator and the capsule edge is [IndicatorGap], which also keeps their
 * corner radii concentric. A touch target taller than the indicator already covers part of the
 * vertical gap, so the padding is derived rather than equal to the gap.
 */
internal fun capsuleGeometry(labelLine: Dp, touchTarget: Dp): CapsuleMetrics {
    val indicator = maxOf(GlyphSize, labelLine) + IndicatorPadding * 2
    val item = maxOf(touchTarget, indicator)
    val verticalInset = (IndicatorGap - (item - indicator) / 2).coerceAtLeast(0.dp)
    return CapsuleMetrics(item + verticalInset * 2, verticalInset, IndicatorGap)
}

@Composable
private fun capsuleMetrics(): CapsuleMetrics = capsuleGeometry(
    // A scaled label can outgrow the glyph, and the indicator follows the taller content.
    labelLine = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() },
    touchTarget = LocalMinimumInteractiveComponentSize.current,
)

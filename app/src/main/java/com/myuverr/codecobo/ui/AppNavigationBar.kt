// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.NavigationGlyph
import kotlinx.coroutines.flow.Flow

internal enum class TopLevelPage(val title: Int, val icon: Int, val selectedIcon: Int, val motion: Int) {
    CONTROL(R.string.home_nav_control, R.drawable.ic_nav_home, R.drawable.ic_nav_home_selected, R.raw.nav_house_motion),
    HEADPHONES(R.string.home_nav_headphones, R.drawable.ic_nav_library, R.drawable.ic_nav_library_selected, R.raw.nav_books_motion),
    SETTINGS(R.string.home_nav_settings, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_selected, R.raw.nav_gear_six_motion),
}

/**
 * Floating tab capsule with one contextual action beside it. Only the selected tab shows its
 * label, so the capsule is as wide as two icons plus one label whatever the selection is.
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
    val motion = MotionScheme.expressive()
    val (verticalInset, horizontalInset) = capsuleInsets()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Surface(
            // The capsule yields width before the action does when a long label needs room.
            Modifier.weight(1f, fill = false).testTag("navigation-capsule"),
            shape = CircleShape,
            color = ShortNavigationBarDefaults.containerColor,
            shadowElevation = 1.dp,
        ) {
            // Tabs size to their own content; the bar's own layout would force equal slots.
            Row(Modifier.selectableGroup().padding(horizontal = horizontalInset, vertical = verticalInset),
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
                            NavigationGlyph(page.icon, page.selectedIcon, page.motion, pageSelected,
                                LocalContentColor.current,
                                Modifier.size(GlyphSize).navigationPulse(pageSelected)
                                    .testTag("navigation-glyph-" + page.name))
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
                        interactionSource = remember { PressFreeInteractionSource() },
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
                Spacer(Modifier.width(8.dp))
                action()
            }
        }
    }
}

/** Keeps hover and focus feedback while dropping the press ripple. */
private class PressFreeInteractionSource : MutableInteractionSource {
    private val delegate = MutableInteractionSource()
    override val interactions: Flow<Interaction> = delegate.interactions
    override suspend fun emit(interaction: Interaction) {
        if (interaction !is PressInteraction) delegate.emit(interaction)
    }
    override fun tryEmit(interaction: Interaction): Boolean =
        interaction !is PressInteraction && delegate.tryEmit(interaction)
}

private val GlyphSize = 24.dp

/** Material's horizontal tab draws a 40dp indicator inside a minimum-size touch target. */
private val IndicatorHeight = 40.dp

/**
 * Leaves the same gap between the indicator and the capsule edge vertically and horizontally,
 * so the capsule corner radius equals the indicator radius plus that gap.
 */
@Composable
private fun capsuleInsets(): Pair<Dp, Dp> {
    val target = LocalMinimumInteractiveComponentSize.current
    val indicatorInset = (target - IndicatorHeight).coerceAtLeast(0.dp) / 2
    return indicatorInset to indicatorInset * 2
}

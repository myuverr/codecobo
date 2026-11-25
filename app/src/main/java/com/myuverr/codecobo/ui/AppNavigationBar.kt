// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.NavigationGlyph
import kotlinx.coroutines.flow.Flow

internal enum class TopLevelPage(val title: Int, val icon: Int, val selectedIcon: Int, val motion: Int) {
    CONTROL(R.string.home_nav_control, R.drawable.ic_nav_home, R.drawable.ic_nav_home_selected, R.raw.nav_house_motion),
    HEADPHONES(R.string.home_nav_headphones, R.drawable.ic_nav_library, R.drawable.ic_nav_library_selected, R.raw.nav_books_motion),
    SETTINGS(R.string.home_nav_settings, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_selected, R.raw.nav_gear_six_motion),
}

/** Floating tab capsule with one contextual action beside it. */
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Surface(
            Modifier.weight(1f, fill = false).widthIn(max = 256.dp).testTag("navigation-capsule"),
            shape = CircleShape,
            color = ShortNavigationBarDefaults.containerColor,
            shadowElevation = 1.dp,
        ) {
            // The capsule owns the surface, so the bar must not paint another one.
            ShortNavigationBar(containerColor = Color.Transparent, windowInsets = WindowInsets(0, 0, 0, 0)) {
                for (page in TopLevelPage.entries) {
                    val pageSelected = selected == page
                    ShortNavigationBarItem(
                        selected = pageSelected,
                        onClick = { if (!pageSelected) onSelected(page) },
                        icon = { NavigationItemGlyph(page, pageSelected, motion) },
                        label = { Text(stringResource(page.title)) },
                        iconPosition = NavigationItemIconPosition.Start,
                        interactionSource = remember { PressFreeInteractionSource() },
                        modifier = Modifier.testTag("navigation-" + page.name),
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

/** The glyph stays composed so its draw-in and selection pulse keep running while hidden. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavigationItemGlyph(page: TopLevelPage, selected: Boolean, motion: MotionScheme) {
    val width by animateDpAsState(
        targetValue = if (selected) GlyphSize else 0.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "navigation-glyph-width",
    )
    Box(Modifier.width(width.coerceIn(0.dp, GlyphSize)).height(GlyphSize).clipToBounds()) {
        NavigationGlyph(page.icon, page.selectedIcon, page.motion, selected, LocalContentColor.current,
            Modifier.size(GlyphSize).navigationPulse(selected).testTag("navigation-glyph-" + page.name))
    }
}

private val GlyphSize = 22.dp

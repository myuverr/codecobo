// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import com.myuverr.codecobo.ui.components.NavigationGlyph
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.flow.filterNot
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalLayoutDirection
import com.myuverr.codecobo.ui.components.FloatingOverlayState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.adaptiveSp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.theme.CodecTheme

class MainNavigation(view: ComposeView, private val actions: Actions,
    private val overlay: FloatingOverlayState) {
    enum class Page(val title: Int, val icon: Int, val selectedIcon: Int, val motion: Int) {
        CONTROL(R.string.home_nav_control, R.drawable.ic_nav_home, R.drawable.ic_nav_home_selected, R.raw.nav_house_motion),
        HEADPHONES(R.string.home_nav_headphones, R.drawable.ic_nav_library, R.drawable.ic_nav_library_selected, R.raw.nav_books_motion),
        SETTINGS(R.string.home_nav_settings, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_selected, R.raw.nav_gear_six_motion),
    }
    fun interface Actions { fun select(page: Page) }
    var selectedPage by mutableStateOf(Page.CONTROL)
        private set

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent { CodecTheme {
            AppNavigationBar(selectedPage, Modifier.onSizeChanged { overlay.navigationMeasured(it.height) }) { actions.select(it) }
        } }
    }

    fun show(page: Page) { selectedPage = page }
}

@Composable
internal fun AppNavigationBar(selected: MainNavigation.Page, modifier: Modifier = Modifier, onSelected: (MainNavigation.Page) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // MainActivity owns system-bar and cutout insets.
    BoxWithConstraints(
        modifier.widthIn(max = 256.dp).fillMaxWidth().testTag("navigation-viewport"),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val labelStyle = LocalTextStyle.current.copy(fontSize = 10.adaptiveSp, lineHeight = 15.adaptiveSp)
        val titles = MainNavigation.Page.entries.map { stringResource(it.title) }
        // Use the narrowest weighted slot so wrapped labels fit every item.
        val textWidth = with(density) {
            ((maxWidth.roundToPx() - 4.dp.roundToPx() * 4) / 3 - 6.dp.roundToPx() * 2).coerceAtLeast(1)
        }
        val labelHeight = remember(measurer, labelStyle, titles, selected, textWidth) {
            titles.mapIndexed { index, title ->
                measurer.measure(title,
                    style = labelStyle.copy(fontWeight = if (MainNavigation.Page.entries[index] == selected) FontWeight.Bold else FontWeight.Normal),
                    constraints = Constraints(maxWidth = textWidth)).size.height
            }.max()
        }
        val height = with(density) {
            (maxOf(48.dp.roundToPx(), 22.dp.roundToPx() + 3.dp.roundToPx() + labelHeight + 4.dp.roundToPx() * 2) +
                4.dp.roundToPx() * 2).toDp()
        }
        val direction = LocalLayoutDirection.current
        val slots = remember(density, direction) { mutableStateMapOf<MainNavigation.Page, Rect>() }
        var rowSize by remember { mutableStateOf(IntSize.Zero) }
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.widthIn(max = 256.dp).fillMaxWidth().height(height).testTag("navigation-capsule"),
            contentPadding = PaddingValues(4.dp),
            expandedShadowElevation = 1.dp,
            collapsedShadowElevation = 1.dp,
        ) {
            Box(Modifier.fillMaxSize().onSizeChanged { rowSize = it }) {
                NavigationSelectionIndicator(slots[selected], rowSize)
                Row(
                    Modifier.fillMaxSize().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for ((index, page) in MainNavigation.Page.entries.withIndex()) {
                        val isSelected = selected == page
                        val iconTint by animateColorAsState(if (isSelected) colors.primary else colors.onSurfaceVariant,
                            MotionScheme.expressive().fastEffectsSpec(), label = "navigation-icon-tint")
                        val tabShape = remember { RoundedCornerShape(percent = 50) }
                        // Press-disabled ripple can still paint SDK35 sparkles. Filter its press
                        // input while retaining selectable's gesture and focus handling.
                        val interaction = remember { MutableInteractionSource() }
                        val visualInteraction = remember(interaction) {
                            object : InteractionSource {
                                override val interactions = interaction.interactions.filterNot { it is PressInteraction }
                            }
                        }
                        val tabIndication = remember(tabShape) {
                            ripple(focusRingShape = tabShape, enablePressIndication = false)
                        }
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight().testTag("navigation-" + page.name)
                                .onGloballyPositioned { coordinates ->
                                    slots[page] = Rect(coordinates.positionInParent(), androidx.compose.ui.geometry.Size(
                                        coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                                }
                                .minimumInteractiveComponentSize()
                                .clip(tabShape)
                                .indication(visualInteraction, tabIndication)
                                .selectable(selected = isSelected, interactionSource = interaction,
                                    indication = null, role = Role.Tab,
                                    onClick = { if (!isSelected) onSelected(page) }),
                            shape = tabShape,
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = colors.onSurfaceVariant,
                        ) {
                            Column(
                                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                NavigationPulsingGlyph(page, isSelected) { motion ->
                                    NavigationGlyph(page.icon, page.selectedIcon, page.motion, isSelected, iconTint, Modifier.size(22.dp).then(motion))
                                }
                                Text(
                                    titles[index],
                                    style = labelStyle,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

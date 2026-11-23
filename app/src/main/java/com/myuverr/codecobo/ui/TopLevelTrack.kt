// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import kotlin.math.roundToInt

@Composable
internal fun TopLevelTrack(
    selected: TopLevelPage?,
    position: State<Float>,
    content: @Composable (TopLevelPage) -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            for (page in TopLevelPage.entries) {
                val active = page == selected
                // A pane passing through the viewport is visible, not interactive.
                val interaction = if (active) Modifier else Modifier
                    .clearAndSetSemantics { hideFromAccessibility() }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                Box(interaction.focusProperties {
                    canFocus = active
                    onEnter = { if (!active) cancelFocusChange() }
                }.focusGroup()) { content(page) }
            }
        },
    ) { measurables, constraints ->
        val panes = measurables.map { it.measure(constraints) }
        val width = constraints.maxWidth
        layout(width, constraints.maxHeight) {
            val offset = (position.value * width).roundToInt()
            panes.forEachIndexed { index, pane -> pane.placeRelative(index * width - offset, 0) }
        }
    }
}

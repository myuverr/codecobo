// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.myuverr.codecobo.ui.components.FloatingOverlayState
import com.myuverr.codecobo.ui.library.DeviceKey
import com.myuverr.codecobo.ui.settings.AssetTextScreen
import com.myuverr.codecobo.ui.settings.SettingsRoute
import com.myuverr.codecobo.ui.settings.ShippedLicenses
import com.myuverr.codecobo.ui.theme.CodecTheme
import kotlinx.serialization.Serializable

@Serializable
internal data object MainTabs : NavKey

@Serializable
private data class LicenseText(val file: String) : NavKey

@Composable
internal fun AppNavigation(
    home: HomePanel,
    library: LibraryPanel,
    settings: SettingsPanel,
    refreshManagement: () -> Unit,
    edit: (String) -> Boolean,
) {
    val backStack = rememberNavBackStack(MainTabs)
    var selectedTab by rememberSaveable { mutableStateOf(TopLevelPage.CONTROL) }
    val currentRoute = backStack.last()
    val showingTabs = currentRoute == MainTabs
    val overlay = remember { FloatingOverlayState() }

    fun goBack() {
        // Two back events can arrive before NavDisplay observes that the root is active.
        if (backStack.size == 1) return
        if (backStack.last() is DeviceKey) library.clearFeedback()
        backStack.removeAt(backStack.lastIndex)
    }

    val activeSection = if (currentRoute is DeviceKey) TopLevelPage.HEADPHONES else selectedTab
    LaunchedEffect(activeSection) {
        if (activeSection == TopLevelPage.SETTINGS) settings.refreshLanguageFacts()
        if (activeSection != TopLevelPage.CONTROL) refreshManagement()
    }

    val settingsPage: @Composable (SettingsRoute) -> Unit = { route ->
        settings.Content(
            route,
            onBack = ::goBack,
            onNavigate = { backStack.add(it) },
            onOpenRecord = { address, id -> backStack.add(DeviceKey(address, id)) },
            onOpenLicense = { backStack.add(LicenseText(it)) },
        )
    }

    CodecTheme(navigationOverlay = overlay.takeIf { showingTabs }) {
        val maxTabPosition = TopLevelPage.entries.lastIndex.toFloat()
        val animatedPosition = animateFloatAsState(
            selectedTab.ordinal.toFloat(),
            animationSpec = if (showingTabs) {
                // Keep pace with the capsule's initial travel without overshooting a page.
                spring(stiffness = 1600f, visibilityThreshold = 0.001f)
            } else snap(),
            label = "tab-strip-position",
        )
        val tabPosition = remember(animatedPosition, maxTabPosition) {
            derivedStateOf { animatedPosition.value.coerceIn(0f, maxTabPosition) }
        }

        val outerInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        Box(Modifier.fillMaxSize().windowInsetsPadding(outerInsets)) {
            val entries = rememberDecoratedNavEntries(
                backStack,
                listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider {
                    entry(MainTabs) {
                        Box(Modifier.fillMaxSize()) {
                            TopLevelTrack(selectedTab.takeIf { showingTabs }, tabPosition) { page ->
                                when (page) {
                                    TopLevelPage.CONTROL -> home.Content {
                                        selectedTab = TopLevelPage.SETTINGS
                                        backStack.add(SettingsRoute.ACCESS)
                                    }
                                    TopLevelPage.HEADPHONES -> library.Root { backStack.add(it) }
                                    TopLevelPage.SETTINGS -> settingsPage(SettingsRoute.ROOT)
                                }
                            }
                            if (showingTabs) {
                                val showAction = selectedTab == TopLevelPage.CONTROL && home.canShowEditor
                                AppNavigationBar(
                                    selected = selectedTab,
                                    showAction = showAction,
                                    action = { home.PrimaryAction(showAction) },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                                        .onSizeChanged { overlay.navigationMeasured(it.height) },
                                    onSelected = { selectedTab = it },
                                )
                            }
                        }
                    }
                    entry<DeviceKey> { device ->
                        library.Details(device, onBack = ::goBack) { address ->
                            if (edit(address)) {
                                selectedTab = TopLevelPage.CONTROL
                                backStack.subList(1, backStack.size).clear()
                            }
                        }
                    }
                    entry<SettingsRoute> { settingsPage(it) }
                    entry<LicenseText> { license ->
                        AssetTextScreen("licenses/${license.file}", ShippedLicenses.getValue(license.file), ::goBack)
                    }
                },
            )
            ChildNavigationDisplay(entries, ::goBack, Modifier.fillMaxSize().clipToBounds())
        }
    }
}

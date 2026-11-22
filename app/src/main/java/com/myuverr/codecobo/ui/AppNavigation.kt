// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.myuverr.codecobo.ui.components.FloatingOverlayState
import com.myuverr.codecobo.ui.library.DeviceKey
import com.myuverr.codecobo.ui.settings.AssetTextScreen
import com.myuverr.codecobo.ui.settings.SettingsRoute
import com.myuverr.codecobo.ui.settings.ShippedLicenses
import com.myuverr.codecobo.ui.theme.CodecTheme
import kotlinx.serialization.Serializable

@Serializable
internal data object Control : NavKey

@Serializable
internal data object Library : NavKey

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
    val backStack = rememberNavBackStack(Control)
    val currentRoute = backStack.last()
    val selectedTab = TopLevelPage.entries.firstOrNull { it.route == currentRoute }
    val overlay = remember { FloatingOverlayState() }

    fun replaceStack(vararg routes: NavKey) {
        backStack.clear()
        backStack.addAll(routes)
    }

    fun goBack() {
        // Two back events can arrive before NavDisplay observes that the root is active.
        if (backStack.size == 1) return
        if (backStack.last() is DeviceKey) library.clearFeedback()
        backStack.removeAt(backStack.lastIndex)
    }

    val activeSection = if (currentRoute is DeviceKey) Library else backStack.first()
    LaunchedEffect(activeSection) {
        if (activeSection == SettingsRoute.ROOT) settings.refreshLanguageFacts()
        if (activeSection != Control) refreshManagement()
    }

    CodecTheme(navigationOverlay = overlay.takeIf { selectedTab != null }) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = ::goBack,
                entryProvider = entryProvider {
                    entry(Control) {
                        home.Content {
                            replaceStack(SettingsRoute.ROOT, SettingsRoute.ACCESS)
                        }
                    }
                    entry(Library) {
                        library.Root { backStack.add(it) }
                    }
                    entry<DeviceKey> { device ->
                        library.Details(device, onBack = ::goBack) { address ->
                            if (edit(address)) replaceStack(Control)
                        }
                    }
                    entry<SettingsRoute> { page ->
                        settings.Content(
                            page,
                            onBack = ::goBack,
                            onNavigate = { backStack.add(it) },
                            onOpenRecord = { address, id -> backStack.add(DeviceKey(address, id)) },
                            onOpenLicense = { backStack.add(LicenseText(it)) },
                        )
                    }
                    entry<LicenseText> { license ->
                        AssetTextScreen("licenses/${license.file}", ShippedLicenses.getValue(license.file), ::goBack)
                    }
                },
            )
            if (selectedTab != null) {
                AppNavigationBar(
                    selectedTab,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                        .onSizeChanged { overlay.navigationMeasured(it.height) },
                    onSelected = { replaceStack(it.route) },
                )
            }
        }
    }
}

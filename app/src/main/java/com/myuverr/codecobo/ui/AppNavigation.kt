// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.FloatingOverlayState
import com.myuverr.codecobo.ui.home.EditSavedResult
import com.myuverr.codecobo.ui.home.HomeActions
import com.myuverr.codecobo.ui.home.HomeScreen
import com.myuverr.codecobo.ui.home.HomeSubmission
import com.myuverr.codecobo.ui.home.HomeViewModel
import com.myuverr.codecobo.ui.home.SubmitTarget
import com.myuverr.codecobo.ui.library.DeviceKey
import com.myuverr.codecobo.ui.management.ManagementSnapshot
import com.myuverr.codecobo.ui.settings.AssetTextScreen
import com.myuverr.codecobo.ui.settings.LicenseDocumentKey
import com.myuverr.codecobo.ui.settings.SettingsRoute
import com.myuverr.codecobo.ui.settings.licenseDocument
import com.myuverr.codecobo.ui.theme.CodecTheme
import kotlinx.serialization.Serializable

@Serializable
internal data object MainTabs : NavKey

/**
 * The capsule's spatial spring, critically damped so a slide that starts from rest cannot overshoot,
 * with a thousandth-of-a-page threshold so the strip lands flush. A slide retargeted mid-flight
 * keeps its velocity and may briefly run past its target; only the two ends are clamped.
 * [MotionScheme.fastSpatialSpec] is typed as a plain spec, hence the cast to read its stiffness.
 */
internal val MotionScheme.slideSpec: SpringSpec<Float>
    get() = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = (fastSpatialSpec<Float>() as SpringSpec<Float>).stiffness,
        visibilityThreshold = 0.001f,
    )

@Composable
internal fun AppNavigation(
    home: HomeViewModel,
    library: LibraryPanel,
    settings: SettingsPanel,
    management: ManagementSnapshot,
    refreshManagement: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onSubmit: (HomeSubmission) -> Unit,
    onOpenBluetooth: () -> Unit,
) {
    val backStack = rememberNavBackStack(MainTabs)
    var selectedTab by rememberSaveable { mutableStateOf(TopLevelPage.CONTROL) }
    val currentRoute = backStack.last()
    val showingTabs = currentRoute == MainTabs
    val overlay = remember { FloatingOverlayState() }
    val homeActions = remember(home, backStack, onSelectDevice, onOpenBluetooth) {
        HomeActions(
            onDeviceSelected = { address -> if (home.selectDevice(address)) onSelectDevice(address) },
            onParameterSelected = home::selectParameter,
            onOpenBluetooth = onOpenBluetooth,
            onOpenSettings = {
                selectedTab = TopLevelPage.SETTINGS
                backStack.add(SettingsRoute.ACCESS)
            },
            onRestoreChanged = home::setRestore,
        )
    }

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
            management.data,
            onBack = ::goBack,
            onNavigate = { backStack.add(it) },
            onOpenRecord = { address, id -> backStack.add(DeviceKey(address, id)) },
            onOpenLicense = { backStack.add(LicenseDocumentKey(it.file)) },
        )
    }

    CodecTheme(navigationOverlay = overlay.takeIf { showingTabs }) {
        val tabPosition = animateFloatAsState(
            selectedTab.ordinal.toFloat(),
            animationSpec = if (showingTabs) MaterialTheme.motionScheme.slideSpec else snap(),
            label = "tab-strip-position",
        )

        val outerInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        Box(Modifier.fillMaxSize().windowInsetsPadding(outerInsets)) {
            val entries = rememberDecoratedNavEntries(
                backStack,
                listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider {
                    entry(MainTabs) {
                        val homeState = home.state
                        Box(Modifier.fillMaxSize()) {
                            TopLevelTrack(selectedTab.takeIf { showingTabs }, tabPosition) { page ->
                                when (page) {
                                    TopLevelPage.CONTROL -> HomeScreen(homeState, homeActions)
                                    TopLevelPage.HEADPHONES -> library.Root(management.data) { backStack.add(it) }
                                    TopLevelPage.SETTINGS -> settingsPage(SettingsRoute.ROOT)
                                }
                            }
                            if (showingTabs) {
                                val showAction = selectedTab == TopLevelPage.CONTROL && homeState.canShowEditor
                                AppNavigationBar(
                                    selected = selectedTab,
                                    showAction = showAction,
                                    action = {
                                        // A different device starts with a fresh save animation.
                                        key(homeState.selectedAddress, homeState.editor.deviceAddress) {
                                            SubmitTarget(homeState, { home.submission()?.let(onSubmit) }, showAction)
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                                        .onSizeChanged { overlay.navigationMeasured(it.height) },
                                    onSelected = { selectedTab = it },
                                )
                            }
                        }
                    }
                    entry<DeviceKey> { device ->
                        val connectionChanged = stringResource(R.string.library_connection_changed)
                        library.Details(device, management.data, onBack = ::goBack) { address ->
                            when (val result = home.editSaved(address)) {
                                is EditSavedResult.OpenHome -> {
                                    result.deviceToSelect?.let(onSelectDevice)
                                    selectedTab = TopLevelPage.CONTROL
                                    backStack.subList(1, backStack.size).clear()
                                }
                                EditSavedResult.Disconnected -> library.showError(connectionChanged)
                                EditSavedResult.Ignored -> Unit
                            }
                        }
                    }
                    entry<SettingsRoute> { settingsPage(it) }
                    entry<LicenseDocumentKey> { license ->
                        val document = licenseDocument(license.file)
                        AssetTextScreen(document.assetPath, document.title, ::goBack)
                    }
                },
            )
            ChildNavigationDisplay(entries, ::goBack, Modifier.fillMaxSize().clipToBounds())
        }
    }
}

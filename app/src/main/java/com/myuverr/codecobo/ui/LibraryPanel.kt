// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.library.*
import com.myuverr.codecobo.ui.management.ManagementData
import com.myuverr.codecobo.ui.theme.CodecTheme
import kotlinx.coroutines.launch

class LibraryPanel(private val context: Context, private val actions: Actions,
    private val navigationOverlay: FloatingOverlayState) {
    interface Actions {
        fun enabled(address: String, enabled: Boolean)
        fun paused(paused: Boolean)
        fun edit(address: String)
        fun remove(address: String, revision: Long)
        fun returnToRecords()
    }
    private var state by mutableStateOf(LibraryState())
    private var selected by mutableStateOf<DeviceKey?>(null)
    private var returnToRecords by mutableStateOf(false)
    private var pageActive by mutableStateOf(false)
    private var animateRoute by mutableStateOf(false)
    private var deleting by mutableStateOf<DeviceProfile?>(null)
    private var awaitingResult = false
    private var localError by mutableStateOf<String?>(null)
    private val root = ComposeView(context).apply {
        id = R.id.library_compose
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { CodecTheme(navigationOverlay = navigationOverlay) { LibraryRoutes() } }
    }

    fun view(): View = root
    fun render(connection: ScreenState, restorePaused: Boolean) {
        if (awaitingResult && !connection.busy()) {
            localError = when (connection.operationFailure()) {
                ScreenState.OperationFailure.PROFILE_CHANGED -> context.getString(R.string.library_changed)
                ScreenState.OperationFailure.PROFILE -> context.getString(R.string.notice_profile_operation_failed)
                ScreenState.OperationFailure.RESTORE -> context.getString(R.string.notice_profile_restore_failed)
                else -> null
            }
            awaitingResult = false
        }
        state = state.copy(connection = connection, data = state.data.copy(restorePaused = restorePaused))
    }
    fun renderData(data: ManagementData) {
        // Audio rendering supplies the current preference; metadata may have started before that write.
        val paused = if (state.connection == null) data.restorePaused else state.data.restorePaused
        state = state.copy(data = data.copy(restorePaused = paused))
    }
    fun setActive(value: Boolean) { pageActive = value; if (!value) deleting = null }
    fun showRoot() { animateRoute = pageActive; selected = null; returnToRecords = false; deleting = null }
    fun openRecord(address: String?, id: Int) { animateRoute = false; selected = DeviceKey(address, id); returnToRecords = true }
    private fun back() {
        awaitingResult = false
        localError = null
        if (returnToRecords) {
            // Keep the outgoing detail intact while its parent page animates away.
            actions.returnToRecords()
        } else {
            animateRoute = true
            selected = null
        }
    }
    fun showError(message: String) { localError = message }

    fun save(bundle: Bundle) {
        bundle.remove("library.association")
        bundle.putBoolean("library.returnToRecords", returnToRecords)
        bundle.putString("library.address", selected?.address)
        selected?.associationId?.let { bundle.putInt("library.association", it) }
    }
    fun restore(bundle: Bundle) {
        returnToRecords = bundle.getBoolean("library.returnToRecords")
        val address = bundle.getString("library.address")
        val id = if (bundle.containsKey("library.association")) bundle.getInt("library.association") else null
        selected = if (address != null || id != null) DeviceKey(address, id) else null
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LibraryRoutes() {
        BackHandler(pageActive && selected != null) { back() }
        val routeKey = selected?.let { it.address.orEmpty() + "|" + (it.associationId?.toString() ?: "") } ?: "library-root"
        val feedbackClearance = LocalFloatingNavigationClearance.current
        Column {
            ChildPageMotion(routeKey, if (selected == null) 0 else 1, Modifier.weight(1f), enabled = animateRoute && pageActive) { shown ->
                val key = if (shown == "library-root") null else DeviceKey(
                    shown.substringBefore('|').ifBlank { null }, shown.substringAfter('|').toIntOrNull())
                val detail = key?.let(state::detail)
                if (key == null) {
                    LibraryScreen(
                        state,
                        onOpen = { animateRoute = true; selected = it; awaitingResult = false; localError = null },
                        onRestore = { address, enabled -> command { actions.enabled(address, enabled) } },
                        onPaused = { paused -> command { actions.paused(paused) } },
                    )
                } else if (detail == null) {
                    PageLayout(stringResource(R.string.home_nav_headphones), onBack = ::back) {
                        if (state.data.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else PageNote(if (state.data.readError != null) context.getString(R.string.notice_profile_read_failed) else stringResource(R.string.library_gone))
                    }
                } else key(key) {
                    DeviceDetailsScreen(
                        state, detail,
                        onBack = ::back,
                        onRestore = { address, enabled -> command { actions.enabled(address, enabled) } },
                        onEdit = { actions.edit(it) },
                        onDelete = { deleting = detail.saved },
                    )
                }
            }
            localError?.let { PageNote(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = feedbackClearance)) }

        }
        deleting?.let { requested ->
            val current = state.data.profiles.find { it.address() == requested.address() }
            val unchanged = current?.revision() == requested.revision()
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
            val scope = rememberCoroutineScope()
            ModalBottomSheet(sheetState = sheetState, onDismissRequest = { deleting = null }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.library_delete_question), style = MaterialTheme.typography.titleLarge)
                    PageNote(if (unchanged) stringResource(R.string.profile_delete_explanation) else stringResource(R.string.library_changed))
                    Button(
                        {
                            deleting = null
                            command { actions.remove(requested.address(), requested.revision()) }
                        },
                        Modifier.fillMaxWidth().testTag("confirm-delete"),
                        enabled = unchanged && state.connection?.busy() != true,
                    ) { Text(stringResource(R.string.profile_delete)) }
                    TextButton({ scope.launch { sheetState.hide(); deleting = null } }, Modifier.fillMaxWidth()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }

    private inline fun command(action: () -> Unit) {
        if (state.connection?.busy() == true) return
        awaitingResult = true
        localError = null
        action()
    }
}

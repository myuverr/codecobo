// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.ui.components.LocalFloatingNavigationClearance
import com.myuverr.codecobo.ui.components.PageLayout
import com.myuverr.codecobo.ui.components.PageNote
import com.myuverr.codecobo.ui.library.*
import com.myuverr.codecobo.ui.management.ManagementData
import kotlinx.coroutines.launch

internal class LibraryPanel(private val context: Context, private val actions: Actions) {
    interface Actions {
        fun enabled(address: String, enabled: Boolean)
        fun paused(paused: Boolean)
        fun remove(address: String, revision: Long)
    }

    private var state by mutableStateOf(LibraryState())
    private var awaitingResult = false
    private var localError by mutableStateOf<String?>(null)

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

    fun showError(message: String) { localError = message }

    fun clearFeedback() {
        awaitingResult = false
        localError = null
    }

    @Composable
    fun Root(onOpen: (DeviceKey) -> Unit) {
        WithFeedback {
            LibraryScreen(
                state,
                onOpen = { device ->
                    clearFeedback()
                    onOpen(device)
                },
                onRestore = { address, enabled -> command { actions.enabled(address, enabled) } },
                onPaused = { paused -> command { actions.paused(paused) } },
            )
        }
    }

    @Composable
    fun Details(device: DeviceKey, onBack: () -> Unit, onEdit: (String) -> Unit) {
        val detail = state.detail(device)
        var deleting by remember { mutableStateOf<DeviceProfile?>(null) }
        WithFeedback {
            if (detail == null) {
                PageLayout(stringResource(R.string.home_nav_headphones), onBack = onBack) {
                    when {
                        state.data.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.data.readError != null -> PageNote(stringResource(R.string.notice_profile_read_failed))
                        else -> PageNote(stringResource(R.string.library_gone))
                    }
                }
            } else {
                DeviceDetailsScreen(
                    state, detail,
                    onBack = onBack,
                    onRestore = { address, enabled -> command { actions.enabled(address, enabled) } },
                    onEdit = onEdit,
                    onDelete = { deleting = detail.saved },
                )
            }
        }
        deleting?.let { requested ->
            DeleteSheet(requested, onDismiss = { deleting = null }) {
                deleting = null
                command { actions.remove(requested.address(), requested.revision()) }
            }
        }
    }

    @Composable
    private fun WithFeedback(content: @Composable () -> Unit) {
        val clearance = LocalFloatingNavigationClearance.current
        Column {
            Box(Modifier.weight(1f)) { content() }
            localError?.let {
                PageNote(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = clearance))
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DeleteSheet(requested: DeviceProfile, onDismiss: () -> Unit, onConfirm: () -> Unit) {
        val current = state.data.profiles.find { it.address() == requested.address() }
        val unchanged = current?.revision() == requested.revision()
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        val scope = rememberCoroutineScope()
        val colors = MaterialTheme.colorScheme
        ModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.library_delete_question), style = MaterialTheme.typography.titleLarge)
                PageNote(stringResource(if (unchanged) R.string.profile_delete_explanation else R.string.library_changed))
                Button(
                    onConfirm, Modifier.fillMaxWidth().testTag("confirm-delete"),
                    enabled = unchanged && state.connection?.busy() != true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = colors.onError,
                    ),
                ) {
                    Icon(painterResource(R.drawable.ic_action_delete), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.profile_delete))
                }
                TextButton({ scope.launch { sheetState.hide(); onDismiss() } }, Modifier.fillMaxWidth()) {
                    Text(stringResource(android.R.string.cancel))
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

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.controller

import android.content.Context
import android.content.IntentSender
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.edit
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.ScreenState.AccessMode
import com.myuverr.codecobo.controller.ScreenState.OperationFailure
import com.myuverr.codecobo.core.ApplyEngine
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.CodecRules
import com.myuverr.codecobo.core.ManualSubmission
import com.myuverr.codecobo.core.OperationGate
import com.myuverr.codecobo.core.SaveFlow
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.core.WriteArbiter
import com.myuverr.codecobo.platform.BluetoothRepository
import com.myuverr.codecobo.platform.BluetoothRuntime
import com.myuverr.codecobo.platform.CompanionAssociations
import com.myuverr.codecobo.platform.DevicePresence
import com.myuverr.codecobo.platform.ProfileStore
import com.myuverr.codecobo.platform.RestoreService
import com.myuverr.codecobo.platform.ShizukuBridge
import java.util.concurrent.Executors
import java.util.function.Consumer

/** Owns connection and operation state on the main thread and Bluetooth work on a single worker. */
internal class CodecController(
    context: Context,
    restoredAddress: String?,
    private val listener: (ScreenState) -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val operations = OperationGate()
    private val reads = OperationGate()
    private val profileJobs = OperationGate()
    private val app = context.applicationContext
    private val preferences = context.getSharedPreferences("mode", Context.MODE_PRIVATE)
    private val profiles = ProfileStore(context)
    private val associations = CompanionAssociations(context)
    private val bluetoothLease = BluetoothRuntime.acquire(context, ::onBluetoothChanged)
    private val bluetooth = bluetoothLease.repository()
    private val shizuku = ShizukuBridge(context, ::onShizukuChanged)
    @Volatile private var saving: SaveTransaction? = null
    @Volatile private var closed = false
    private var devices = emptyList<BluetoothRepository.DeviceInfo>()
    private var selected = restoredAddress
    private var snapshot: BluetoothRepository.Snapshot? = null
    private var accessMode = if (preferences.getBoolean(ShizukuModeKey, false) && shizuku.ready())
        AccessMode.SHIZUKU else AccessMode.SYSTEM
    private var operationFailure = OperationFailure.NONE
    private var retainOutcome = false
    private var profileChanging = false
    private var submissionFeedback: SubmissionFeedback? = null
    private var editorRevision = 0L
    private var observedAutomaticRestore = bluetooth.automaticRestoreGeneration(restoredAddress)
    private var messageId = R.string.controller_reading
    private var detail = ""

    var deviceListKnown = false
        private set

    init {
        preferences.edit { putBoolean(ShizukuModeKey, accessMode.requested()) }
    }

    private fun onBluetoothChanged() {
        if (closed) return
        val transaction = saving
        if (transaction != null && bluetooth.connectionGeneration() != transaction.connectionGeneration) {
            failSave(transaction, R.string.controller_connection_changed, "")
        }
        if (saving == null) refresh()
    }

    private fun onShizukuChanged() {
        if (closed) return
        if (accessMode == AccessMode.SHIZUKU && !shizuku.ready()) failAuthorization(shizuku.status())
        else emit()
    }

    fun state() = ScreenState(
        devices, selected, snapshot, saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging,
        bluetooth.hasPermission(), accessMode, shizuku.ready(), messageId, operationFailure, detail,
        editorRevision, saving != null, submissionFeedback,
    )

    private fun emit() {
        if (!closed) listener(state())
    }

    private fun clearWait(transaction: SaveTransaction) {
        transaction.waitingSystem = false
        transaction.timeout?.let(main::removeCallbacks)
        transaction.timeout = null
    }

    private fun releaseSave() {
        val transaction = saving
        saving = null
        if (transaction != null) {
            clearWait(transaction)
            transaction.pending = null
            transaction.notice = null
            transaction.lease.close()
        }
    }

    private fun invalidateSave() {
        submissionFeedback = null
        operations.invalidate()
        if (saving?.writeStarted == true) {
            editorRevision++
            snapshot = null
        }
        releaseSave()
    }

    fun select(address: String) {
        if (selected == address) return
        invalidateSave()
        reads.invalidate()
        selected = address
        observedAutomaticRestore = bluetooth.automaticRestoreGeneration(address)
        snapshot = null
        retainOutcome = false
        refresh()
    }

    fun setShizukuMode(enabled: Boolean) {
        if (closed || enabled && accessMode.requested() &&
            (accessMode == AccessMode.AUTHORIZING || shizuku.ready())) return
        invalidateSave()
        reads.invalidate()
        cancelProfileWork()
        WriteArbiter.SHARED.cancelAutomatic()
        shizuku.cancelPermissionRequest()
        preferences.edit { putBoolean(ShizukuModeKey, false) }
        accessMode = if (enabled) AccessMode.AUTHORIZING else AccessMode.SYSTEM
        operationFailure = OperationFailure.NONE
        detail = ""
        retainOutcome = false
        if (!enabled) {
            shizuku.releaseService()
            messageId = R.string.controller_system_mode_selected
            emit()
            refresh()
            return
        }
        messageId = R.string.controller_authorizing
        emit()
        shizuku.requestPermission { granted ->
            if (closed || accessMode != AccessMode.AUTHORIZING) return@requestPermission
            if (!granted || !shizuku.ready()) {
                failAuthorization(shizuku.permissionError())
                return@requestPermission
            }
            accessMode = AccessMode.SHIZUKU
            preferences.edit { putBoolean(ShizukuModeKey, true) }
            messageId = R.string.controller_authorized
            detail = "Bluetooth reads and writes still run in the app"
            emit()
            refresh()
        }
    }

    private fun failAuthorization(reason: String) {
        invalidateSave()
        reads.invalidate()
        cancelProfileWork()
        accessMode = AccessMode.AUTHORIZATION_FAILED
        operationFailure = OperationFailure.NONE
        shizuku.cancelPermissionRequest()
        shizuku.releaseService()
        preferences.edit { putBoolean(ShizukuModeKey, false) }
        retainOutcome = true
        messageId = R.string.controller_authorization_failed
        detail = reason.ifEmpty { shizuku.status() } + "\nFell back to system access"
        emit()
    }

    fun refresh() {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging) return
        bluetooth.bind()
        val token = reads.issue()
        val requested = selected
        worker.execute {
            val connection = bluetooth.connectionGeneration()
            val result = readConnection(requested)
            main.post {
                if (closed || !reads.isCurrent(token) || connection != bluetooth.connectionGeneration()) return@post
                showConnection(result)
            }
        }
    }

    private data class ConnectionRead(
        val devicesKnown: Boolean,
        val devices: List<BluetoothRepository.DeviceInfo>,
        val address: String?,
        val snapshot: BluetoothRepository.Snapshot?,
        val automaticRestore: Long?,
        val error: String,
    )

    private fun readConnection(requested: String?): ConnectionRead {
        var known = false
        var found = emptyList<BluetoothRepository.DeviceInfo>()
        var address: String? = null
        var value: BluetoothRepository.Snapshot? = null
        var automaticRestore: Long? = null
        val error = try {
            // A disabled radio is conclusive; a missing proxy or permission is not.
            known = bluetooth.hasPermission() && !bluetooth.enabled()
            found = bluetooth.devices()
            known = true
            address = found.firstOrNull { it.address() == requested }?.address()
                ?: (found.firstOrNull { it.active() } ?: found.singleOrNull())?.address()
            if (address != null) {
                automaticRestore = bluetooth.automaticRestoreGeneration(address)
                value = bluetooth.read(address)
            }
            ""
        } catch (error: Exception) {
            describe(error)
        } catch (error: LinkageError) {
            describe(error)
        }
        return ConnectionRead(known, found, address, value, automaticRestore, error)
    }

    private fun showConnection(result: ConnectionRead) {
        val authorizationFailed = accessMode == AccessMode.AUTHORIZATION_FAILED
        if (result.devicesKnown) {
            if (selected != result.address) {
                invalidateSave()
                observedAutomaticRestore = result.automaticRestore
                if (!authorizationFailed) {
                    retainOutcome = false
                    messageId = R.string.controller_reading
                    detail = ""
                }
            } else if (result.automaticRestore != null && observedAutomaticRestore != result.automaticRestore) {
                observedAutomaticRestore = result.automaticRestore
                editorRevision++
            }
            selected = result.address
        }
        deviceListKnown = result.devicesKnown
        devices = result.devices
        snapshot = result.snapshot
        if (!authorizationFailed) {
            when {
                result.error.isNotEmpty() -> {
                    // Preserve the completed result when a later refresh fails.
                    if (!retainOutcome) {
                        messageId = R.string.controller_read_failed
                        detail = result.error
                    } else if (result.error !in detail) {
                        detail += "\nRead details: ${result.error}"
                    }
                }
                devices.isEmpty() -> {
                    messageId = R.string.controller_disconnected
                    detail = ""
                }
                selected == null -> {
                    messageId = R.string.controller_select_device
                    detail = ""
                }
                messageId == R.string.controller_reading || messageId == R.string.controller_read_failed ||
                    messageId == R.string.controller_disconnected -> {
                    messageId = R.string.controller_read_complete
                    detail = ""
                }
            }
        }
        emit()
    }

    fun submit(request: CodecRequest, intent: SubmissionIntent, enableRestore: Boolean,
        pending: Consumer<IntentSender>, completed: Consumer<String>) {
        if (closed || intent == SubmissionIntent.NONE) return
        val current = state()
        if (!current.canEdit() || !CodecRules.valid(request, current.snapshot().capabilities())) {
            messageId = R.string.controller_edit_unavailable
            emit()
            completed.accept(app.getString(messageId))
            return
        }
        beginSave(request, intent, enableRestore, pending, completed)
    }

    fun prepareEdit(pending: Consumer<IntentSender>, completed: Consumer<String>) {
        if (state().canPrepareEdit()) beginSave(null, SubmissionIntent.NONE, false, pending, completed)
    }

    private fun beginSave(request: CodecRequest?, intent: SubmissionIntent, enableRestore: Boolean,
        pending: Consumer<IntentSender>, completed: Consumer<String>) {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING) return
        reads.invalidate()
        val lease = WriteArbiter.SHARED.manual()
        // Manual work supersedes initialization, including a late automatic notification.
        observedAutomaticRestore = bluetooth.automaticRestoreGeneration(selected) ?: bluetooth.connectionGeneration()
        val transaction = SaveTransaction(operations.issue(), selected, bluetooth.connectionGeneration(),
            request, intent, enableRestore, lease, pending, completed)
        saving = transaction
        operationFailure = OperationFailure.NONE
        submissionFeedback = if (request == null || intent == SubmissionIntent.NONE) null else
            SubmissionFeedback(transaction.id, transaction.address, request, intent, SubmissionFeedback.Status.RUNNING)
        retainOutcome = false
        messageId = R.string.controller_checking_association
        detail = ""
        emit()
        val timeout = Runnable {
            if (!closed && saving == transaction && operations.isCurrent(transaction.id)) {
                failSave(transaction, R.string.controller_save_timeout, "")
            }
        }
        transaction.timeout = timeout
        main.postDelayed(timeout, 120_000)
        SaveFlow(request, { current(transaction) }, object : SaveFlow.Host {
            override fun inspect(result: Consumer<Boolean>) = inspectAssociation(transaction, result)
            override fun associate(success: Runnable) = requestAssociation(transaction, success)
            override fun write(request: CodecRequest) = runWrite(transaction)

            override fun prepared() {
                val notice = transaction.takeNotice()
                operations.invalidate()
                releaseSave()
                messageId = R.string.controller_edit_prepared
                emit()
                notice.accept(app.getString(R.string.controller_options_ready))
                refresh()
            }

            override fun failed() = failSave(transaction, R.string.controller_association_missing, detail)
        }).start()
    }

    private fun inspectAssociation(transaction: SaveTransaction, result: Consumer<Boolean>) {
        if (!current(transaction)) return
        worker.execute {
            var linked = false
            val failure = try {
                check(bluetooth.connected(transaction.address) &&
                    bluetooth.connectionGeneration() == transaction.connectionGeneration) { "Device connection changed" }
                linked = associations.associated(transaction.address)
                null
            } catch (error: Exception) {
                describe(error)
            } catch (error: LinkageError) {
                describe(error)
            }
            main.post {
                if (!current(transaction)) return@post
                if (failure != null) failSave(transaction, R.string.controller_preflight_failed, failure)
                else result.accept(linked)
            }
        }
    }

    private fun requestAssociation(transaction: SaveTransaction, success: Runnable) {
        if (!current(transaction)) return
        if (accessMode == AccessMode.SHIZUKU) {
            messageId = R.string.controller_associating_privileged
            emit()
            shizuku.associate(Process.myUid() / 100000, transaction.address) { output ->
                if (current(transaction)) {
                    detail = output
                    success.run()
                }
            }
        } else {
            messageId = R.string.controller_association_consent
            transaction.waitingSystem = true
            emit()
            try {
                associations.request(transaction.address,
                    { sender ->
                        if (current(transaction) && transaction.waitingSystem) transaction.showPending(sender)
                    },
                    {
                        if (current(transaction) && transaction.waitingSystem) {
                            transaction.waitingSystem = false
                            success.run()
                        }
                    },
                    { error ->
                        if (current(transaction)) failSave(transaction, R.string.controller_association_failed, error)
                    },
                )
            } catch (error: RuntimeException) {
                failSave(transaction, R.string.controller_association_start_failed, describe(error))
            }
        }
    }

    private fun current(transaction: SaveTransaction): Boolean =
        !closed && saving == transaction && transaction.lease.current() && operations.isCurrent(transaction.id) &&
            selected == transaction.address && bluetooth.connectionGeneration() == transaction.connectionGeneration

    /** RESULT_OK is not an association receipt; wait for onAssociationCreated. */
    fun associationActivityResult(ok: Boolean) {
        val transaction = saving
        if (!closed && transaction != null && transaction.waitingSystem && !ok) {
            failSave(transaction, R.string.controller_association_cancelled, "")
        }
    }

    private fun failSave(transaction: SaveTransaction, text: Int, error: String) {
        val notice = transaction.takeNotice()
        invalidateSave()
        retainOutcome = true
        messageId = text
        detail = error
        emit()
        notice.accept(app.getString(text))
        refresh()
    }

    private fun runWrite(transaction: SaveTransaction) {
        if (!current(transaction)) return
        clearWait(transaction)
        transaction.writeStarted = transaction.intent.applies()
        messageId = if (transaction.intent.applies()) R.string.controller_applying else R.string.controller_saving
        emit()
        val deviceName = devices.firstOrNull { it.address() == transaction.address }?.name() ?: transaction.address
        worker.execute {
            val result = executeSubmission(transaction, deviceName)
            main.post { finishSubmission(transaction, result) }
        }
    }

    private data class SubmissionResult(
        val outcome: ApplyEngine.Result?,
        val readback: BluetoothRepository.Snapshot?,
        val error: String?,
        val saved: Boolean,
        val profileNote: String,
    )

    private fun executeSubmission(transaction: SaveTransaction, deviceName: String): SubmissionResult {
        val address = transaction.address
        val request = transaction.request
        val intent = transaction.intent
        var error: String? = null
        val outcome = try {
            val device = bluetooth.operation(address) { current(transaction) }
            val guarded = object : ApplyEngine.Device {
                override fun connected() = current(transaction) && device.connected()
                override fun write(target: CodecRequest) {
                    check(current(transaction)) { "Save request is no longer current" }
                    device.write(target)
                }
                override fun read() = device.read()
            }
            ManualSubmission { Thread.sleep(500) }.execute(guarded, request, intent, operations, transaction.id)
        } catch (failure: Exception) {
            error = describe(failure)
            null
        } catch (failure: LinkageError) {
            error = describe(failure)
            null
        }
        val fresh = try {
            if (current(transaction)) bluetooth.read(address) else null
        } catch (failure: Exception) {
            if (error == null) error = describe(failure)
            null
        } catch (failure: LinkageError) {
            if (error == null) error = describe(failure)
            null
        }
        var profileNote = ""
        var saved = false
        if (current(transaction) && intent.saves() && outcome?.status() == ApplyEngine.Status.VERIFIED) {
            if (fresh != null && CodecRules.matches(request, fresh.actual())) {
                try {
                    saved = profiles.saveVerified(address, deviceName, fresh.codecName(), request,
                        fresh.actual(), transaction.enableRestore) { current(transaction) }
                    profileNote = if (saved) "\nConfiguration saved" else "\nInvalid configuration not saved"
                } catch (failure: RuntimeException) {
                    profileNote = "\nProfile commit failed: ${describe(failure)}"
                }
            } else {
                profileNote = "\nFinal output unavailable or changed; previous profile retained"
            }
        }
        return SubmissionResult(outcome, fresh, error, saved, profileNote)
    }

    private fun finishSubmission(transaction: SaveTransaction, result: SubmissionResult) {
        if (!current(transaction)) return
        val (outcome, fresh, failure, saved, profileNote) = result
        val request = transaction.request
        val intent = transaction.intent
        val readbackMatches = fresh != null && CodecRules.matches(request, fresh.actual())
        val verified = outcome?.status() == ApplyEngine.Status.VERIFIED && readbackMatches
        val notice = transaction.takeNotice()
        operations.invalidate()
        releaseSave()
        editorRevision++
        snapshot = fresh
        retainOutcome = true
        messageId = when {
            outcome == null -> R.string.controller_operation_failed
            !intent.applies() && !verified -> if (fresh != null && !readbackMatches) {
                R.string.controller_save_actual_changed
            } else {
                R.string.controller_save_unverified
            }
            else -> when (outcome.status()) {
                ApplyEngine.Status.VERIFIED -> when {
                    !verified -> R.string.controller_readback_changed
                    intent.applies() -> R.string.controller_apply_verified
                    saved -> R.string.controller_profile_saved
                    else -> R.string.controller_verified_not_saved
                }
                ApplyEngine.Status.DIFFERENT -> R.string.controller_different
                ApplyEngine.Status.UNVERIFIED -> R.string.controller_unverified
                ApplyEngine.Status.CANCELLED -> R.string.controller_cancelled
            }
        }
        detail = if (outcome == null) failure.orEmpty()
            else outcome.detail() + failure?.let { "\nCurrent state read: $it" }.orEmpty() + profileNote
        submissionFeedback?.takeIf { it.id() == transaction.id }?.let {
            submissionFeedback = it.completed(verified, saved)
        }
        emit()
        val completed = when {
            !verified -> messageId
            !intent.saves() -> R.string.controller_applied
            !saved -> if (intent.applies()) R.string.controller_applied_not_saved else R.string.controller_save_failed
            intent.applies() -> R.string.controller_applied_and_saved
            else -> R.string.controller_saved
        }
        notice.accept(app.getString(completed))
        if (saved) DevicePresence.sync(app)
    }

    private fun cancelProfileWork() {
        profileJobs.invalidate()
        profileChanging = false
    }

    fun setAutoRestore(address: String, enabled: Boolean, done: (Boolean) -> Unit) {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging) {
            done(false)
            return
        }
        val token = profileJobs.issue()
        profileChanging = true
        operationFailure = OperationFailure.NONE
        retainOutcome = true
        messageId = R.string.controller_checking_restore
        emit()
        worker.execute {
            var matches = false
            var paused = false
            val failure = try {
                check(profiles.get(address) != null) { "Connect the device and save a verified configuration first" }
                check(!enabled || bluetooth.hasPermission() && associations.associated(address)) {
                    "Nearby devices permission and a valid association required; connect and save. Association is not requested automatically."
                }
                if (!closed && profileJobs.isCurrent(token)) profiles.setEnabled(address, enabled)
                matches = profiles.get(address)?.enabled() == enabled
                paused = profiles.paused()
                null
            } catch (error: RuntimeException) {
                describe(error)
            }
            val success = failure == null && matches
            main.post {
                if (closed || !profileJobs.isCurrent(token)) return@post
                profileChanging = false
                operationFailure = if (success) OperationFailure.NONE else OperationFailure.RESTORE
                messageId = when {
                    !success -> R.string.controller_restore_change_failed
                    enabled -> R.string.controller_restore_enabled
                    else -> R.string.controller_restore_disabled
                }
                detail = failure ?: if (paused) "All restores remain paused; resume in the library"
                    else "One attempt per connection; settings are not continuously enforced"
                emit()
                done(success)
                if (success) DevicePresence.sync(app)
                if (success && enabled) RestoreService.requestCheck(app)
            }
        }
    }

    private fun changeProfiles(success: Int, check: Boolean, change: () -> Boolean) {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging) return
        val token = profileJobs.issue()
        profileChanging = true
        operationFailure = OperationFailure.NONE
        emit()
        worker.execute {
            var completed = false
            val failure = try {
                if (!closed && profileJobs.isCurrent(token)) completed = change()
                null
            } catch (error: RuntimeException) {
                describe(error)
            }
            main.post {
                if (closed || !profileJobs.isCurrent(token)) return@post
                profileChanging = false
                retainOutcome = true
                operationFailure = when {
                    failure != null -> OperationFailure.PROFILE
                    completed -> OperationFailure.NONE
                    else -> OperationFailure.PROFILE_CHANGED
                }
                messageId = when {
                    failure != null -> R.string.controller_profile_change_failed
                    completed -> success
                    else -> R.string.library_changed
                }
                detail = failure.orEmpty()
                emit()
                if (failure == null && completed) {
                    DevicePresence.sync(app)
                    if (check) RestoreService.requestCheck(app)
                }
            }
        }
    }

    fun removeProfile(address: String, revision: Long) =
        changeProfiles(R.string.controller_profile_deleted, check = false) { profiles.remove(address, revision) }

    fun pauseRestore(paused: Boolean) {
        if (paused) WriteArbiter.SHARED.cancelAutomatic()
        changeProfiles(if (paused) R.string.controller_restore_paused else R.string.controller_restore_resumed,
            check = !paused) {
            profiles.setPaused(paused)
            true
        }
    }

    override fun close() {
        closed = true
        operations.invalidate()
        reads.invalidate()
        releaseSave()
        cancelProfileWork()
        worker.shutdownNow()
        bluetoothLease.close()
        shizuku.close()
    }

    private fun describe(error: Throwable): String {
        var cause = error
        while (true) {
            val next = cause.cause ?: break
            if (next === cause) break
            cause = next
        }
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }

    private companion object {
        const val ShizukuModeKey = "shizuku"
    }
}

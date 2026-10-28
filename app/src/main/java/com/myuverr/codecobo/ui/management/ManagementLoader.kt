// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.management

import java.util.function.Consumer
import com.myuverr.codecobo.platform.RestoreTrace
import kotlinx.coroutines.*

/** Coalesces refreshes; closing cancels publication, not an in-flight Binder call. */
class ManagementLoader(private val reader: ManagementReader, private val updated: Consumer<ManagementData>) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var requested = false
    private var closed = false
    private var lastSuccessful = ManagementData(loading = true)

    fun refresh() {
        if (closed) return
        requested = true
        if (job?.isActive == true) return
        job = scope.launch {
            do {
                requested = false
                val result = try {
                    withContext(Dispatchers.IO) { reader.read() }.also {
                        ensureActive()
                        lastSuccessful = it
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: RuntimeException) {
                    val failure = "[Refresh failure]\nmanagement_read=failed exception=" +
                        RestoreTrace.safe(error.javaClass.name) +
                        "\nSnapshot below remains the previous successful observation; current facts are unavailable.\n"
                    // A failed publication must not become the next refresh's successful baseline.
                    lastSuccessful.copy(
                        loading = false, readError = "Records unavailable: " + error.javaClass.simpleName,
                        diagnostics = failure + lastSuccessful.diagnostics,
                    )
                }
                ensureActive()
                updated.accept(result)
            } while (requested)
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
    }
}

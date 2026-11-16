// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFlowTest {
    private val host = FakeHost()
    private var current = true

    @Test
    fun continuesOnlyOnceAfterAssociationIsConfirmed() {
        SaveFlow(aac48Request, { current }, host).start()
        host.inspections.single().accept(false)
        checkNotNull(host.association).run()

        assertTrue(host.writes.isEmpty())
        val recheck = host.inspections.last()
        recheck.accept(true)
        recheck.accept(true)

        assertEquals(listOf(aac48Request), host.writes)
    }

    @Test
    fun failedAssociationRecheckDoesNotSave() {
        SaveFlow(aac48Request, { current }, host).start()
        host.inspections.single().accept(false)
        checkNotNull(host.association).run()
        host.inspections.last().accept(false)

        assertTrue(host.failed)
        assertTrue(host.writes.isEmpty())
    }

    @Test
    fun preparingAnEditorDoesNotSubmitParameters() {
        SaveFlow(null, { current }, host).start()
        host.inspections.single().accept(true)

        assertTrue(host.prepared)
        assertTrue(host.writes.isEmpty())
    }

    @Test
    fun expiredAssociationCallbackDoesNotContinue() {
        SaveFlow(aac48Request, { current }, host).start()
        host.inspections.single().accept(false)
        current = false
        checkNotNull(host.association).run()

        assertEquals(1, host.inspections.size)
        assertTrue(host.writes.isEmpty())
    }

    private class FakeHost : SaveFlow.Host {
        val inspections = mutableListOf<Consumer<Boolean>>()
        var association: Runnable? = null
        val writes = mutableListOf<CodecRequest>()
        var prepared = false
        var failed = false

        override fun inspect(result: Consumer<Boolean>) { inspections += result }
        override fun associate(success: Runnable) { association = success }
        override fun write(request: CodecRequest) { writes += request }
        override fun prepared() { prepared = true }
        override fun failed() { failed = true }
    }
}

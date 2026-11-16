// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteArbiterTest {
    @Test
    fun manualWorkKeepsOwnershipWhenPreemptedAutomaticWorkCloses() {
        val arbiter = WriteArbiter()
        val automatic = checkNotNull(arbiter.automatic())
        val manual = arbiter.manual()

        assertFalse(automatic.current())
        assertTrue(manual.current())
        assertNull(arbiter.automatic())

        automatic.close()
        assertTrue(manual.current())
        assertNull(arbiter.automatic())

        manual.close()
        assertNotNull(arbiter.automatic())
    }

    @Test
    fun cancellingAutomaticWorkDoesNotCancelManualWork() {
        val arbiter = WriteArbiter()
        val manual = arbiter.manual()

        arbiter.cancelAutomatic()

        assertTrue(manual.current())
        manual.close()
        val automatic = checkNotNull(arbiter.automatic())
        arbiter.cancelAutomatic()
        assertFalse(automatic.current())
    }
}

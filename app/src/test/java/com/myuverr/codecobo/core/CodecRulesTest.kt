// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecRulesTest {
    @Test
    fun acceptsSupportedExplicitParameters() {
        assertTrue(CodecRules.valid(aac48Request, aacCapabilities))
    }

    @Test
    fun rejectsInvalidOrUnsupportedCombinations() {
        val unsupported = listOf(
            CodecRequest(1, 0),
            CodecRequest(1, 3),
            CodecRequest(1, 4),
            CodecRequest(2, 2),
            CodecRequest(1, 2, 2, 2, 0),
            CodecRequest(1, 2, 1, 1, 0),
        )
        for (request in unsupported) {
            assertFalse(request.toString(), CodecRules.valid(request, aacCapabilities))
        }
    }

    @Test
    fun unspecifiedFieldsDoNotOverrideSystemNegotiation() {
        val target = CodecRequest(1, 2)
        assertTrue(CodecRules.matches(target, CodecState(1, 2, 2, 1, -1)))
        assertFalse(CodecRules.matches(target, aac44State))
        assertFalse(CodecRules.matches(target, CodecState(4, 2, 2, 1, -1)))
    }

    @Test
    fun explicitlySelectedFieldsMustMatchReadback() {
        assertTrue(CodecRules.matches(aac48Request, aac48State))
        assertFalse(CodecRules.matches(aac48Request, CodecState(1, 2, 2, 2, 0)))
        assertFalse(CodecRules.matches(aac48Request, CodecState(1, 2, 1, 1, 0)))
    }

    @Test
    fun qualityPolicyMustBeRecognizedAndBelongToLdac() {
        val ldac = listOf(CodecCapability(4, "LDAC", 2, 1, 2))
        assertTrue(CodecRules.valid(CodecRequest(4, 2, 1, 2, 1001), ldac))
        assertFalse(CodecRules.valid(CodecRequest(4, 2, 1, 2, 1004), ldac))
        assertFalse(CodecRules.valid(CodecRequest(1, 2, 1, 2, 1001), aacCapabilities))
        assertFalse(CodecRules.matches(CodecRequest(4, 2, 1, 2, 1001), CodecState(4, 2, 1, 2, 1000)))
    }
}

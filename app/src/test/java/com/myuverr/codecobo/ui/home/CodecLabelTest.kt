// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CodecLabelTest {
    @Test
    fun aptxKeepsTheFamilyAndVariantApart() {
        assertEquals(CodecLabel("aptX", "HD"), codecLabel("aptX HD"))
    }

    @Test
    fun otherCodecsStayWhole() {
        assertEquals(CodecLabel("AAC", null), codecLabel("AAC"))
    }
}

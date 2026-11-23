// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class CodecNamesTest {
    @Test
    fun `known Savitech IDs override the platform label`() {
        assertEquals("LHDC V3/V4", CodecNames.extendedName(0x4C33_053A_FFL, "LHDC"))
        assertEquals("LHDC V5", CodecNames.extendedName(0x4C35_053A_FFL, "LHDC"))
    }

    @Test
    fun `unconfirmed IDs keep their platform labels`() {
        assertEquals("LHDC v3 (vendor)", CodecNames.extendedName(0x33_053A_FFL, "LHDC v3 (vendor)"))
        assertEquals("LHDC v5 (vendor)", CodecNames.extendedName(0x35_053A_FFL, "LHDC v5 (vendor)"))
        assertEquals("OEM codec", CodecNames.extendedName(0x4C35_1234_FFL, "OEM codec"))
    }
}

// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.ui.management.OwnAssociation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssociationRowsTest {
    private fun profile(address: String) =
        DeviceProfile(address, "Saved", "AAC", CodecRequest(1, 2), true, 1)

    @Test
    fun savedFlagMatchesAddressesIgnoringCase() {
        val rows = associationRows(
            listOf(OwnAssociation(1, "AA:BB:CC:DD:EE:01", "Headphones")),
            listOf(profile("aa:bb:cc:dd:ee:01")),
        )

        assertTrue(rows.single().saved)
    }

    @Test
    fun associationWithoutAddressIsNeverSaved() {
        val rows = associationRows(listOf(OwnAssociation(7, null, "Headphones")), listOf(profile("AA:BB:CC:DD:EE:01")))

        assertFalse(rows.single().saved)
        assertEquals(AssociationLabel.Named("Headphones"), rows.single().label)
    }

    @Test
    fun duplicateNamesAreCountedOnceForTheWholeList() {
        val rows = associationRows(
            listOf(
                OwnAssociation(1, "AA:BB:CC:DD:EE:01", "Headphones"),
                OwnAssociation(2, "AA:BB:CC:DD:EE:02", "Headphones"),
                OwnAssociation(3, "AA:BB:CC:DD:EE:03", "Speaker"),
            ),
            emptyList(),
        )

        assertEquals(listOf(true, true, false), rows.map { it.duplicateName })
        assertEquals("EE:01", rows[0].disambiguator)
        assertNull(rows[2].disambiguator)
    }

    @Test
    fun blankAndMissingNamesDoNotDisambiguate() {
        val rows = associationRows(
            listOf(
                OwnAssociation(1, null, null),
                OwnAssociation(2, "AA:BB:CC:DD:EE:02", "  "),
            ),
            emptyList(),
        )

        assertEquals(listOf(false, false), rows.map { it.duplicateName })
    }

    @Test
    fun labelFallsBackFromNameToAddressSuffixToIdentifier() {
        val rows = associationRows(
            listOf(
                OwnAssociation(1, "AA:BB:CC:DD:EE:01", "Headphones"),
                OwnAssociation(2, "AA:BB:CC:DD:EE:02", null),
                OwnAssociation(3, null, "   "),
            ),
            emptyList(),
        )

        assertEquals(AssociationLabel.Named("Headphones"), rows[0].label)
        assertEquals(AssociationLabel.AddressSuffix("EE:02"), rows[1].label)
        assertEquals(AssociationLabel.Identifier(3), rows[2].label)
    }
}

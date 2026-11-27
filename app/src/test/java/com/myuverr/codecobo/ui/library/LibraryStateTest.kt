// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.library

import com.myuverr.codecobo.ui.management.ManagementData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateTest {
    private val staleRead = ManagementData(restorePaused = false)

    @Test
    fun newerPausePreferenceSurvivesAnOlderMetadataRead() {
        val state = LibraryState().withMetadata(staleRead, latestPaused = true)

        assertTrue(state.data.restorePaused)
    }

    @Test
    fun newerResumePreferenceSurvivesAnOlderMetadataRead() {
        val state = LibraryState().withMetadata(ManagementData(restorePaused = true), latestPaused = false)

        assertFalse(state.data.restorePaused)
    }

    @Test
    fun metadataValueAppliesUntilTheAudioStateReportsOne() {
        assertTrue(LibraryState().withMetadata(ManagementData(restorePaused = true), latestPaused = null).data.restorePaused)
        assertFalse(LibraryState().withMetadata(staleRead, latestPaused = null).data.restorePaused)
    }
}

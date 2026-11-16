// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.controller

import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.core.aac48Request
import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionFeedbackTest {
    @Test
    fun verifiedAndPersistedConfigurationIsReportedAsSaved() {
        assertEquals(SubmissionFeedback.Status.SAVED, receipt(SubmissionIntent.SAVE).completed(true, true).status())
        assertEquals(
            SubmissionFeedback.Status.SAVED,
            receipt(SubmissionIntent.APPLY_AND_SAVE).completed(true, true).status(),
        )
    }

    @Test
    fun verifiedApplyWithoutSavingIsReportedAsApplied() {
        assertEquals(SubmissionFeedback.Status.APPLIED, receipt(SubmissionIntent.APPLY).completed(true, false).status())
    }

    @Test
    fun partialOrUnverifiedSubmissionDoesNotClaimCompletion() {
        val combined = receipt(SubmissionIntent.APPLY_AND_SAVE)
        assertEquals(SubmissionFeedback.Status.FINISHED, combined.completed(true, false).status())
        assertEquals(SubmissionFeedback.Status.FINISHED, combined.completed(false, true).status())
        assertEquals(SubmissionFeedback.Status.FINISHED, combined.completed(false, false).status())
        assertEquals(SubmissionFeedback.Status.FINISHED, receipt(SubmissionIntent.APPLY).completed(false, false).status())
    }

    private fun receipt(intent: SubmissionIntent) = SubmissionFeedback(
        1, "A1:B2:C3:D4:E5:F6", aac48Request, intent, SubmissionFeedback.Status.RUNNING,
    )
}

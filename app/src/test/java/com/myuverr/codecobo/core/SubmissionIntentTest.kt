// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

import com.myuverr.codecobo.core.SubmissionIntent.APPLY
import com.myuverr.codecobo.core.SubmissionIntent.APPLY_AND_SAVE
import com.myuverr.codecobo.core.SubmissionIntent.NONE
import com.myuverr.codecobo.core.SubmissionIntent.SAVE
import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionIntentTest {
    @Test
    fun choosesEffectsFromActualOutputAndSaveConsent() {
        assertEquals(NONE, SubmissionIntent.choose(aac48Request, aac48State, null, false, false))
        assertEquals(APPLY, SubmissionIntent.choose(aac48Request, aac44State, null, false, false))
        assertEquals(SAVE, SubmissionIntent.choose(aac48Request, aac48State, null, true, false))
        assertEquals(APPLY_AND_SAVE, SubmissionIntent.choose(aac48Request, aac44State, null, true, false))
    }

    @Test
    fun disablingRestoreStillRequiresSavingThePreference() {
        assertEquals(SAVE, SubmissionIntent.choose(aac48Request, aac48State, aac48Request, false, true))
    }

    @Test
    fun matchingSavedConfigurationNeedsNoSubmission() {
        assertEquals(NONE, SubmissionIntent.choose(aac48Request, aac48State, aac48Request, true, false))
    }

    @Test
    fun unspecifiedSavedFieldsAreOnlyUpdatedForAnActualParameterChange() {
        val saved = CodecRequest(1, 2)
        assertEquals(NONE, SubmissionIntent.choose(aac48Request, aac48State, saved, true, false))
        assertEquals(
            APPLY_AND_SAVE,
            SubmissionIntent.choose(aac48Request, CodecState(1, 2, 2, 2, 0), saved, true, false),
        )
    }

    @Test
    fun missingTargetOrActualOutputCannotAuthorizeSubmission() {
        assertEquals(NONE, SubmissionIntent.choose(null, aac48State, null, true, true))
        assertEquals(NONE, SubmissionIntent.choose(aac48Request, null, null, true, true))
    }
}

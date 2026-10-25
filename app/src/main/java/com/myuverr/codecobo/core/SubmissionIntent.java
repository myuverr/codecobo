// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;

/** Effects authorized by the submit action. */
public enum SubmissionIntent {
    NONE(false, false),
    APPLY(true, false),
    SAVE(false, true),
    APPLY_AND_SAVE(true, true);

    private final boolean applies, saves;
    SubmissionIntent(boolean applies, boolean saves) {
        this.applies = applies;
        this.saves = saves;
    }
    public boolean applies() {
        return applies;
    }
    public boolean saves() {
        return saves;
    }

    /** Restore preference changes require a save, including disabling. */
    public static SubmissionIntent choose(
        CodecRequest target, CodecState actual, CodecRequest saved, boolean restore, boolean restoreChanged) {
        if (target == null || actual == null)
            return NONE;
        boolean apply = !CodecRules.matches(target, actual);
        boolean save = restoreChanged || restore && CodecRules.needsSave(target, actual, saved);
        return apply ? (save ? APPLY_AND_SAVE : APPLY) : (save ? SAVE : NONE);
    }
}

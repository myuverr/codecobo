// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.controller;

import com.myuverr.codecobo.core.CodecRequest;
import com.myuverr.codecobo.core.SubmissionIntent;

/** UI receipt for an explicit submission. */
public record SubmissionFeedback(
    long id, String address, CodecRequest request, SubmissionIntent intent, Status status) {
    public enum Status { RUNNING, SAVED, APPLIED, FINISHED }
    public SubmissionFeedback completed(boolean verified, boolean saved) {
        Status result = verified && saved                     ? Status.SAVED
            : verified && intent.applies() && !intent.saves() ? Status.APPLIED
                                                              : Status.FINISHED;
        return new SubmissionFeedback(id, address, request, intent, result);
    }
}

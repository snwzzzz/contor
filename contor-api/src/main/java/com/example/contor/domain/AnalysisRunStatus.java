package com.example.contor.domain;

import java.util.Set;

/** Lifecycle of an inbox analysis run. */
public enum AnalysisRunStatus {
    QUEUED, DISCOVERING, AWAITING_REVIEW, EXTRACTING, COMPLETED, FAILED, CANCELLED;

    /** A user has at most one run in one of these states. */
    public static final Set<AnalysisRunStatus> ACTIVE = Set.of(QUEUED, DISCOVERING, AWAITING_REVIEW, EXTRACTING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}

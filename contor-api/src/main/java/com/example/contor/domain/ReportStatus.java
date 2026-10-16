package com.example.contor.domain;

import java.util.Set;

/** Lifecycle of a report: queued, then the pipeline phases, then READY or FAILED. */
public enum ReportStatus {
    QUEUED, COLLECTING, DRAFTING, FACT_CHECKING, READY, FAILED;

    /** Still being generated: no follow-up or regeneration until it is done. */
    public static final Set<ReportStatus> ACTIVE = Set.of(QUEUED, COLLECTING, DRAFTING, FACT_CHECKING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}

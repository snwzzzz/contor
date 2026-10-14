package com.example.contor.ai.eval;

import java.time.LocalDate;
import java.util.List;

/** Whether an expected insight of the business mailbox was proposed: in time (within a week of {@code asOf}), late or not at all. */
final class InsightMatch {

    static final int GRACE_DAYS = 7;

    enum Status { FOUND, LATE, MISSED }

    /** An insight the pipeline proposed, with the messages it cites and the day it was proposed. */
    record Proposed(List<String> evidence, LocalDate proposedOn) {
    }

    private InsightMatch() {
    }

    static Status status(BusinessCorpus.ExpectedInsight expected, List<Proposed> proposed) {
        LocalDate first = proposed.stream().filter(insight -> insight.evidence().containsAll(expected.evidence()))
                .map(Proposed::proposedOn).min(LocalDate::compareTo).orElse(null);
        if (first == null) {
            return Status.MISSED;
        }
        return first.isAfter(expected.asOf().plusDays(GRACE_DAYS)) ? Status.LATE : Status.FOUND;
    }
}

package com.example.contor.ai.eval;

import java.util.List;

/** What the extraction produced for one eval case; {@code error} is set when the model call failed. */
public record CaseResult(String caseId, List<ProducedRecord> produced, long latencyMillis, String error) {

    public CaseResult {
        produced = produced != null ? produced : List.of();
    }
}

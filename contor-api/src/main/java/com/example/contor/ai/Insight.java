package com.example.contor.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Something the user may have missed across several emails (AI v2 step 8), before it becomes an INSIGHT proposal.
 *
 * @param key              what it is about, stable across runs; an insight with a known key is never proposed again
 * @param evidenceEmailIds the emails it is based on (the user's own; checked before it is stored)
 * @param followUps        to-dos or appointments approving it creates (the user can drop each)
 * @param source           {@code rule:<name>} or {@code model}
 * @param confidence       0–1, {@code null} for rules
 */
public record Insight(String key, String title, String observation, String whyItMatters, List<Long> evidenceEmailIds,
                      List<FollowUp> followUps, String source, BigDecimal confidence) {

    public Insight {
        evidenceEmailIds = List.copyOf(evidenceEmailIds);
        followUps = List.copyOf(followUps);
    }

    /**
     * @param collection {@code tasks} or {@code events}
     * @param values     record values ({@code due_date} for a to-do, {@code starts_at} for an appointment)
     */
    public record FollowUp(String collection, String title, Map<String, Object> values) {

        public FollowUp {
            values = Map.copyOf(values);
        }
    }
}

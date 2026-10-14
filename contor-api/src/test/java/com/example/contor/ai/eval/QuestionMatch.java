package com.example.contor.ai.eval;

import java.util.ArrayList;
import java.util.List;

/** How many labelled questions the triage found: a found question matches when it shares enough words (Jaccard ≥ 0.4). */
final class QuestionMatch {

    static final double SIMILARITY = 0.4;

    private QuestionMatch() {
    }

    static int found(List<String> labelled, List<String> found) {
        List<String> unclaimed = new ArrayList<>(found);
        int matched = 0;
        for (String question : labelled) {
            String hit = unclaimed.stream().filter(candidate -> EvalScorer.titleSimilarity(question, candidate) >= SIMILARITY)
                    .findFirst().orElse(null);
            if (hit != null) {
                unclaimed.remove(hit);
                matched++;
            }
        }
        return matched;
    }
}

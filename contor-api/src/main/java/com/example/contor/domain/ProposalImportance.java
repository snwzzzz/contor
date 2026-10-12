package com.example.contor.domain;

/**
 * How much a proposal deserves the user's attention. Derived from the 0–100 importance score by fixed thresholds, so a
 * filter "at least HIGH" is a score filter ({@link #minScore()}).
 */
public enum ProposalImportance {
    LOW(0), NORMAL(35), HIGH(60), URGENT(80);

    private final int minScore;

    ProposalImportance(int minScore) {
        this.minScore = minScore;
    }

    /** The lowest score of this level. */
    public int minScore() {
        return minScore;
    }

    /** The level of a score (0–100). */
    public static ProposalImportance of(int score) {
        ProposalImportance level = LOW;
        for (ProposalImportance candidate : values()) {
            if (score >= candidate.minScore) {
                level = candidate;
            }
        }
        return level;
    }
}

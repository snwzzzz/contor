package com.example.contor.ai.eval;

/**
 * How well the reply decision (candidate rules + triage call) matches the {@code needsReply} labels.
 * Precision: of the emails that got a draft, how many needed one. Recall: of the emails that needed one, how many got it.
 */
record ReplyTriageScore(int truePositives, int falsePositives, int falseNegatives, int trueNegatives) {

    static ReplyTriageScore empty() {
        return new ReplyTriageScore(0, 0, 0, 0);
    }

    ReplyTriageScore add(boolean expected, boolean predicted) {
        return new ReplyTriageScore(
                truePositives + (expected && predicted ? 1 : 0),
                falsePositives + (!expected && predicted ? 1 : 0),
                falseNegatives + (expected && !predicted ? 1 : 0),
                trueNegatives + (!expected && !predicted ? 1 : 0));
    }

    /** 1 when nothing was predicted (no wrong drafts). */
    double precision() {
        int predicted = truePositives + falsePositives;
        return predicted == 0 ? 1.0 : (double) truePositives / predicted;
    }

    /** 1 when nothing needed a reply. */
    double recall() {
        int expected = truePositives + falseNegatives;
        return expected == 0 ? 1.0 : (double) truePositives / expected;
    }
}

package com.example.contor.domain;

/**
 * Fact-check result of a report sentence: SUPPORTED and PARTIAL by the verifier, REPAIRED = rewritten after a failed
 * check, REMOVED = still unsupported (kept only for the fact-check details), CONFLICT = its sources disagree.
 */
public enum ClaimVerification {
    SUPPORTED, PARTIAL, REPAIRED, REMOVED, CONFLICT
}

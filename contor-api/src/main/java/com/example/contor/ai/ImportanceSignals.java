package com.example.contor.ai;

/**
 * What is known about the email a proposal came from, beyond the proposal itself (see {@link ImportanceScorer}).
 *
 * @param urgency       the reply triage's {@code low|normal|high}, {@code null} when not asked
 * @param knownContact  the sender is one of the user's People records
 * @param corresponded  the user has written to the sender before
 * @param bulkSender    a no-reply, notification or mailing-list style sender
 */
public record ImportanceSignals(String urgency, boolean knownContact, boolean corresponded, boolean bulkSender) {

    /** Nothing known (proposals without a source email, or before the signals were collected). */
    public static final ImportanceSignals NONE = new ImportanceSignals(null, false, false, false);

    public ImportanceSignals withUrgency(String urgency) {
        return new ImportanceSignals(urgency, knownContact, corresponded, bulkSender);
    }
}

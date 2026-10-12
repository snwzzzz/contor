package com.example.contor.ai;

import com.example.contor.domain.ProposalImportance;
import com.example.contor.domain.ProposalKind;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How important a proposal is, as a 0–100 score with its level and a short reason the UI shows. Rules, no model call:
 * the kind sets the base (a reply someone waits for > a to-do > an appointment > a new category > a finding > a
 * relation), then a deadline or date in the payload (soon raises, past lowers), the reply triage's urgency, and what is
 * known about the sender move it. The age of a proposal is not part of it: sorting by date covers that.
 */
public final class ImportanceScorer {

    private static final String TASKS = "tasks";
    private static final String EVENTS = "events";
    private static final String COURTESY = "COURTESY";

    private ImportanceScorer() {
    }

    public record Importance(int score, ProposalImportance level, String reason) {

        /** What a proposal gets when nothing was scored (before importance existed). */
        public static final Importance DEFAULT = new Importance(50, ProposalImportance.NORMAL, null);
    }

    /**
     * @param collectionSlug the collection of a record proposal, {@code null} for other kinds
     * @param payload        the proposal's payload (record values under {@code values})
     */
    public static Importance score(ProposalKind kind, String collectionSlug, Map<String, Object> payload, ImportanceSignals signals,
                                   Instant now, ZoneId zone) {
        List<String> reasons = new ArrayList<>();
        // An optional polite reply (a thank-you after a rejection) never competes with real work: always LOW.
        boolean courtesy = kind == ProposalKind.DRAFT_REPLY && COURTESY.equals(payload.get("intent"));
        int score = courtesy ? reason(reasons, "an optional polite reply", 20) : base(kind, collectionSlug, reasons);

        Long days = daysUntil(payload, now, zone);
        if (days != null) {
            if (days < 0) {
                score -= 20;
                reasons.add("already in the past");
            } else if (days <= 2) {
                score += 25;
                reasons.add("due within 2 days");
            } else if (days <= 7) {
                score += 10;
                reasons.add("due this week");
            }
        }
        if ("high".equals(signals.urgency())) {
            score += 20;
            reasons.add("marked urgent");
        } else if ("low".equals(signals.urgency())) {
            score -= 10;
            reasons.add("can wait");
        }
        if (signals.knownContact()) {
            score += 10;
            reasons.add("from one of your contacts");
        }
        if (signals.corresponded()) {
            score += 10;
            reasons.add("someone you write with");
        }
        if (signals.bulkSender()) {
            score -= 30;
            reasons.add("bulk sender");
        }
        int clamped = Math.clamp(score, 0, courtesy ? ProposalImportance.NORMAL.minScore() - 1 : 100);
        return new Importance(clamped, ProposalImportance.of(clamped), String.join("; ", reasons));
    }

    private static int base(ProposalKind kind, String collectionSlug, List<String> reasons) {
        return switch (kind) {
            case DRAFT_REPLY -> reason(reasons, "a reply someone waits for", 55);
            case CREATE_RECORD, UPDATE_RECORD -> {
                if (TASKS.equals(collectionSlug)) yield reason(reasons, "a to-do", 50);
                if (EVENTS.equals(collectionSlug)) yield reason(reasons, "an appointment", 45);
                yield kind == ProposalKind.CREATE_RECORD ? reason(reasons, "a finding", 30) : reason(reasons, "an update of a finding", 25);
            }
            case CREATE_COLLECTION, ADD_FIELD -> reason(reasons, "a new category", 40);
            case CREATE_RELATION -> reason(reasons, "a connection between records", 15);
            default -> reason(reasons, "housekeeping", 25);
        };
    }

    private static int reason(List<String> reasons, String reason, int score) {
        reasons.add(reason);
        return score;
    }

    /** Days from today to the payload's {@code due_date} or {@code starts_at} (negative: past); {@code null} without one. */
    private static Long daysUntil(Map<String, Object> payload, Instant now, ZoneId zone) {
        if (!(payload.get("values") instanceof Map<?, ?> values)) {
            return null;
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        try {
            if (values.get("due_date") instanceof String due) {
                return ChronoUnit.DAYS.between(today, LocalDate.parse(due));
            }
            if (values.get("starts_at") instanceof String start) {
                OffsetDateTime starts = OffsetDateTime.parse(start);
                return starts.toInstant().isBefore(now) ? -1L : ChronoUnit.DAYS.between(today, starts.atZoneSameInstant(zone).toLocalDate());
            }
        } catch (DateTimeParseException e) {
            return null;
        }
        return null;
    }
}

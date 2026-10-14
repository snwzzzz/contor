package com.example.contor.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Insights found by rules, no model call: cheap, testable and never invented. The model pass ({@code InsightModelService})
 * looks for what rules cannot see.
 */
public final class InsightDetectors {

    /** A question gets this long before its silence is worth a follow-up. */
    public static final Duration ANSWER_WAIT = Duration.ofDays(4);

    private static final String TASKS = "tasks";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);

    private InsightDetectors() {
    }

    /**
     * Mail the user sent and nobody answered in its conversation.
     *
     * @param body the cleaned text the user wrote (no quoted history)
     */
    public record SentMail(Long emailId, String toName, String toAddress, String subject, String body, Instant sentAt) {
    }

    /** A timed appointment of the Calendar collection, with the emails it was created from. */
    public record Appointment(Long recordId, String title, Instant start, Instant end, List<Long> emailIds) {
    }

    /** A question the user asked at least {@link #ANSWER_WAIT} ago without an answer: follow up tomorrow. */
    public static Optional<Insight> unansweredQuestion(SentMail mail, Instant now, ZoneId zone) {
        if (mail.body() == null || !mail.body().contains("?") || mail.sentAt().isAfter(now.minus(ANSWER_WAIT))) {
            return Optional.empty();
        }
        String who = mail.toName() != null && !mail.toName().isBlank() ? mail.toName()
                : Optional.ofNullable(mail.toAddress()).orElse("them");
        String subject = Optional.ofNullable(mail.subject()).filter(text -> !text.isBlank()).orElse("your email");
        LocalDate today = now.atZone(zone).toLocalDate();
        long days = ChronoUnit.DAYS.between(mail.sentAt().atZone(zone).toLocalDate(), today);
        LocalDate tomorrow = today.plusDays(1);
        return Optional.of(new Insight("unanswered:" + mail.emailId(), "No answer from " + who + " yet",
                "You asked " + who + " something in \"" + subject + "\" " + days + " days ago and nobody has answered since.",
                "Questions without an answer tend to stall what depends on them; a short follow-up keeps it moving.",
                List.of(mail.emailId()), List.of(new Insight.FollowUp(TASKS, "Follow up with " + who + ": " + subject,
                Map.of("due_date", tomorrow.toString()))), "rule:unanswered-question", null));
    }

    /** Pairs of appointments that overlap: resolve each clash the day before. */
    public static List<Insight> clashes(List<Appointment> appointments, ZoneId zone) {
        List<Appointment> sorted = appointments.stream().sorted(Comparator.comparing(Appointment::start)).toList();
        List<Insight> clashes = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size() && sorted.get(j).start().isBefore(sorted.get(i).end()); j++) {
                Appointment first = sorted.get(i);
                Appointment second = sorted.get(j);
                LocalDate day = first.start().atZone(zone).toLocalDate();
                long low = Math.min(first.recordId(), second.recordId());
                long high = Math.max(first.recordId(), second.recordId());
                List<Long> evidence = Stream.concat(first.emailIds().stream(), second.emailIds().stream()).distinct().sorted().toList();
                clashes.add(new Insight("clash:" + low + ":" + high, "Two appointments overlap on " + DAY.format(day),
                        "\"" + first.title() + "\" and \"" + second.title() + "\" are both in your calendar at the same time.",
                        "You cannot attend both; moving one now is easier than at the last minute.", evidence,
                        List.of(new Insight.FollowUp(TASKS, "Resolve the clash: " + first.title() + " / " + second.title(),
                                Map.of("due_date", day.minusDays(1).toString()))), "rule:clash", null));
            }
        }
        return clashes;
    }
}

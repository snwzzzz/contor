package com.example.contor.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cheap rules that rule out emails which do not need a reply from the user, before any model call: the user's own mail,
 * automated senders and texts, mail that only copies the user, conversations already answered or continued, and mail
 * older than {@link #WINDOW} (nobody wants drafts for last year's mail).
 */
public final class ReplyCandidateFilter {

    /** Only mail received within this window gets a draft, however far back the analysis reads. */
    public static final Duration WINDOW = Duration.ofDays(14);

    private static final Pattern AUTOMATED_SENDER = Pattern.compile(
            "^(.*no-?reply.*|.*do-?not-?reply.*|mailer-daemon|postmaster|notifications?|.*bounces?.*)@.*");
    private static final List<String> AUTOMATED_TEXT = List.of(
            "this is an automated message", "automatically generated", "please do not reply", "do not reply to this",
            "automatisch generiert", "automatisch erstellt", "bitte antworten sie nicht", "unsubscribe");

    /** Why an email gets no draft. */
    public enum Skip { OWN_MESSAGE, AUTOMATED_SENDER, NOT_ADDRESSED, ANSWERED, NEWER_MESSAGE, TOO_OLD, AUTOMATED_TEXT }

    /**
     * @param to           addresses in the email's To line
     * @param body         the cleaned body ({@link EmailTextCleaner})
     * @param ownAddresses the user's mailbox addresses
     * @param answered     a message the user sent in the conversation is newer than this email
     * @param newerMessage a newer received message in the conversation exists (it gets the draft instead)
     */
    public record Candidate(String fromAddress, List<String> to, String body, Instant receivedAt, Set<String> ownAddresses,
                            boolean answered, boolean newerMessage) {
    }

    private ReplyCandidateFilter() {
    }

    /** The first rule that rules the email out, or empty when it may need a reply. */
    public static Optional<Skip> skip(Candidate candidate, Instant now) {
        Set<String> own = candidate.ownAddresses().stream().map(ReplyCandidateFilter::normalize).collect(Collectors.toSet());
        String from = normalize(candidate.fromAddress());
        if (own.contains(from)) {
            return Optional.of(Skip.OWN_MESSAGE);
        }
        if (from.isEmpty() || AUTOMATED_SENDER.matcher(from).matches()) {
            return Optional.of(Skip.AUTOMATED_SENDER);
        }
        if (candidate.to() == null || candidate.to().stream().map(ReplyCandidateFilter::normalize).noneMatch(own::contains)) {
            return Optional.of(Skip.NOT_ADDRESSED);
        }
        if (candidate.answered()) {
            return Optional.of(Skip.ANSWERED);
        }
        if (candidate.newerMessage()) {
            return Optional.of(Skip.NEWER_MESSAGE);
        }
        if (candidate.receivedAt() == null || candidate.receivedAt().isBefore(now.minus(WINDOW))) {
            return Optional.of(Skip.TOO_OLD);
        }
        String body = candidate.body() != null ? candidate.body().toLowerCase(Locale.ROOT) : "";
        if (AUTOMATED_TEXT.stream().anyMatch(body::contains)) {
            return Optional.of(Skip.AUTOMATED_TEXT);
        }
        return Optional.empty();
    }

    private static String normalize(String address) {
        return address != null ? address.strip().toLowerCase(Locale.ROOT) : "";
    }
}

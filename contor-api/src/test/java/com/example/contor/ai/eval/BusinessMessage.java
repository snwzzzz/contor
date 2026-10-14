package com.example.contor.ai.eval;

import com.example.contor.ai.EmailContent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One message of the business mailbox ({@code ai-eval/business/mailbox}): an {@link EvalCase} plus where it sits in the
 * mailbox (account, folder, conversation, references) and the reply, importance and context labels.
 *
 * @param storyline storyline id, or {@code noise}
 * @param folder    {@code inbox} or {@code sent} (sent mail is never extracted and carries no labels)
 * @param cc        the email's CC addresses ({@link EmailContent} has none)
 * @param duplicateOf the first copy when the same mail reached a second account
 * @param dependsOn earlier messages needed to label this one fully
 * @param derived   {@code collection.field} labels that are not stated literally (relative dates, calendar weeks, typos) and why
 */
public record BusinessMessage(String id, String storyline, String beat, String category, String kind, String lang, String account,
                              String folder, String conversationId, String inReplyTo, String duplicateOf, EmailContent email,
                              List<String> cc, EvalCase.Expected expected, List<EvalCase.Forbidden> mustNotProduce, Boolean needsReply,
                              Reply reply, String importance, List<String> dependsOn, List<String> tags, List<String> storylineTags,
                              Map<String, String> derived, List<String> retracts) {

    public BusinessMessage {
        cc = cc != null ? cc : List.of();
        expected = expected != null ? expected : new EvalCase.Expected(null, null);
        mustNotProduce = mustNotProduce != null ? mustNotProduce : List.of();
        dependsOn = dependsOn != null ? dependsOn : List.of();
        tags = tags != null ? tags : List.of();
        storylineTags = storylineTags != null ? storylineTags : List.of();
        derived = derived != null ? derived : Map.of();
        retracts = retracts != null ? retracts : List.of();
    }

    /**
     * @param answeredBy the owner's sent reply in the mailbox, if any (that mail must then get no new draft)
     */
    public record Reply(String intent, List<String> questions, LocalDate scheduleFrom, LocalDate scheduleTo, String timeOfDay,
                        String courtesyKind, String answeredBy) {

        public Reply {
            questions = questions != null ? questions : List.of();
        }
    }

    public boolean sent() {
        return "sent".equals(folder);
    }

    public boolean noise() {
        return "noise".equals(storyline);
    }

    /** A business mail the extraction and the triage are judged on: inbox, not noise, not the second copy of a mail. */
    public boolean labelled() {
        return !sent() && !noise() && duplicateOf == null;
    }

    public EvalCase evalCase() {
        return new EvalCase(id, email, expected, mustNotProduce, needsReply);
    }

    /** As {@link #evalCase()}, with the messages it depends on as context: same conversation → thread, same sender → sender history. */
    public EvalCase evalCaseWithContext(Map<String, BusinessMessage> byId) {
        List<EmailContent> thread = new ArrayList<>();
        List<EmailContent> fromSender = new ArrayList<>();
        List<EmailContent> related = new ArrayList<>();
        dependsOn.stream().map(byId::get).filter(Objects::nonNull).forEach(earlier -> {
            if (Objects.equals(earlier.conversationId(), conversationId)) {
                thread.add(earlier.email());
            } else if (Objects.equals(earlier.email().fromAddress(), email.fromAddress())) {
                fromSender.add(earlier.email());
            } else {
                related.add(earlier.email());
            }
        });
        return new EvalCase(id, email, expected, mustNotProduce, needsReply, new EvalCase.Context(thread, fromSender, related, List.of()));
    }
}

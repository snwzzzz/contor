package com.example.contor.ai.eval;

import com.example.contor.ai.ImportanceScorer;
import com.example.contor.ai.ImportanceSignals;
import com.example.contor.ai.ReplyCandidateFilter;
import com.example.contor.domain.ProposalImportance;
import com.example.contor.domain.ProposalKind;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The importance the rules ({@link ImportanceScorer}) give the main proposal of a labelled business mail, next to the
 * labelled band. The main proposal is the reply when one is needed (or a courtesy reply), else the first to-do, then an
 * appointment, then any other record. Signals come from the mail before it: a sender labelled as a person earlier is a
 * known contact, one the owner wrote to is corresponded; the triage's urgency is not part of it (no model call).
 */
record ImportanceCase(String messageId, ProposalKind kind, String collection, ImportanceSignals signals, ProposalImportance scored,
                      ProposalImportance expected) {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final List<String> RECORD_ORDER = List.of("tasks", "events", "complaints", "sales_orders", "purchase_orders", "shipments",
            "invoices", "customers", "suppliers", "people", "organizations");

    /** {@code null} when the mail leads to no proposal at all. */
    static ImportanceCase of(BusinessMessage message, List<BusinessMessage> earlier) {
        ProposalKind kind;
        String collection = null;
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean courtesy = message.reply() != null && "COURTESY".equals(message.reply().intent());
        if (Boolean.TRUE.equals(message.needsReply()) || courtesy) {
            kind = ProposalKind.DRAFT_REPLY;
            payload.put("intent", courtesy ? "COURTESY" : message.reply() != null ? message.reply().intent() : "ANSWER_QUESTIONS");
        } else {
            collection = RECORD_ORDER.stream().filter(slug -> !message.expected().records().getOrDefault(slug, List.of()).isEmpty())
                    .findFirst().orElse(null);
            if (collection == null) {
                return null;
            }
            kind = ProposalKind.CREATE_RECORD;
            payload.put("values", message.expected().records().get(collection).getFirst().values());
        }
        String sender = message.email().fromAddress();
        boolean known = earlier.stream().filter(m -> !m.sent()).flatMap(m -> m.expected().records().getOrDefault("people", List.of()).stream())
                .anyMatch(person -> sender.equalsIgnoreCase(String.valueOf(person.values().get("email"))));
        boolean corresponded = earlier.stream().filter(BusinessMessage::sent)
                .anyMatch(m -> m.email().to().stream().anyMatch(sender::equalsIgnoreCase) || m.cc().stream().anyMatch(sender::equalsIgnoreCase));
        ImportanceSignals signals = new ImportanceSignals(null, known, corresponded, ReplyCandidateFilter.isAutomatedSender(sender));
        ImportanceScorer.Importance importance = ImportanceScorer.score(kind, collection, payload, signals, message.email().receivedAt(), ZONE);
        ProposalImportance expected = message.importance() != null ? ProposalImportance.valueOf(message.importance()) : null;
        return new ImportanceCase(message.id(), kind, collection, signals, importance.level(), expected);
    }
}

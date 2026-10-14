package com.example.contor.ai.eval;

import com.example.contor.ai.EmailContent;
import com.example.contor.domain.ProposalImportance;
import com.example.contor.domain.ProposalKind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BusinessScoringTest {

    private static final String OWNER = "alex.morgan@nordhafen.example";

    private static BusinessMessage message(String id, String folder, String from, List<String> to, String at, Map<String, List<EvalCase.ExpectedRecord>> records,
                                           Boolean needsReply, BusinessMessage.Reply reply, String importance) {
        return new BusinessMessage(id, "s", "b", "A1", null, "de", "alex", folder, "c", null, null,
                new EmailContent(null, OWNER, "Sender", from, to, "Subject", Instant.parse(at), "Body"), List.of(),
                new EvalCase.Expected(records, List.of()), List.of(), needsReply, reply, importance, List.of(), List.of(), List.of(), Map.of(), List.of());
    }

    private static BusinessMessage.Reply reply(String intent) {
        return new BusinessMessage.Reply(intent, List.of(), null, null, null, null, null);
    }

    @Test
    void the_main_proposal_is_the_reply_when_one_is_needed_else_a_to_do_before_other_records() {
        BusinessMessage needsReply = message("m-1", "inbox", "a@x.example", List.of(OWNER), "2026-07-07T08:00:00Z",
                Map.of("tasks", List.of(new EvalCase.ExpectedRecord("Pay", Map.of()))), true, reply("CONFIRM"), "NORMAL");
        BusinessMessage task = message("m-2", "inbox", "a@x.example", List.of(OWNER), "2026-07-07T08:00:00Z",
                Map.of("sales_orders", List.of(new EvalCase.ExpectedRecord("SO", Map.of())),
                        "tasks", List.of(new EvalCase.ExpectedRecord("Pay invoice", Map.of("due_date", "2026-07-08")))), false, null, "HIGH");
        BusinessMessage courtesy = message("m-3", "inbox", "a@x.example", List.of(OWNER), "2026-07-07T08:00:00Z", Map.of(), false,
                reply("COURTESY"), "LOW");

        assertThat(ImportanceCase.of(needsReply, List.of()).kind()).isEqualTo(ProposalKind.DRAFT_REPLY);
        ImportanceCase toDo = ImportanceCase.of(task, List.of());
        assertThat(toDo.kind()).isEqualTo(ProposalKind.CREATE_RECORD);
        assertThat(toDo.collection()).isEqualTo("tasks");
        assertThat(toDo.scored()).isEqualTo(ProposalImportance.HIGH); // to-do 50 + due within 2 days 25 = 75
        assertThat(ImportanceCase.of(courtesy, List.of()).scored()).isEqualTo(ProposalImportance.LOW);
    }

    @Test
    void earlier_mail_makes_the_sender_known_and_corresponded() {
        BusinessMessage earlierIn = message("m-1", "inbox", "a@x.example", List.of(OWNER), "2026-07-06T08:00:00Z",
                Map.of("people", List.of(new EvalCase.ExpectedRecord("A", Map.of("email", "a@x.example")))), true, reply("CONFIRM"), "NORMAL");
        BusinessMessage earlierOut = message("m-2", "sent", OWNER, List.of("a@x.example"), "2026-07-06T09:00:00Z", Map.of(), null, null, null);
        BusinessMessage now = message("m-3", "inbox", "a@x.example", List.of(OWNER), "2026-07-07T08:00:00Z",
                Map.of("events", List.of(new EvalCase.ExpectedRecord("Visit", Map.of("starts_at", "2026-07-20T10:00:00+02:00")))), false, null, "NORMAL");

        ImportanceCase scored = ImportanceCase.of(now, List.of(earlierIn, earlierOut));

        assertThat(scored.signals().knownContact()).isTrue();
        assertThat(scored.signals().corresponded()).isTrue();
        assertThat(scored.signals().bulkSender()).isFalse();
        assertThat(ImportanceCase.of(now, List.of()).signals().knownContact()).isFalse();
    }

    @Test
    void a_mail_without_a_proposal_has_no_importance_case() {
        BusinessMessage nothing = message("m-1", "inbox", "a@x.example", List.of(OWNER), "2026-07-07T08:00:00Z", Map.of(), false, null, "LOW");
        assertThat(ImportanceCase.of(nothing, List.of())).isNull();
    }

    @Test
    void questions_count_as_found_when_they_share_enough_words() {
        List<String> labelled = List.of("Was kosten 6 Stehlagereinheiten UCP 205?", "Ist eine Lieferung bis 17.07. möglich?");
        List<String> found = List.of("Lieferung nach Leipzig bis 17.07. möglich?", "Gibt es Rabatt?");

        assertThat(QuestionMatch.found(labelled, found)).isEqualTo(1);
        assertThat(QuestionMatch.found(labelled, List.of())).isZero();
    }

    @Test
    void the_end_state_comparison_scores_collections_and_counts_duplicates() {
        BusinessCorpus.EndState expected = new BusinessCorpus.EndState("2026-07-31", Map.of(
                "people", List.of(new BusinessCorpus.StateRecord("Marek Wiśniewski", Map.of("phone", "+48 5550 4171"), List.of("m-1")),
                        new BusinessCorpus.StateRecord("Marek Kowalczyk", Map.of("email", "m.k@o.example"), List.of("m-2"))),
                "sales_orders", List.of(new BusinessCorpus.StateRecord("Kessler SO-26-0155", Map.of("order_number", "SO-26-0155", "state", "delivered"),
                        List.of("m-3")))), List.of(new BusinessCorpus.StateRelation("drives_for", "people:Marek Wiśniewski", "organizations:Wisła-Trans", List.of("m-1"))));
        Map<String, List<ProducedRecord>> actual = Map.of(
                "people", List.of(new ProducedRecord("people", "Marek Wiśniewski", Map.of("phone", "+48 5550 4171")),
                        new ProducedRecord("people", "Marek Wisniewski", Map.of("phone", "+48 5550 4171")),
                        new ProducedRecord("people", "Marek Kowalczyk", Map.of("email", "m.k@o.example"))),
                "sales_orders", List.of(new ProducedRecord("sales_orders", "Kessler order", Map.of("order_number", "SO-26-0155", "state", "shipped"))));

        EndStateComparison.Result result = EndStateComparison.compare(expected, actual, List.of(), EvalCollections.business());

        assertThat(result.report().collections().get("people").recall()).isEqualTo(1.0);
        assertThat(result.duplicates()).containsEntry("people", 1);
        assertThat(result.report().fields().get("sales_orders.state").accuracy()).isEqualTo(0.0);
        assertThat(result.report().relations().recall()).isEqualTo(0.0);
    }

    @Test
    void an_insight_is_found_when_its_evidence_covers_the_expected_messages_and_late_after_a_week() {
        var expected = new BusinessCorpus.ExpectedInsight("i", "s", "Kessler at risk", LocalDate.parse("2026-07-23"), List.of("m-1", "m-2"));

        assertThat(InsightMatch.status(expected, List.of(new InsightMatch.Proposed(List.of("m-1", "m-2", "m-9"), LocalDate.parse("2026-07-24")))))
                .isEqualTo(InsightMatch.Status.FOUND);
        assertThat(InsightMatch.status(expected, List.of(new InsightMatch.Proposed(List.of("m-1", "m-2"), LocalDate.parse("2026-08-03")))))
                .isEqualTo(InsightMatch.Status.LATE);
        assertThat(InsightMatch.status(expected, List.of(new InsightMatch.Proposed(List.of("m-1"), LocalDate.parse("2026-07-24")))))
                .isEqualTo(InsightMatch.Status.MISSED);
    }
}

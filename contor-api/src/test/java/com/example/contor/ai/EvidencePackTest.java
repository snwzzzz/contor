package com.example.contor.ai;

import com.example.contor.ai.EvidencePack.EmailInput;
import com.example.contor.ai.EvidencePack.FieldInput;
import com.example.contor.ai.EvidencePack.RecordInput;
import com.example.contor.ai.EvidencePack.RelationInput;
import com.example.contor.ai.EvidencePack.Source;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EvidencePackTest {

    private static final RecordInput ORDER = new RecordInput(1L, "Orders", 4711L, "Order 4711", true, List.of(
            new FieldInput("expected_delivery", "Expected delivery", FieldType.DATE, "2026-09-02"),
            new FieldInput("total", "Total", FieldType.MONEY, Map.of("amount", 110, "currency", "EUR")),
            new FieldInput("notes", "Notes", FieldType.LONG_TEXT, null)));
    private static final RecordInput DELIVERY = new RecordInput(2L, "Deliveries", 88L, "Delivery 88", false, List.of(
            new FieldInput("delivered_at", "Delivered at", FieldType.DATE, "2026-09-07")));

    @Test
    void records_and_their_filled_fields_become_sources_with_tokens() {
        var pack = EvidencePack.builder().record(ORDER).record(DELIVERY).build("What went wrong?", EvidencePack.DEFAULT_BUDGET);

        assertThat(pack.sources()).extracting(Source::token).containsExactly("R1", "F1", "F2", "R2", "F3");
        Source total = pack.source("F2").orElseThrow();
        assertThat(total.type()).isEqualTo(CitationType.FIELD);
        assertThat(total.collectionId()).isEqualTo(1L);
        assertThat(total.recordId()).isEqualTo(4711L);
        assertThat(total.fieldSlug()).isEqualTo("total");
        assertThat(total.value()).isEqualTo(Map.of("amount", 110, "currency", "EUR"));
        assertThat(total.text()).isEqualTo("Order 4711 · Total = 110 EUR");
        assertThat(pack.source("F1").orElseThrow().date()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(pack.render()).contains("[R1] Orders: Order 4711 (the record the report is about)", "[F1] Order 4711 · Expected delivery = 2026-09-02");
    }

    @Test
    void tokens_are_found_with_or_without_brackets_and_in_any_case() {
        var pack = EvidencePack.builder().record(ORDER).build("", EvidencePack.DEFAULT_BUDGET);

        assertThat(pack.source("[f1]")).isPresent();
        assertThat(pack.source(" F1 ")).isPresent();
        assertThat(pack.source("F9")).isEmpty();
        assertThat(pack.source(null)).isEmpty();
    }

    @Test
    void a_field_cited_together_with_its_record_is_the_field() {
        assertThat(EvidencePack.normalizeToken("R1:F3")).isEqualTo("F3");
        assertThat(EvidencePack.normalizeToken("[R1] [F3]")).isEqualTo("F3");
        assertThat(EvidencePack.normalizeToken("F3 (R1)")).isEqualTo("F3");
        assertThat(EvidencePack.normalizeToken("E1/P1.2")).isEqualTo("P1.2");
        assertThat(EvidencePack.normalizeToken("R2")).isEqualTo("R2");
        assertThat(EvidencePack.normalizeToken("Order 4711")).isNull();
    }

    @Test
    void relations_are_sources_between_records() {
        var pack = EvidencePack.builder().record(ORDER).record(DELIVERY)
                .relation(new RelationInput(204L, 1L, 4711L, "Order 4711", "delivered by", 2L, 88L, "Delivery 88", LocalDate.of(2026, 9, 1), null))
                .build("", EvidencePack.DEFAULT_BUDGET);

        Source relation = pack.source("L1").orElseThrow();
        assertThat(relation.type()).isEqualTo(CitationType.RELATION);
        assertThat(relation.relationId()).isEqualTo(204L);
        assertThat(relation.text()).isEqualTo("Order 4711 — delivered by → Delivery 88 (from 2026-09-01)");
    }

    @Test
    void email_paragraphs_are_sources_and_the_sender_address_is_masked() {
        var pack = EvidencePack.builder().record(ORDER)
                .email(email(7L, LocalDate.of(2026, 9, 4), "Delay notice", "Your shipment is held at customs.", "We expect it on 2026-09-07."))
                .build("", EvidencePack.DEFAULT_BUDGET);

        Source paragraph = pack.source("P1.2").orElseThrow();
        assertThat(paragraph.type()).isEqualTo(CitationType.EMAIL_PARAGRAPH);
        assertThat(paragraph.emailId()).isEqualTo(7L);
        assertThat(paragraph.paragraphIndex()).isEqualTo(1);
        assertThat(paragraph.paragraphHash()).hasSize(16);
        assertThat(paragraph.text()).isEqualTo("We expect it on 2026-09-07.");
        assertThat(pack.emailIds()).containsExactly(7L);
        assertThat(pack.render()).contains("E1 2026-09-04 · from Carrier Support (carrier.example) · \"Delay notice\"")
                .contains("[P1.1] Your shipment is held at customs.")
                .doesNotContain("support@carrier.example");
    }

    @Test
    void the_users_own_mail_is_marked_as_sent_by_them() {
        var sent = new EmailInput(8L, LocalDate.of(2026, 9, 1), "Tom", "tom@example.com", true, "Where is order 4711?",
                EmailParagraphs.split("Where is it?", null));

        assertThat(EvidencePack.builder().email(sent).build("", EvidencePack.DEFAULT_BUDGET).render())
                .contains("E1 2026-09-01 · sent by you · \"Where is order 4711?\"");
    }

    @Test
    void emails_that_do_not_fit_the_budget_are_left_out_most_relevant_first() {
        var customs = email(1L, LocalDate.of(2026, 9, 1), "Customs hold", "The shipment is held at customs. " + "x".repeat(150));
        var newsletter = email(2L, LocalDate.of(2026, 9, 9), "Newsletter", "Our autumn offers are here. " + "y".repeat(150));
        var records = EvidencePack.builder().record(ORDER).build("", EvidencePack.DEFAULT_BUDGET).render().length();

        var pack = EvidencePack.builder().record(ORDER).email(newsletter).email(customs)
                .build("Why was the customs shipment late?", records + 420);

        assertThat(pack.emailIds()).containsExactly(1L);
        assertThat(pack.render()).contains("[1 email left out]");
    }

    @Test
    void without_a_prompt_the_newest_emails_come_first() {
        var older = email(1L, LocalDate.of(2026, 9, 1), "Old", "old text " + "x".repeat(150));
        var newer = email(2L, LocalDate.of(2026, 9, 9), "New", "new text " + "y".repeat(150));
        var records = EvidencePack.builder().record(ORDER).build("", EvidencePack.DEFAULT_BUDGET).render().length();

        assertThat(EvidencePack.builder().record(ORDER).email(older).email(newer).build("", records + 420).emailIds()).containsExactly(2L);
    }

    @Test
    void the_dates_of_fields_and_emails_are_the_timeline() {
        var pack = EvidencePack.builder().record(ORDER).record(DELIVERY)
                .email(email(7L, LocalDate.of(2026, 9, 4), "Delay notice", "Held at customs."))
                .build("", EvidencePack.DEFAULT_BUDGET);

        assertThat(pack.timeline()).extracting(Source::token).containsExactly("F1", "P1.1", "F3");
    }

    @Test
    void related_emails_and_other_records_are_listed_apart_and_the_records_own_emails_come_first() {
        var linked = email(1L, LocalDate.of(2026, 9, 1), "Order", "Your order has shipped.");
        var related = new EmailInput(2L, LocalDate.of(2026, 9, 9), "Other Customer", "x@other.example", false, "Wrong item again",
                EmailParagraphs.split("Again the wrong discs were delivered.", null), true);
        var otherOrder = new RecordInput(1L, "Orders", 4800L, "Order 4800", false, List.of(), true);

        var pack = EvidencePack.builder().record(ORDER).record(otherOrder).email(related).email(linked)
                .build("wrong discs delivered", EvidencePack.DEFAULT_BUDGET);

        assertThat(pack.emailIds()).containsExactly(1L, 2L);
        String text = pack.render();
        assertThat(text.indexOf("Other records (found through related emails):"))
                .isGreaterThan(text.indexOf("[R1] Orders: Order 4711")).isLessThan(text.indexOf("Order 4800"));
        assertThat(text.indexOf("Related emails from the rest of the mailbox"))
                .isGreaterThan(text.indexOf("Your order has shipped.")).isLessThan(text.indexOf("Again the wrong discs"));
        assertThat(pack.source("R2").orElseThrow().recordTitle()).isEqualTo("Order 4800");
    }

    private static EmailInput email(long id, LocalDate date, String subject, String... paragraphs) {
        return new EmailInput(id, date, "Carrier Support", "support@carrier.example", false, subject,
                EmailParagraphs.split(String.join("\n\n", paragraphs), null));
    }
}

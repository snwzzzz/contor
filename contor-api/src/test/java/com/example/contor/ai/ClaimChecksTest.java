package com.example.contor.ai;

import com.example.contor.ai.ClaimChecks.Quote;
import com.example.contor.ai.EvidencePack.EmailInput;
import com.example.contor.ai.EvidencePack.FieldInput;
import com.example.contor.ai.EvidencePack.RecordInput;
import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClaimChecksTest {

    // R1 Order 4711, F1 expected 2026-09-02, F2 total 1.250,00 EUR | R2 Delivery 88, F3 delivered 2026-09-07 | P1.1, P1.2
    private static final EvidencePack PACK = EvidencePack.builder()
            .record(new RecordInput(1L, "Orders", 4711L, "Order 4711", true, List.of(
                    new FieldInput("expected_delivery", "Expected delivery", FieldType.DATE, "2026-09-02"),
                    new FieldInput("total", "Total", FieldType.MONEY, Map.of("amount", 1250.00, "currency", "EUR")))))
            .record(new RecordInput(2L, "Deliveries", 88L, "Delivery 88", false, List.of(
                    new FieldInput("delivered_at", "Delivered at", FieldType.DATE, "2026-09-07"))))
            .email(new EmailInput(7L, LocalDate.of(2026, 9, 4), "Carrier Support", "support@carrier.example", false, "Delay notice",
                    EmailParagraphs.split("Your shipment is held at customs – we’ll release it on 06.09.2026.\n\n"
                            + "The invoice of €1,250 stays unchanged.", null)))
            .build("", EvidencePack.DEFAULT_BUDGET);

    @Test
    void a_claim_whose_facts_are_in_its_sources_passes() {
        var result = ClaimChecks.check("The order was due on 2026-09-02 and arrived on 2026-09-07.", List.of("F1", "F3"), List.of(), PACK);

        assertThat(result.passed()).isTrue();
        assertThat(result.refs()).containsExactly("F1", "F3");
    }

    @Test
    void unknown_and_duplicate_refs_are_dropped() {
        var result = ClaimChecks.check("It was held at customs.", List.of("[p1.1]", "X9", "P1.1"), List.of(), PACK);

        assertThat(result.passed()).isTrue();
        assertThat(result.refs()).containsExactly("P1.1");
    }

    @Test
    void a_claim_without_a_valid_source_fails() {
        assertThat(ClaimChecks.check("It was late.", List.of(), List.of(), PACK).problems()).containsExactly("cites no source");
        assertThat(ClaimChecks.check("It was late.", List.of("Z1"), List.of(), PACK).passed()).isFalse();
        assertThat(ClaimChecks.check(" ", List.of("F1"), List.of(), PACK).passed()).isFalse();
    }

    @Test
    void a_number_that_is_not_in_the_cited_sources_fails() {
        var result = ClaimChecks.check("The total was 1,300 EUR.", List.of("F2"), List.of(), PACK);

        assertThat(result.passed()).isFalse();
        assertThat(result.problems()).containsExactly("1,300 is not in its sources");
    }

    @Test
    void numbers_match_across_formats() {
        assertThat(ClaimChecks.check("The invoice is 1.250,00 €.", List.of("F2"), List.of(), PACK).passed()).isTrue();
        assertThat(ClaimChecks.check("The invoice of €1250 stays unchanged.", List.of("P1.2"), List.of(), PACK).passed()).isTrue();
    }

    @Test
    void a_number_only_in_an_uncited_source_fails() {
        assertThat(ClaimChecks.check("The total was 1,250 EUR.", List.of("F1"), List.of(), PACK).passed()).isFalse();
    }

    @Test
    void single_digit_counts_are_not_checked() {
        assertThat(ClaimChecks.check("2 records mention the delivery.", List.of("F3"), List.of(), PACK).passed()).isTrue();
    }

    @Test
    void a_date_must_be_in_the_cited_sources_in_any_written_form() {
        assertThat(ClaimChecks.check("The release was planned for September 6, 2026.", List.of("P1.1"), List.of(), PACK).passed()).isTrue();
        assertThat(ClaimChecks.check("The release was planned for 2026-09-06.", List.of("P1.1"), List.of(), PACK).passed()).isTrue();
        var wrong = ClaimChecks.check("The release was planned for 2026-09-08.", List.of("P1.1"), List.of(), PACK);
        assertThat(wrong.problems()).containsExactly("2026-09-08 is not in its sources");
    }

    @Test
    void a_date_written_without_a_year_takes_the_year_of_its_email() {
        var pack = EvidencePack.builder()
                .email(new EmailInput(9L, LocalDate.of(2026, 9, 4), "Dispatch", "dispatch@carrier.example", false, "Delivery",
                        EmailParagraphs.split("Arrival Wednesday 29.07. 09:00.\n\nThe rest follows by 24 July, the latest on July 31.", null)))
                .build("", EvidencePack.DEFAULT_BUDGET);

        assertThat(ClaimChecks.check("It arrived on 2026-07-29.", List.of("P1.1"), List.of(), pack).passed()).isTrue();
        assertThat(ClaimChecks.check("The rest was due by 2026-07-24.", List.of("P1.2"), List.of(), pack).passed()).isTrue();
        assertThat(ClaimChecks.check("The rest was due by 2026-07-31 at the latest.", List.of("P1.2"), List.of(), pack).passed()).isTrue();
        assertThat(ClaimChecks.check("It arrived on 2025-07-29.", List.of("P1.1"), List.of(), pack).passed()).isFalse();
        assertThat(ClaimChecks.check("It arrived on 2026-07-30.", List.of("P1.1"), List.of(), pack).passed()).isFalse();
    }

    @Test
    void a_time_of_day_is_not_a_date() {
        var pack = EvidencePack.builder()
                .email(new EmailInput(9L, LocalDate.of(2026, 9, 4), "Dispatch", "dispatch@carrier.example", false, "Delivery",
                        EmailParagraphs.split("Arrival at 11.30. Gate 2.", null)))
                .build("", EvidencePack.DEFAULT_BUDGET);

        assertThat(ClaimChecks.check("It arrived on 2026-11-30.", List.of("P1.1"), List.of(), pack).passed()).isFalse();
    }

    @Test
    void a_number_of_days_between_two_cited_dates_is_allowed() {
        assertThat(ClaimChecks.check("It arrived 5 days late.", List.of("F1", "F3"), List.of(), PACK).passed()).isTrue();
        assertThat(ClaimChecks.check("It arrived 15 days late.", List.of("F1", "F3"), List.of(), PACK).passed()).isFalse();
    }

    @Test
    void a_verbatim_quote_is_kept_and_its_paragraph_counts_as_a_source() {
        var result = ClaimChecks.check("It was held at customs.", List.of(),
                List.of(new Quote("P1.1", "held at customs - we'll release it")), PACK);

        assertThat(result.passed()).isTrue();
        assertThat(result.quotes()).extracting(Quote::token).containsExactly("P1.1");
        assertThat(result.refs()).containsExactly("P1.1");
    }

    @Test
    void a_quote_that_is_not_verbatim_is_dropped() {
        var result = ClaimChecks.check("It was held at customs.", List.of("P1.1"),
                List.of(new Quote("P1.1", "stuck in customs"), new Quote("F1", "2026-09-02")), PACK);

        assertThat(result.passed()).isTrue();
        assertThat(result.quotes()).isEmpty();
    }
}

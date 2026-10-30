package com.example.contor.ai;

import com.example.contor.ai.EvidencePack.EmailInput;
import com.example.contor.ai.EvidencePack.FieldInput;
import com.example.contor.ai.EvidencePack.RecordInput;
import com.example.contor.ai.References.Quote;
import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReferencesTest {

    // R1 Wrong item SO-26-0155: F1 State = open, F2 Severity = medium | E1 (#7) "Falsche Ware": P1.1, P1.2
    private static final EvidencePack PACK = EvidencePack.builder()
            .record(new RecordInput(10L, "Complaints", 5L, "Wrong item SO-26-0155", true, List.of(
                    new FieldInput("state", "State", FieldType.STATUS, "open"),
                    new FieldInput("severity", "Severity", FieldType.ENUM, "medium"))))
            .email(new EmailInput(7L, LocalDate.of(2026, 9, 29), "Sabine Wendt", "s@kessler.example", false, "Falsche Ware",
                    EmailParagraphs.split("In der Lieferung waren Trennscheiben 125x2,5 statt 125x1,0.\n\nBitte tauschen Sie die 5 Pack.", null)))
            .build("", EvidencePack.DEFAULT_BUDGET);

    @Test
    void a_sentence_keeps_its_text_and_its_known_references() {
        var resolved = References.resolve("The wrong discs were delivered.", List.of("P1.1", "X9", "[p1.1]"), List.of(), PACK);

        assertThat(resolved.text()).isEqualTo("The wrong discs were delivered.");
        assertThat(resolved.refs()).containsExactly("P1.1");
    }

    @Test
    void a_sentence_without_references_is_kept_as_it_is() {
        var resolved = References.resolve("A double check before packing would catch this.", List.of(), List.of(), PACK);

        assertThat(resolved.text()).isEqualTo("A double check before packing would catch this.");
        assertThat(resolved.refs()).isEmpty();
    }

    @Test
    void nothing_is_checked_or_removed_numbers_and_dates_included() {
        var resolved = References.resolve("It arrived on 2026-12-24 with 999 packs.", List.of("P1.1"), List.of(), PACK);

        assertThat(resolved.text()).isEqualTo("It arrived on 2026-12-24 with 999 packs.");
        assertThat(resolved.refs()).containsExactly("P1.1");
    }

    @Test
    void a_whole_email_stands_for_its_paragraphs() {
        assertThat(References.resolve("The customer complained.", List.of("E1"), List.of(), PACK).refs()).containsExactly("P1.1", "P1.2");
    }

    @Test
    void codes_written_in_the_text_become_references_and_readable_names() {
        var resolved = References.resolve("Record R1 shows that F1 is still open; email P1.2 asks for an exchange.", List.of(), List.of(), PACK);

        assertThat(resolved.text())
                .isEqualTo("Wrong item SO-26-0155 shows that the State is still open; the email “Falsche Ware” asks for an exchange.");
        assertThat(resolved.refs()).containsExactly("R1", "F1", "P1.2");
    }

    @Test
    void a_code_at_the_start_of_the_sentence_starts_with_a_capital_letter() {
        assertThat(References.resolve("P1.1 confirms the mix-up.", List.of(), List.of(), PACK).text())
                .isEqualTo("The email “Falsche Ware” confirms the mix-up.");
    }

    @Test
    void codes_in_brackets_are_removed_and_unknown_codes_disappear() {
        var resolved = References.resolve("The discs were wrong (P1.1, R1:F2) [F9].", List.of(), List.of(), PACK);

        assertThat(resolved.text()).isEqualTo("The discs were wrong.");
        assertThat(resolved.refs()).containsExactly("P1.1", "F2");
    }

    @Test
    void a_verbatim_quote_is_kept_and_its_paragraph_becomes_a_reference() {
        var resolved = References.resolve("The customer asked for an exchange.", List.of(),
                List.of(new Quote("P1.2", "bitte tauschen sie die 5 pack"), new Quote("P1.1", "made up words")), PACK);

        assertThat(resolved.quotes()).extracting(Quote::token).containsExactly("P1.2");
        assertThat(resolved.refs()).containsExactly("P1.2");
    }

    @Test
    void a_figure_row_stands_for_its_records_and_reads_as_its_label() {
        var pack = EvidencePack.builder().analysis(new EvidencePack.AnalysisInput("Orders by customer", "3 orders", List.of("#", "Customer", "Orders"),
                2, List.of(new EvidencePack.AnalysisRow(List.of("1", "Kessler GmbH", "2"), List.of(
                        new EvidencePack.RowSource(5L, "Orders", 1L, "SO-1", null, null, null),
                        new EvidencePack.RowSource(5L, "Orders", 2L, "SO-2", null, null, null)))), null))
                .build("", EvidencePack.DEFAULT_BUDGET);

        var resolved = References.resolve("A1.1 orders most (A1.1).", List.of("[A1.1]"), List.of(), pack);

        assertThat(resolved.text()).isEqualTo("Kessler GmbH orders most.");
        assertThat(resolved.refs()).hasSize(2);
    }

    @Test
    void a_code_like_word_that_is_no_figure_row_is_left_alone() {
        assertThat(References.resolve("Print it on A4 paper.", List.of(), List.of(), PACK).text()).isEqualTo("Print it on A4 paper.");
    }

    @Test
    void app_items_are_references_too() {
        var pack = EvidencePack.builder()
                .appItem(new EvidencePack.AppItemInput(9L, 7L, "Shop", "Orders", "SO-155", "{}", LocalDate.of(2026, 10, 28)))
                .build("", EvidencePack.DEFAULT_BUDGET);

        var resolved = References.resolve("Order SO-155 shipped yesterday (D1).", List.of(), List.of(), pack);

        assertThat(resolved.text()).isEqualTo("Order SO-155 shipped yesterday.");
        assertThat(resolved.refs()).containsExactly("D1");
    }
}

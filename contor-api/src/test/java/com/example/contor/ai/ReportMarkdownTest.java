package com.example.contor.ai;

import com.example.contor.ai.ReportDraft.Block;
import com.example.contor.ai.ReportDraft.Claim;
import com.example.contor.ai.ReportDraft.Section;
import com.example.contor.domain.ReportBlockKind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReportMarkdownTest {

    @Test
    void the_text_before_the_first_heading_is_an_untitled_section_split_into_sentences() {
        var document = ReportMarkdown.parse("""
                The wrong discs were shipped [P2.1]. The replacement came on 23.07. Die Ware kam am Donnerstag [P1.1, F3]!

                A second check would catch this.""");

        assertThat(document.title()).isEmpty();
        assertThat(document.sections()).singleElement().satisfies(section -> {
            assertThat(section.title()).isEmpty();
            assertThat(section.blocks()).extracting(Block::kind).containsExactly(ReportBlockKind.PARAGRAPH, ReportBlockKind.PARAGRAPH);
            assertThat(section.blocks().getFirst().items()).containsExactly(
                    new Claim("The wrong discs were shipped.", List.of("P2.1")),
                    new Claim("The replacement came on 23.07. Die Ware kam am Donnerstag!", List.of("P1.1", "F3")));
            assertThat(section.blocks().get(1).items()).containsExactly(new Claim("A second check would catch this.", List.of()));
        });
    }

    @Test
    void headings_start_sections_and_a_leading_top_heading_is_the_title() {
        var document = ReportMarkdown.parse("""
                # Damaged goods SO-26-0156

                The box was crushed [P1.1].

                ## Possible causes
                - Rough handling.

                **Next steps**
                1. Review the packaging.""");

        assertThat(document.title()).isEqualTo("Damaged goods SO-26-0156");
        assertThat(document.sections()).extracting(Section::title).containsExactly("", "Possible causes", "Next steps");
    }

    @Test
    void bullet_and_numbered_lists_are_one_item_per_line_with_continuations() {
        var document = ReportMarkdown.parse("""
                ## Reasons
                1. **Packaging:** the box was too weak [P1.1].
                2. Rough handling by the carrier,
                   for example when stacking.
                   - a nested point
                3) Time pressure.

                * Agree on a backup carrier.
                + Check the packing station.""");

        List<Block> blocks = document.sections().getFirst().blocks();
        assertThat(blocks).extracting(Block::kind).containsExactly(ReportBlockKind.NUMBERED, ReportBlockKind.BULLETS);
        assertThat(blocks.getFirst().items()).containsExactly(
                new Claim("**Packaging:** the box was too weak.", List.of("P1.1")),
                new Claim("Rough handling by the carrier, for example when stacking.", List.of()),
                new Claim("a nested point", List.of()),
                new Claim("Time pressure.", List.of()));
        assertThat(blocks.get(1).items()).extracting(Claim::text).containsExactly("Agree on a backup carrier.", "Check the packing station.");
    }

    @Test
    void a_table_has_its_header_as_columns_and_a_claim_per_cell() {
        var document = ReportMarkdown.parse("""
                | Customer | Orders | Revenue |
                |:---|---:|---|
                | Kessler Maschinenbau | 12 [R2] | 14,300 EUR [F5] |
                | Ateliers Moreau | 3 |
                """);

        Block table = document.sections().getFirst().blocks().getFirst();
        assertThat(table.kind()).isEqualTo(ReportBlockKind.TABLE);
        assertThat(table.columns()).containsExactly("Customer", "Orders", "Revenue");
        assertThat(table.rows()).containsExactly(
                List.of(new Claim("Kessler Maschinenbau", List.of()), new Claim("12", List.of("R2")), new Claim("14,300 EUR", List.of("F5"))),
                List.of(new Claim("Ateliers Moreau", List.of()), new Claim("3", List.of()), new Claim("", List.of())));
    }

    @Test
    void a_table_without_a_separator_row_has_no_header() {
        var document = ReportMarkdown.parse("| Pro | Con |\n| fast | expensive |");

        Block table = document.sections().getFirst().blocks().getFirst();
        assertThat(table.columns()).isEmpty();
        assertThat(table.rows()).hasSize(2);
    }

    @Test
    void a_quote_block_keeps_its_text_and_code() {
        var document = ReportMarkdown.parse("> „in der Lieferung waren Trennscheiben 125x2,5“ [P2.1]\n> statt 125x1,0");

        Block quote = document.sections().getFirst().blocks().getFirst();
        assertThat(quote.kind()).isEqualTo(ReportBlockKind.QUOTE);
        assertThat(quote.items()).containsExactly(new Claim("„in der Lieferung waren Trennscheiben 125x2,5“ statt 125x1,0", List.of("P2.1")));
    }

    @Test
    void rules_fences_and_empty_headings_are_ignored() {
        var document = ReportMarkdown.parse("---\n```\nText.\n```\n##\n***\n");

        assertThat(document.sections()).singleElement().satisfies(section ->
                assertThat(section.blocks()).singleElement().satisfies(block ->
                        assertThat(block.items()).containsExactly(new Claim("Text.", List.of()))));
    }

    @Test
    void empty_sections_are_dropped_and_everything_is_capped() {
        String items = String.join("\n", Collections.nCopies(40, "- a"));
        String rows = String.join("\n", Collections.nCopies(60, "| a | b | c | d | e | f | g | h | i | j |"));

        var document = ReportMarkdown.parse("## Empty\n\n## Items\n" + items + "\n\n## Table\n" + rows);

        assertThat(document.sections()).extracting(Section::title).containsExactly("Items", "Table");
        assertThat(document.sections().getFirst().blocks().getFirst().items()).hasSize(ReportMarkdown.MAX_ITEMS);
        Block table = document.sections().get(1).blocks().getFirst();
        assertThat(table.rows()).hasSize(ReportMarkdown.MAX_ROWS);
        assertThat(table.rows().getFirst()).hasSize(ReportMarkdown.MAX_COLUMNS);
    }

    @Test
    void blank_input_is_an_empty_document() {
        assertThat(ReportMarkdown.parse("  ").sections()).isEmpty();
        assertThat(ReportMarkdown.parse(null).sections()).isEmpty();
    }
}

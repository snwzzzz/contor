package com.example.contor.ai;

import com.example.contor.ai.EmailParagraphs.Paragraph;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmailParagraphsTest {

    @Test
    void a_plain_body_is_split_on_blank_lines_and_numbered() {
        var paragraphs = EmailParagraphs.split("Your order 4711 has shipped.\n\nIt will arrive on 2026-09-02.\n\nBest regards, Shop", null);

        assertThat(paragraphs).extracting(Paragraph::index).containsExactly(0, 1, 2);
        assertThat(paragraphs).extracting(Paragraph::text)
                .containsExactly("Your order 4711 has shipped.", "It will arrive on 2026-09-02.", "Best regards, Shop");
    }

    @Test
    void lines_of_one_paragraph_stay_together() {
        assertThat(EmailParagraphs.split("Your shipment is held\nat customs pending documents.", null)).extracting(Paragraph::text)
                .containsExactly("Your shipment is held\nat customs pending documents.");
    }

    @Test
    void an_empty_body_has_no_paragraphs() {
        assertThat(EmailParagraphs.split(null, null)).isEmpty();
        assertThat(EmailParagraphs.split(" \n ", "  ")).isEmpty();
    }

    @Test
    void a_short_greeting_joins_the_next_paragraph() {
        assertThat(EmailParagraphs.split("Hi Tom,\n\nyour order has shipped.\n\nAnna", null)).extracting(Paragraph::text)
                .containsExactly("Hi Tom,\nyour order has shipped.", "Anna");
    }

    @Test
    void quoted_history_and_the_signature_are_not_paragraphs() {
        var body = "Friday works.\n\n--\nAnna Berger\nAcme\n\nOn Mon, Oct 5, 2026 at 10:00 AM Anna <anna@acme.com> wrote:\n> Can we meet?";

        assertThat(EmailParagraphs.split(body, null)).extracting(Paragraph::text).containsExactly("Friday works.");
    }

    @Test
    void list_items_are_paragraphs_of_their_own() {
        assertThat(EmailParagraphs.split("Please send the documents.\n- invoice copy\n- customs form\n- proof of payment", null))
                .extracting(Paragraph::text)
                .containsExactly("Please send the documents.", "- invoice copy", "- customs form", "- proof of payment");
    }

    @Test
    void an_html_only_body_is_converted_to_text() {
        var paragraphs = EmailParagraphs.split(null, "<p>Your order has shipped.</p><p>Tracking: 123</p>");

        assertThat(paragraphs).extracting(Paragraph::text).containsExactly("Your order has shipped.", "Tracking: 123");
    }

    @Test
    void the_text_body_wins_over_the_html_body() {
        assertThat(EmailParagraphs.split("Plain text.", "<p>HTML text.</p>")).extracting(Paragraph::text).containsExactly("Plain text.");
    }

    @Test
    void a_very_long_paragraph_is_split_at_a_sentence_end() {
        var sentence = "This sentence is part of a very long paragraph about the delivery. ";
        var body = sentence.repeat(30).strip();

        var paragraphs = EmailParagraphs.split(body, null);

        assertThat(paragraphs).hasSizeGreaterThan(1);
        assertThat(paragraphs).allSatisfy(paragraph -> {
            assertThat(paragraph.text().length()).isLessThanOrEqualTo(EmailParagraphs.MAX_PARAGRAPH_CHARS);
            assertThat(paragraph.text()).endsWith(".");
        });
        assertThat(String.join(" ", paragraphs.stream().map(Paragraph::text).toList())).isEqualTo(body);
    }

    @Test
    void the_hash_ignores_case_and_whitespace_but_not_the_words() {
        var first = EmailParagraphs.split("Held at  customs.", null).getFirst();
        var same = EmailParagraphs.split("held at\ncustoms.", null).getFirst();
        var other = EmailParagraphs.split("Released from customs.", null).getFirst();

        assertThat(first.hash()).isEqualTo(same.hash()).hasSize(16);
        assertThat(first.hash()).isNotEqualTo(other.hash());
    }

    @Test
    void locates_a_quote_ignoring_case_whitespace_and_typographic_quotes() {
        List<Paragraph> paragraphs = EmailParagraphs.split(
                "Your order has shipped.\n\nYour shipment is held\nat customs – we’ll send the documents.", null);

        assertThat(EmailParagraphs.locate(paragraphs, "held at customs")).contains(1);
        assertThat(EmailParagraphs.locate(paragraphs, "  HELD AT   CUSTOMS - we'll ")).contains(1);
        assertThat(EmailParagraphs.locate(paragraphs, "your order")).contains(0);
    }

    @Test
    void a_quote_that_is_not_in_the_email_is_not_located() {
        List<Paragraph> paragraphs = EmailParagraphs.split("Your order has shipped.", null);

        assertThat(EmailParagraphs.locate(paragraphs, "delivered late")).isEmpty();
        assertThat(EmailParagraphs.locate(paragraphs, " ")).isEmpty();
        assertThat(EmailParagraphs.locate(paragraphs, null)).isEmpty();
    }
}

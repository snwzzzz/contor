package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmailTextCleanerTest {

    @Test
    void a_plain_body_is_kept() {
        assertThat(EmailTextCleaner.clean("Hello Alex,\nyour order has shipped.\n\nBest, Shop", 1000))
                .isEqualTo("Hello Alex,\nyour order has shipped.\n\nBest, Shop");
    }

    @Test
    void null_and_blank_become_empty() {
        assertThat(EmailTextCleaner.clean(null, 100)).isEmpty();
        assertThat(EmailTextCleaner.clean("  \n \n", 100)).isEmpty();
    }

    @Test
    void quoted_lines_are_dropped() {
        assertThat(EmailTextCleaner.clean("Sounds good!\n> Can we meet on Friday?\n>> Earlier text\nSee you.", 1000))
                .isEqualTo("Sounds good!\nSee you.");
    }

    @Test
    void everything_after_an_english_reply_header_is_dropped() {
        assertThat(EmailTextCleaner.clean("Thanks, Friday works.\n\nOn Mon, Oct 5, 2026 at 10:00 AM Anna Berger <anna@acme.com> wrote:\nCan we meet?\nAnna", 1000))
                .isEqualTo("Thanks, Friday works.");
    }

    @Test
    void everything_after_a_german_reply_header_is_dropped() {
        assertThat(EmailTextCleaner.clean("Passt, bis Freitag!\n\nAm 05.10.2026 um 10:00 schrieb Anna Berger <anna@acme.com>:\nKönnen wir uns treffen?", 1000))
                .isEqualTo("Passt, bis Freitag!");
    }

    @Test
    void forwarded_message_headers_cut_the_history() {
        assertThat(EmailTextCleaner.clean("Please see below.\n\n-----Original Message-----\nFrom: Anna\nSent: Monday\nOld text", 1000)).isEqualTo("Please see below.");
        assertThat(EmailTextCleaner.clean("Siehe unten.\n\n-----Ursprüngliche Nachricht-----\nVon: Anna\nAlter Text", 1000)).isEqualTo("Siehe unten.");
        assertThat(EmailTextCleaner.clean("Looks fine.\n\nFrom: Anna Berger <anna@acme.com>\nSent: Monday, October 5, 2026 10:00\nTo: Alex\nSubject: Meeting\nOld text", 1000))
                .isEqualTo("Looks fine.");
        assertThat(EmailTextCleaner.clean("Sieht gut aus.\n\nVon: Anna Berger\nGesendet: Montag, 5. Oktober 2026\nAn: Alex\nBetreff: Termin\nAlter Text", 1000))
                .isEqualTo("Sieht gut aus.");
    }

    @Test
    void a_from_line_alone_is_ordinary_text() {
        assertThat(EmailTextCleaner.clean("From: the desk of the manager\nPlease read on.", 1000)).isEqualTo("From: the desk of the manager\nPlease read on.");
    }

    @Test
    void the_signature_after_the_delimiter_is_dropped() {
        assertThat(EmailTextCleaner.clean("See you Friday.\n-- \nAnna Berger\nAcme Corp\n+43 660 1234567", 1000)).isEqualTo("See you Friday.");
        assertThat(EmailTextCleaner.clean("See you Friday.\n--\nAnna", 1000)).isEqualTo("See you Friday.");
    }

    @Test
    void mobile_footers_are_dropped() {
        assertThat(EmailTextCleaner.clean("On my way.\nSent from my iPhone", 1000)).isEqualTo("On my way.");
        assertThat(EmailTextCleaner.clean("Bin unterwegs.\nGesendet von meinem iPhone", 1000)).isEqualTo("Bin unterwegs.");
    }

    @Test
    void wrote_inside_a_sentence_is_not_a_reply_header() {
        assertThat(EmailTextCleaner.clean("She wrote: thanks a lot.\nMore text.", 1000)).isEqualTo("She wrote: thanks a lot.\nMore text.");
    }

    @Test
    void blank_runs_and_spaces_are_collapsed() {
        assertThat(EmailTextCleaner.clean("Hello   Alex,\n\n\n\n  thanks\t\tfor   this.\n", 1000)).isEqualTo("Hello Alex,\n\nthanks for this.");
    }

    @Test
    void a_mail_that_only_quotes_becomes_empty() {
        assertThat(EmailTextCleaner.clean("> old\n> older", 1000)).isEmpty();
    }

    @Test
    void long_text_is_cut_at_a_word_boundary_within_the_budget() {
        String cleaned = EmailTextCleaner.clean("alpha beta gamma delta epsilon", 14);

        assertThat(cleaned).isEqualTo("alpha beta");
        assertThat(EmailTextCleaner.clean("short", 14)).isEqualTo("short");
        assertThat(EmailTextCleaner.clean("abcdefghijklmnop", 5)).isEqualTo("abcde");
    }
}

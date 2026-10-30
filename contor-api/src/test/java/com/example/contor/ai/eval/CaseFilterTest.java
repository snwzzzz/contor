package com.example.contor.ai.eval;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CaseFilterTest {

    private static final List<String> IDS = List.of("invoice-de-due", "invoice-en-paid", "meeting-en-friday", "trip-de-train");

    @Test
    void no_filter_selects_everything() {
        assertThat(CaseFilter.of(null).isAll()).isTrue();
        assertThat(CaseFilter.of("  ").select(IDS, Function.identity())).isEqualTo(IDS);
    }

    @Test
    void an_exact_id_selects_that_case() {
        assertThat(CaseFilter.of("trip-de-train").select(IDS, Function.identity())).containsExactly("trip-de-train");
    }

    @Test
    void star_and_question_mark_are_globs() {
        assertThat(CaseFilter.of("invoice-*").select(IDS, Function.identity())).containsExactly("invoice-de-due", "invoice-en-paid");
        assertThat(CaseFilter.of("*-de-*").select(IDS, Function.identity())).containsExactly("invoice-de-due", "trip-de-train");
        assertThat(CaseFilter.of("invoice-?n-paid").select(IDS, Function.identity())).containsExactly("invoice-en-paid");
    }

    @Test
    void a_comma_list_selects_the_union_in_corpus_order() {
        assertThat(CaseFilter.of("trip-*, invoice-de-due").select(IDS, Function.identity()))
                .containsExactly("invoice-de-due", "trip-de-train");
    }

    @Test
    void matching_ignores_case_and_other_regex_characters_are_literal() {
        assertThat(CaseFilter.of("MEETING-*").select(IDS, Function.identity())).containsExactly("meeting-en-friday");
        assertThat(CaseFilter.of("invoice.de.due").select(IDS, Function.identity())).isEmpty();
    }

    @Test
    void select_or_skip_aborts_the_test_when_nothing_matches() {
        assertThatThrownBy(() -> CaseFilter.of("nothing-*").selectOrSkip(IDS, Function.identity()))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("nothing-*");
        assertThat(CaseFilter.of("trip-*").selectOrSkip(IDS, Function.identity())).containsExactly("trip-de-train");
    }
}

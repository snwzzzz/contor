package com.example.contor.ai;

import com.example.contor.domain.ReportBlockKind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReportDraftTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void the_document_is_parsed_into_sections_with_the_title_and_the_timeline_choice() {
        var draft = ReportDraft.parse(mapper.readTree("""
                {"document": "It arrived broken [P1.1].\\n\\n## Reasons\\n1. Weak box.\\n2. Rough handling.",
                 "title": " Damaged goods ", "timeline": true}"""));

        assertThat(draft.title()).isEqualTo("Damaged goods");
        assertThat(draft.timeline()).isTrue();
        assertThat(draft.sections()).extracting(ReportDraft.Section::title).containsExactly("", "Reasons");
        assertThat(draft.sections().get(1).blocks().getFirst().kind()).isEqualTo(ReportBlockKind.NUMBERED);
        assertThat(draft.isEmpty()).isFalse();
    }

    @Test
    void a_top_heading_is_the_title_when_the_model_gave_none() {
        var draft = ReportDraft.parse(mapper.readTree("""
                {"document": "# Top customers\\n\\n| Customer | Orders |\\n|---|---|\\n| A | 3 |", "title": ""}"""));

        assertThat(draft.title()).isEqualTo("Top customers");
        assertThat(draft.timeline()).as("no timeline unless asked for").isFalse();
    }

    @Test
    void a_missing_document_is_empty() {
        var draft = ReportDraft.parse(mapper.readTree("{\"title\": \"x\"}"));

        assertThat(draft.sections()).isEmpty();
        assertThat(draft.isEmpty()).isTrue();
    }

    @Test
    void the_document_comes_first_in_the_schema() {
        String schema = mapper.valueToTree(ReportDraft.schema()).toString();

        assertThat(schema.indexOf("\"document\"")).isPositive().isLessThan(schema.indexOf("\"title\""));
        assertThat(schema).contains("\"timeline\"");
    }

    @Test
    void codes_and_ranges_written_into_a_sentence_become_refs() {
        var draft = ReportDraft.parse(mapper.readTree("""
                {"document": "It was opened on 2026-10-02 (R1, F1-F3). The truck broke down [E3; P4.2]. \
                Delivered on time (as agreed). Review the carrier (E3:P3.1, R1:F5)."}"""));

        assertThat(draft.sections().getFirst().blocks().getFirst().items()).containsExactly(
                new ReportDraft.Claim("It was opened on 2026-10-02.", List.of("R1", "F1", "F2", "F3")),
                new ReportDraft.Claim("The truck broke down.", List.of("E3", "P4.2")),
                new ReportDraft.Claim("Delivered on time (as agreed).", List.of()),
                new ReportDraft.Claim("Review the carrier.", List.of("E3:P3.1", "R1:F5")));
    }
}

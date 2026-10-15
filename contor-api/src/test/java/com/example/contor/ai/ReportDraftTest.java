package com.example.contor.ai;

import com.example.contor.domain.FindingSeverity;
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
    void parses_every_section() {
        var draft = ReportDraft.parse(mapper.readTree("""
                {"title": " Order 4711 ",
                 "summary": [{"text": "It was late.", "refs": ["F1", "[F3]"]}],
                 "findings": [{"title": "Customs hold", "severity": "problem",
                               "claims": [{"text": "Held at customs.", "refs": ["P1.1"]}],
                               "quotes": [{"ref": "P1.1", "text": "held at customs"}]}],
                 "openQuestions": [{"text": "Is the invoice corrected?", "refs": ["P2.1"]}],
                 "nextSteps": [{"text": "Ask for the corrected invoice.", "refs": ["P2.1"]}],
                 "customSections": [{"title": "Payments", "claims": [{"text": "Not paid yet.", "refs": ["F4"]}]}]}"""));

        assertThat(draft.title()).isEqualTo("Order 4711");
        assertThat(draft.summary()).containsExactly(new ReportDraft.Claim("It was late.", List.of("F1", "F3")));
        var finding = draft.findings().getFirst();
        assertThat(finding.severity()).isEqualTo(FindingSeverity.PROBLEM);
        assertThat(finding.quotes()).containsExactly(new ClaimChecks.Quote("P1.1", "held at customs"));
        assertThat(draft.openQuestions()).hasSize(1);
        assertThat(draft.nextSteps()).hasSize(1);
        assertThat(draft.customSections().getFirst().title()).isEqualTo("Payments");
    }

    @Test
    void missing_parts_are_empty_and_blank_claims_are_dropped() {
        var draft = ReportDraft.parse(mapper.readTree("""
                {"summary": [{"text": "  ", "refs": ["F1"]}, {"text": "Fine.", "refs": "F1"}],
                 "findings": [{"title": "", "claims": [{"text": "x", "refs": ["F1"]}]}, {"title": "Odd", "severity": "WEIRD", "claims": []}]}"""));

        assertThat(draft.title()).isEmpty();
        assertThat(draft.summary()).containsExactly(new ReportDraft.Claim("Fine.", List.of()));
        assertThat(draft.findings()).as("a finding needs a title and a claim").isEmpty();
        assertThat(draft.openQuestions()).isEmpty();
        assertThat(draft.customSections()).isEmpty();
    }

    @Test
    void an_unknown_severity_is_neutral_and_the_model_cannot_claim_a_conflict() {
        var draft = ReportDraft.parse(mapper.readTree("""
                {"findings": [{"title": "A", "severity": "WEIRD", "claims": [{"text": "a", "refs": ["F1"]}]},
                              {"title": "B", "severity": "CONFLICT", "claims": [{"text": "b", "refs": ["F1"]}]}]}"""));

        assertThat(draft.findings()).extracting(ReportDraft.Finding::severity).containsExactly(FindingSeverity.NEUTRAL, FindingSeverity.NEUTRAL);
    }

    @Test
    void sources_written_into_the_sentence_become_refs_and_leave_the_text() {
        var draft = ReportDraft.parse(mapper.readTree("""
                {"summary": [{"text": "It was opened on 2026-10-02 and is still open (R1, F1-F3).", "refs": []},
                             {"text": "The truck broke down near Rosenheim [E3; P4.2].", "refs": ["F9"]},
                             {"text": "Delivered on time (as agreed) by the carrier.", "refs": ["F1"]},
                             {"text": "Review the carrier's performance (E3:P3.1, R1:F5).", "refs": []}]}"""));

        assertThat(draft.summary()).containsExactly(
                new ReportDraft.Claim("It was opened on 2026-10-02 and is still open.", List.of("R1", "F1", "F2", "F3")),
                new ReportDraft.Claim("The truck broke down near Rosenheim.", List.of("F9", "E3", "P4.2")),
                new ReportDraft.Claim("Delivered on time (as agreed) by the carrier.", List.of("F1")),
                new ReportDraft.Claim("Review the carrier's performance.", List.of("E3:P3.1", "R1:F5")));
    }

    @Test
    void lists_are_capped() {
        String claims = String.join(",", java.util.Collections.nCopies(20, "{\"text\": \"a\", \"refs\": [\"F1\"]}"));
        String findings = String.join(",", java.util.Collections.nCopies(20, "{\"title\": \"t\", \"claims\": [" + claims + "]}"));

        var draft = ReportDraft.parse(mapper.readTree("{\"summary\": [" + claims + "], \"findings\": [" + findings + "]}"));

        assertThat(draft.summary()).hasSize(ReportDraft.MAX_SUMMARY);
        assertThat(draft.findings()).hasSize(ReportDraft.MAX_FINDINGS);
        assertThat(draft.findings().getFirst().claims()).hasSize(ReportDraft.MAX_FINDING_CLAIMS);
    }
}

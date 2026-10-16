package com.example.contor.mapper;

import com.example.contor.api.model.CitationKind;
import com.example.contor.api.model.ClaimCheck;
import com.example.contor.api.model.FindingLevel;
import com.example.contor.api.model.RecordRefDto;
import com.example.contor.api.model.ReportBlockDto;
import com.example.contor.api.model.ReportBlockType;
import com.example.contor.api.model.ReportClaimDto;
import com.example.contor.api.model.ReportDto;
import com.example.contor.api.model.ReportSectionType;
import com.example.contor.api.model.ReportSourceDto;
import com.example.contor.api.model.ReportState;
import com.example.contor.api.model.SourceState;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.ClaimVerification;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.FindingSeverity;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportBlock;
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.domain.ReportCitation;
import com.example.contor.domain.ReportClaim;
import com.example.contor.domain.ReportFinding;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.ReportSection;
import com.example.contor.domain.ReportSectionKind;
import com.example.contor.domain.ReportStatus;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ReportMapperTest {

    private static final ReportMapper.SourceLookup CURRENT = new ReportMapper.SourceLookup() {
        @Override
        public SourceState state(ReportCitation citation) {
            return citation.getType() == CitationType.FIELD ? SourceState.CHANGED : SourceState.CURRENT;
        }

        @Override
        public String recordTitle(Long collectionId, Long recordId) {
            return recordId == 4711L ? "Order 4711" : null;
        }
    };

    @Test
    void maps_a_report_with_visible_sentences_sources_quotes_and_removed_sentences() {
        Report report = report();

        ReportDto dto = ReportMapper.toDto(report, List.of(), CURRENT);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getCollectionId()).isEqualTo(1L);
        assertThat(dto.getRecordId()).isEqualTo(4711L);
        assertThat(dto.getState()).isEqualTo(ReportState.READY);
        assertThat(dto.getRecords()).containsExactly(new RecordRefDto(1L, 4711L));
        assertThat(dto.getEmailCount()).isEqualTo(1);
        assertThat(dto.getSections()).extracting(section -> section.getType())
                .containsExactly(ReportSectionType.SUMMARY, ReportSectionType.FINDINGS);
        var summary = dto.getSections().getFirst().getClaims();
        assertThat(summary).hasSize(1);
        assertThat(summary.getFirst().getText()).isEqualTo("It was due on 2026-09-02.");
        assertThat(summary.getFirst().getCheck()).isEqualTo(ClaimCheck.SUPPORTED);
        assertThat(summary.getFirst().getMarkers()).containsExactly(1);

        var finding = dto.getSections().get(1).getFindings().getFirst();
        assertThat(finding.getLevel()).isEqualTo(FindingLevel.PROBLEM);
        assertThat(finding.getClaims().getFirst().getMarkers()).as("a supporting quote is no marker").containsExactly(2);
        assertThat(finding.getQuotes()).singleElement().satisfies(quote -> {
            assertThat(quote.getMarker()).isEqualTo(2);
            assertThat(quote.getText()).isEqualTo("held at customs");
        });

        assertThat(dto.getRemovedClaims()).singleElement().satisfies(removed -> {
            assertThat(removed.getText()).isEqualTo("Invented.");
            assertThat(removed.getNote()).isEqualTo("cites no source");
        });

        assertThat(dto.getSources()).extracting(ReportSourceDto::getMarker).containsExactly(1, 2);
        ReportSourceDto field = dto.getSources().getFirst();
        assertThat(field.getKind()).isEqualTo(CitationKind.FIELD);
        assertThat(field.getLabel()).isEqualTo("Expected delivery");
        assertThat(field.getFieldName()).isEqualTo("Expected delivery");
        assertThat(field.getRecordTitle()).isEqualTo("Order 4711");
        assertThat(field.getValue()).isEqualTo("2026-09-02");
        assertThat(field.getSourceState()).isEqualTo(SourceState.CHANGED);
        ReportSourceDto paragraph = dto.getSources().get(1);
        assertThat(paragraph.getKind()).isEqualTo(CitationKind.EMAIL_PARAGRAPH);
        assertThat(paragraph.getLabel()).isEqualTo("Delay notice");
        assertThat(paragraph.getEmailFrom()).isEqualTo("Carrier Support");
        assertThat(paragraph.getEmailId()).isEqualTo(7L);
        assertThat(paragraph.getParagraphIndex()).isZero();
        assertThat(paragraph.getQuote()).isEqualTo("Your shipment is held at customs.");
    }

    @Test
    void an_unavailable_email_shows_no_text() {
        ReportMapper.SourceLookup hidden = new ReportMapper.SourceLookup() {
            @Override
            public SourceState state(ReportCitation citation) {
                return citation.getType() == CitationType.EMAIL_PARAGRAPH ? SourceState.UNAVAILABLE : SourceState.CURRENT;
            }

            @Override
            public String recordTitle(Long collectionId, Long recordId) {
                return null;
            }
        };

        ReportSourceDto paragraph = ReportMapper.toDto(report(), List.of(), hidden).getSources().get(1);

        assertThat(paragraph.getQuote()).isNull();
        assertThat(paragraph.getEmailSubject()).isNull();
        assertThat(paragraph.getEmailId()).isNull();
        assertThat(paragraph.getLabel()).isEqualTo("Email in another mailbox");
    }

    @Test
    void a_summary_has_the_first_sentence_and_the_follow_up_count() {
        var summary = ReportMapper.toSummary(report(), 2);

        assertThat(summary.getId()).isEqualTo(5L);
        assertThat(summary.getTitle()).isEqualTo("Order 4711");
        assertThat(summary.getSummary()).isEqualTo("It was due on 2026-09-02.");
        assertThat(summary.getFollowUpCount()).isEqualTo(2);
        assertThat(summary.getClaimsVerified()).isEqualTo(2);
    }

    @Test
    void maps_blocks_with_items_and_table_rows() {
        Report report = report();
        report.getSections().clear();
        ReportSection section = ReportSection.builder().id(4L).report(report).position(0).kind(ReportSectionKind.CUSTOM).title("Top customers").build();
        ReportBlock list = ReportBlock.builder().id(41L).section(section).position(0).kind(ReportBlockKind.BULLETS).build();
        list.getClaims().add(ReportClaim.builder().id(51L).section(section).block(list).position(0).text("Kessler orders most.")
                .verification(ClaimVerification.SUPPORTED).build());
        ReportBlock table = ReportBlock.builder().id(42L).section(section).position(1).kind(ReportBlockKind.TABLE)
                .columns(List.of("Customer", "Orders")).build();
        ReportClaim orders = ReportClaim.builder().id(54L).section(section).block(table).position(1).text("12").rowIndex(0).columnIndex(1)
                .verification(ClaimVerification.SUPPORTED).build();
        orders.getCitations().add(ReportCitation.builder().claim(orders).marker(1).type(CitationType.RECORD)
                .collection(report.getCollection()).recordId(4711L).build());
        table.getClaims().addAll(List.of(
                ReportClaim.builder().id(53L).section(section).block(table).position(0).text("Kessler").rowIndex(0).columnIndex(0)
                        .verification(ClaimVerification.SUPPORTED).build(),
                orders,
                ReportClaim.builder().id(55L).section(section).block(table).position(2).text("Moreau").rowIndex(1).columnIndex(0)
                        .verification(ClaimVerification.SUPPORTED).build(),
                ReportClaim.builder().id(56L).section(section).block(table).position(3).text("").rowIndex(1).columnIndex(1)
                        .verification(ClaimVerification.SUPPORTED).build()));
        section.getBlocks().addAll(List.of(list, table));
        report.getSections().add(section);

        ReportDto dto = ReportMapper.toDto(report, List.of(), CURRENT);

        var mapped = dto.getSections().getFirst();
        assertThat(mapped.getType()).isEqualTo(ReportSectionType.CUSTOM);
        assertThat(mapped.getBlocks()).extracting(ReportBlockDto::getType).containsExactly(ReportBlockType.BULLETS, ReportBlockType.TABLE);
        assertThat(mapped.getBlocks().getFirst().getItems()).extracting(ReportClaimDto::getText).containsExactly("Kessler orders most.");
        var mappedTable = mapped.getBlocks().get(1);
        assertThat(mappedTable.getColumns()).containsExactly("Customer", "Orders");
        assertThat(mappedTable.getRows()).hasSize(2);
        assertThat(mappedTable.getRows().getFirst()).extracting(ReportClaimDto::getText).containsExactly("Kessler", "12");
        assertThat(mappedTable.getRows().getFirst().get(1).getMarkers()).containsExactly(1);
        assertThat(dto.getSources()).extracting(ReportSourceDto::getMarker).containsExactly(1);
        assertThat(ReportMapper.toSummary(report, 0).getSummary()).as("the first sentence of any block").isEqualTo("Kessler orders most.");
    }

    @Test
    void a_collection_report_has_no_record_and_shows_its_filters_and_search() {
        Collection orders = Collection.builder().id(new Collection.CollectionId(1L)).name("Orders").fields(new ArrayList<>()).build();
        Report report = Report.builder().id(new Report.ReportId(6L)).collection(orders).status(ReportStatus.QUEUED)
                .scope(ReportScope.ofCollection(new ReportScope.CollectionScope(List.of("state:eq:delivered"), "acme"))).build();
        report.setCreatedAt(Instant.parse("2026-10-15T10:00:00Z"));

        ReportDto dto = ReportMapper.toDto(report, List.of(), CURRENT);

        assertThat(dto.getRecordId()).isNull();
        assertThat(dto.getCollectionId()).isEqualTo(1L);
        assertThat(dto.getRecords()).isEmpty();
        assertThat(dto.getFilters()).containsExactly("state:eq:delivered");
        assertThat(dto.getSearch()).isEqualTo("acme");
    }

    @Test
    void a_record_report_has_no_filters() {
        assertThat(ReportMapper.toDto(report(), List.of(), CURRENT).getFilters()).isEmpty();
    }

    @Test
    void null_in_null_out() {
        assertThat(ReportMapper.toDto(null, List.of(), CURRENT)).isNull();
        assertThat(ReportMapper.toSummary(null, 0)).isNull();
    }

    private static Report report() {
        Collection orders = Collection.builder().id(new Collection.CollectionId(1L)).name("Orders").fields(new ArrayList<>()).build();
        orders.getFields().add(CollectionField.builder().collection(orders).slug(new Slug("expected_delivery")).name("Expected delivery")
                .type(FieldType.DATE).build());
        EmailMessage email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(7L)).subject("Delay notice").fromName("Carrier Support")
                .fromAddress("support@carrier.example").receivedAt(Instant.parse("2026-09-04T08:00:00Z")).build();
        Report report = Report.builder().id(new Report.ReportId(5L)).collection(orders).recordId(4711L).title("Order 4711")
                .prompt("What went wrong?").status(ReportStatus.READY)
                .scope(new ReportScope(List.of(new RecordRef(1L, 4711L)), 0, List.of(7L)))
                .claimsTotal(3).claimsVerified(2).claimsRemoved(1).build();
        report.setCreatedAt(Instant.parse("2026-10-14T10:00:00Z"));

        ReportSection summary = ReportSection.builder().id(1L).report(report).position(0).kind(ReportSectionKind.SUMMARY).title("Executive summary").build();
        ReportClaim due = ReportClaim.builder().id(11L).section(summary).position(0).text("It was due on 2026-09-02.")
                .verification(ClaimVerification.SUPPORTED).build();
        due.getCitations().add(ReportCitation.builder().claim(due).marker(1).type(CitationType.FIELD).collection(orders).recordId(4711L)
                .fieldSlug("expected_delivery").valueSnapshot(Map.of("value", "2026-09-02")).build());
        ReportClaim invented = ReportClaim.builder().id(12L).section(summary).position(1).text("Invented.")
                .verification(ClaimVerification.REMOVED).verifierNote("cites no source").build();
        summary.getClaims().addAll(List.of(due, invented));

        ReportSection findings = ReportSection.builder().id(2L).report(report).position(1).kind(ReportSectionKind.FINDINGS).title("Key findings").build();
        ReportFinding customs = ReportFinding.builder().id(21L).section(findings).position(0).title("Customs hold").severity(FindingSeverity.PROBLEM).build();
        ReportClaim held = ReportClaim.builder().id(13L).section(findings).finding(customs).position(0).text("It was held at customs.")
                .verification(ClaimVerification.SUPPORTED).build();
        held.getCitations().add(ReportCitation.builder().claim(held).marker(2).type(CitationType.EMAIL_PARAGRAPH).email(email)
                .paragraphIndex(0).paragraphHash("abc").segmenterVersion(1).quote("Your shipment is held at customs.").build());
        held.getCitations().add(ReportCitation.builder().claim(held).marker(2).type(CitationType.EMAIL_PARAGRAPH).email(email)
                .paragraphIndex(0).paragraphHash("abc").segmenterVersion(1).quote("held at customs").supportingQuote(true).build());
        customs.getClaims().add(held);
        findings.getFindings().add(customs);

        ReportSection empty = ReportSection.builder().id(3L).report(report).position(2).kind(ReportSectionKind.NEXT_STEPS).title("Next steps").build();
        report.getSections().addAll(List.of(summary, findings, empty));
        return report;
    }
}

package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.ClaimVerification;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.FindingSeverity;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportCitation;
import com.example.contor.domain.ReportClaim;
import com.example.contor.domain.ReportFinding;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.ReportSection;
import com.example.contor.domain.ReportSectionKind;
import com.example.contor.domain.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class ReportRepositoryTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportCitationRepository reportCitationRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Collection orders;
    private AppUser owner;
    private EmailMessage email;

    @BeforeEach
    void setUp() {
        orders = collectionRepository.save(Fixtures.collection("orders"));
        owner = appUserRepository.save(Fixtures.user("owner"));
        email = emailMessageRepository.save(Fixtures.email(mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com")), "AAMk-1"));
    }

    @Test
    void stores_a_report_with_its_sections_findings_claims_and_citations() {
        var report = report(4711L, null);
        var summary = section(report, 0, ReportSectionKind.SUMMARY, "Executive summary");
        var summaryClaim = claim(summary, null, 0, "The order was delivered five days late.");
        summaryClaim.getCitations().add(ReportCitation.builder().claim(summaryClaim).marker(1).type(CitationType.FIELD)
                .collection(orders).recordId(4711L).fieldSlug("expected_delivery").valueSnapshot(Map.of("value", "2026-09-02")).build());
        var findings = section(report, 1, ReportSectionKind.FINDINGS, "Key findings");
        var finding = ReportFinding.builder().section(findings).position(0).title("Late delivery").severity(FindingSeverity.PROBLEM)
                .eventDate(Instant.parse("2026-09-07T00:00:00Z")).build();
        findings.getFindings().add(finding);
        var findingClaim = ReportClaim.builder().section(findings).finding(finding).position(0).text("A customs hold delayed it.")
                .verification(ClaimVerification.SUPPORTED).build();
        finding.getClaims().add(findingClaim);
        findingClaim.getCitations().add(ReportCitation.builder().claim(findingClaim).marker(2).type(CitationType.EMAIL_PARAGRAPH)
                .email(email).paragraphIndex(1).paragraphHash("ab12").segmenterVersion(1).quote("held at customs").supportingQuote(true).build());

        reportRepository.saveAndFlush(report);
        entityManager.clear();

        var reloaded = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.QUEUED);
        assertThat(reloaded.getScope().records()).containsExactly(new RecordRef(orders.getId().id(), 4711L));
        assertThat(reloaded.getSections()).extracting(ReportSection::getKind)
                .containsExactly(ReportSectionKind.SUMMARY, ReportSectionKind.FINDINGS);
        var reloadedSummary = reloaded.getSections().getFirst();
        assertThat(reloadedSummary.getClaims()).extracting(ReportClaim::getText).containsExactly("The order was delivered five days late.");
        assertThat(reloadedSummary.getClaims().getFirst().getCitations().getFirst().getValueSnapshot()).containsEntry("value", "2026-09-02");
        var reloadedFindings = reloaded.getSections().get(1);
        assertThat(reloadedFindings.getClaims()).as("finding claims are not section-level claims").isEmpty();
        var reloadedFinding = reloadedFindings.getFindings().getFirst();
        assertThat(reloadedFinding.getSeverity()).isEqualTo(FindingSeverity.PROBLEM);
        var citation = reloadedFinding.getClaims().getFirst().getCitations().getFirst();
        assertThat(citation.getType()).isEqualTo(CitationType.EMAIL_PARAGRAPH);
        assertThat(citation.getEmail().getId()).isEqualTo(email.getId());
        assertThat(citation.getQuote()).isEqualTo("held at customs");
        assertThat(citation.isSupportingQuote()).isTrue();
    }

    @Test
    void lists_the_top_level_reports_of_a_record_newest_first() {
        var older = reportRepository.saveAndFlush(report(4711L, null));
        var newer = report(4711L, null);
        newer.setCreatedAt(older.getCreatedAt().plusSeconds(60));
        reportRepository.saveAndFlush(newer);
        reportRepository.saveAndFlush(report(4711L, newer));
        reportRepository.saveAndFlush(report(4712L, null));

        var reports = reportRepository.findByCollection_IdAndRecordIdAndParentReportIsNullOrderByCreatedAtDesc(orders.getId(), 4711L);

        assertThat(reports).extracting(Report::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void finds_the_reports_still_being_generated() {
        var drafting = report(4711L, null);
        drafting.setStatus(ReportStatus.DRAFTING);
        reportRepository.saveAndFlush(drafting);
        var ready = report(4712L, null);
        ready.setStatus(ReportStatus.READY);
        reportRepository.saveAndFlush(ready);

        assertThat(reportRepository.findByStatusIn(ReportStatus.ACTIVE)).extracting(Report::getId).containsExactly(drafting.getId());
    }

    @Test
    void finds_the_follow_ups_of_a_report() {
        var parent = reportRepository.saveAndFlush(report(4711L, null));
        var followUp = reportRepository.saveAndFlush(report(4711L, parent));

        assertThat(reportRepository.findByParentReport_IdOrderByCreatedAtAsc(parent.getId())).extracting(Report::getId)
                .containsExactly(followUp.getId());
    }

    @Test
    void deleting_a_report_removes_its_children_and_follow_ups() {
        var report = report(4711L, null);
        var summary = section(report, 0, ReportSectionKind.SUMMARY, "Executive summary");
        var claim = claim(summary, null, 0, "Delivered.");
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(1).type(CitationType.RECORD).collection(orders).recordId(4711L).build());
        reportRepository.saveAndFlush(report);
        reportRepository.saveAndFlush(report(4711L, report));
        entityManager.clear();

        reportRepository.delete(reportRepository.findById(report.getId()).orElseThrow());
        reportRepository.flush();

        assertThat(count("report")).isZero();
        assertThat(count("report_section")).isZero();
        assertThat(count("report_claim")).isZero();
        assertThat(count("report_citation")).isZero();
    }

    @Test
    void a_paragraph_citation_needs_its_paragraph() {
        var report = report(4711L, null);
        var claim = claim(section(report, 0, ReportSectionKind.SUMMARY, "Executive summary"), null, 0, "Delayed.");
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(1).type(CitationType.EMAIL_PARAGRAPH).email(email).build());

        assertThatThrownBy(() -> reportRepository.saveAndFlush(report))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_report_citation_target");
    }

    @Test
    void a_field_citation_needs_its_field() {
        var report = report(4711L, null);
        var claim = claim(section(report, 0, ReportSectionKind.SUMMARY, "Executive summary"), null, 0, "Late.");
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(1).type(CitationType.FIELD).collection(orders).recordId(4711L).build());

        assertThatThrownBy(() -> reportRepository.saveAndFlush(report))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_report_citation_target");
    }

    @Test
    void finds_the_reports_citing_a_record() {
        var citing = report(1L, null);
        var claim = claim(section(citing, 0, ReportSectionKind.SUMMARY, "Executive summary"), null, 0, "The delivery was late.");
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(1).type(CitationType.FIELD)
                .collection(orders).recordId(4711L).fieldSlug("total").build());
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(2).type(CitationType.RECORD)
                .collection(orders).recordId(4711L).build());
        reportRepository.saveAndFlush(citing);
        reportRepository.saveAndFlush(report(4711L, null));

        assertThat(reportCitationRepository.findReportIdsCiting(orders.getId(), 4711L)).containsExactly(citing.getId());
        assertThat(reportCitationRepository.findReportIdsCiting(orders.getId(), 4712L)).isEmpty();
    }

    private Report report(long recordId, Report parent) {
        return Report.builder()
                .collection(orders)
                .recordId(recordId)
                .parentReport(parent)
                .prompt("What went wrong?")
                .title("Order " + recordId)
                .scope(new ReportScope(List.of(new RecordRef(orders.getId().id(), recordId)), 0, List.of()))
                .createdByUser(owner)
                .build();
    }

    private static ReportSection section(Report report, int position, ReportSectionKind kind, String title) {
        var section = ReportSection.builder().report(report).position(position).kind(kind).title(title).build();
        report.getSections().add(section);
        return section;
    }

    private static ReportClaim claim(ReportSection section, ReportFinding finding, int position, String text) {
        var claim = ReportClaim.builder().section(section).finding(finding).position(position).text(text)
                .verification(ClaimVerification.SUPPORTED).build();
        section.getClaims().add(claim);
        return claim;
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from \"" + table + "\"", Long.class);
    }
}

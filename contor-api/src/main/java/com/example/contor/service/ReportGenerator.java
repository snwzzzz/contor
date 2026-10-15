package com.example.contor.service;

import com.example.contor.ai.EmailParagraphs;
import com.example.contor.ai.EvidencePack;
import com.example.contor.ai.EvidencePack.Source;
import com.example.contor.ai.OllamaException;
import com.example.contor.ai.References;
import com.example.contor.ai.ReportDraft;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
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
import com.example.contor.domain.ReportSection;
import com.example.contor.domain.ReportSectionKind;
import com.example.contor.domain.ReportStatus;
import com.example.contor.exception.ResourceNotFoundException;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Generates one report on the AI thread: collect the sources ({@link ReportScopeService}), let the model answer the
 * request ({@link ReportModelService}) and store every sentence as written, with references to its sources where it
 * has any ({@link References}) — nothing is verified or removed, except a sentence that repeats an earlier one (also one
 * the report already said, for a follow-up). The first section is the answer ("Answer", or "Summary" without a request);
 * the timeline is built from the record's dated sources without the model (not for a follow-up: its report has it). Never throws: a failure marks the report
 * FAILED.
 */
@Slf4j
@Service
public class ReportGenerator {

    static final int MAX_TIMELINE = 15;
    private static final int PARAGRAPH_QUOTE_CHARS = 300;
    private static final int TIMELINE_QUOTE_CHARS = 160;
    private static final String FINDINGS_TITLE = "Key findings";

    private final ReportRepository reportRepository;
    private final CollectionRepository collectionRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final ReportScopeService reportScopeService;
    private final ReportModelService reportModelService;
    private final AiSettingsService aiSettingsService;
    private final AuditService auditService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ReportGenerator(ReportRepository reportRepository, CollectionRepository collectionRepository,
                           EmailMessageRepository emailMessageRepository, ReportScopeService reportScopeService,
                           ReportModelService reportModelService, AiSettingsService aiSettingsService, AuditService auditService,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.reportRepository = reportRepository;
        this.collectionRepository = collectionRepository;
        this.emailMessageRepository = emailMessageRepository;
        this.reportScopeService = reportScopeService;
        this.reportModelService = reportModelService;
        this.aiSettingsService = aiSettingsService;
        this.auditService = auditService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** What a run needs from the stored report, read in one transaction. */
    private record Job(ReportScope scope, String prompt, AppUser.AppUserId owner, List<String> earlier, boolean followUp) {}

    /** One sentence of the answer, where it goes and what it refers to. */
    private record Sentence(ReportSectionKind kind, String sectionTitle, int finding, References.Resolved resolved) {}

    public void generate(Long reportId) {
        try {
            Job job = transactions.execute(status -> {
                Report report = load(reportId);
                report.setStatus(ReportStatus.COLLECTING);
                AppUser.AppUserId owner = report.getCreatedByUser() != null ? report.getCreatedByUser().getId() : null;
                List<String> earlier = report.getParentReport() != null ? sentences(report.getParentReport()) : List.of();
                reportRepository.saveAndFlush(report);
                return new Job(report.getScope(), report.getPrompt(), owner, earlier, report.getParentReport() != null);
            });
            boolean mailAccess = job.owner() != null && aiSettingsService.settingsFor(job.owner()).isMailAccess();
            String prompt = Optional.ofNullable(job.prompt()).orElse("");
            EvidencePack pack = reportScopeService.collect(job.scope(), prompt, job.owner(), mailAccess);
            update(reportId, report -> report.setStatus(ReportStatus.DRAFTING));
            ReportDraft draft = reportModelService.draft(pack, prompt, job.earlier());
            List<Sentence> sentences = sentences(draft, prompt.isBlank(), job.earlier(), pack);
            Map<Integer, List<References.Quote>> quotes = new HashMap<>();
            for (int i = 0; i < draft.findings().size(); i++) {
                quotes.put(i, References.resolve("", List.of(), draft.findings().get(i).quotes(), pack).quotes());
            }
            transactions.executeWithoutResult(status -> store(reportId, job, draft, sentences, quotes, pack));
        } catch (RuntimeException | LinkageError e) {
            // A LinkageError (e.g. a class file replaced by a rebuild while running) must not leave the report in progress.
            log.warn("Report {} failed: {}", reportId, e.getClass().getSimpleName());
            String reason = failure(e);
            try {
                update(reportId, report -> {
                    report.setStatus(ReportStatus.FAILED);
                    report.setFailureReason(reason);
                });
            } catch (RuntimeException ignored) {
                log.warn("Report {} could not be marked as failed", reportId);
            }
        }
    }

    /** The drafted sentences in reading order with their references; repeats (also of {@code earlier}) are left out. */
    private static List<Sentence> sentences(ReportDraft draft, boolean noRequest, List<String> earlier, EvidencePack pack) {
        Set<String> said = new HashSet<>();
        earlier.forEach(text -> said.add(EmailParagraphs.normalize(text)));
        List<Sentence> sentences = new ArrayList<>();
        String answerTitle = noRequest ? "Summary" : "Answer";
        draft.summary().forEach(claim -> add(sentences, said, pack, ReportSectionKind.SUMMARY, answerTitle, -1, claim));
        for (int i = 0; i < draft.findings().size(); i++) {
            int finding = i;
            draft.findings().get(i).claims().forEach(claim -> add(sentences, said, pack, ReportSectionKind.FINDINGS, FINDINGS_TITLE, finding, claim));
        }
        draft.openQuestions().forEach(claim -> add(sentences, said, pack, ReportSectionKind.OPEN_QUESTIONS, "Open questions", -1, claim));
        draft.nextSteps().forEach(claim -> add(sentences, said, pack, ReportSectionKind.NEXT_STEPS, "Recommendations and next steps", -1, claim));
        draft.customSections().forEach(section ->
                section.claims().forEach(claim -> add(sentences, said, pack, ReportSectionKind.CUSTOM, section.title(), -1, claim)));
        return sentences;
    }

    private static void add(List<Sentence> sentences, Set<String> said, EvidencePack pack, ReportSectionKind kind, String title,
                            int finding, ReportDraft.Claim claim) {
        References.Resolved resolved = References.resolve(claim.text(), claim.refs(), List.of(), pack);
        if (!resolved.text().isBlank() && said.add(EmailParagraphs.normalize(resolved.text()))) {
            sentences.add(new Sentence(kind, title, finding, resolved));
        }
    }

    private void store(Long reportId, Job job, ReportDraft draft, List<Sentence> sentences, Map<Integer, List<References.Quote>> quotes,
                       EvidencePack pack) {
        Report report = load(reportId);
        report.getSections().clear();
        Map<String, Integer> markers = new HashMap<>();
        Map<String, ReportSection> sections = new LinkedHashMap<>();

        sentences.stream().filter(sentence -> sentence.kind() == ReportSectionKind.SUMMARY)
                .forEach(sentence -> addClaim(section(report, sections, sentence.kind(), sentence.sectionTitle()), null, sentence, pack,
                        markers, List.of()));
        Map<Integer, ReportFinding> findings = new LinkedHashMap<>();
        for (Sentence sentence : sentences) {
            if (sentence.kind() != ReportSectionKind.FINDINGS) {
                continue;
            }
            ReportSection section = section(report, sections, sentence.kind(), sentence.sectionTitle());
            ReportFinding finding = findings.get(sentence.finding());
            List<References.Quote> supporting = List.of();
            if (finding == null) {
                ReportDraft.Finding drafted = draft.findings().get(sentence.finding());
                finding = ReportFinding.builder().section(section).position(section.getFindings().size())
                        .title(drafted.title()).severity(drafted.severity()).build();
                section.getFindings().add(finding);
                findings.put(sentence.finding(), finding);
                supporting = quotes.getOrDefault(sentence.finding(), List.of());
            }
            addClaim(section, finding, sentence, pack, markers, supporting);
        }
        if (!job.followUp()) {
            timeline(report, sections, pack, markers);
        }
        sentences.stream()
                .filter(sentence -> sentence.kind() != ReportSectionKind.SUMMARY && sentence.kind() != ReportSectionKind.FINDINGS)
                .forEach(sentence -> addClaim(section(report, sections, sentence.kind(), sentence.sectionTitle()), null, sentence, pack,
                        markers, List.of()));

        String anchorTitle = pack.sources().isEmpty() ? null : pack.sources().getFirst().recordTitle();
        report.setTitle(!draft.title().isBlank() ? draft.title() : anchorTitle);
        report.setScope(new ReportScope(job.scope().records(), job.scope().depth(), pack.emailIds()));
        report.setStatus(ReportStatus.READY);
        report.setFailureReason(null);
        report.setModel(reportModelService.model());
        report.setPromptVersion(ReportModelService.PROMPT_VERSION);
        report.setClaimsTotal(sentences.size());
        report.setClaimsVerified((int) sentences.stream().filter(sentence -> !sentence.resolved().refs().isEmpty()).count());
        report.setClaimsRemoved(0);
        report.setGeneratedAt(clock.instant());
        reportRepository.saveAndFlush(report);
        AuditEvent event = job.owner() != null ? AuditEvent.user(AuditAction.REPORT_GENERATED, job.owner().id())
                : AuditEvent.system(AuditAction.REPORT_GENERATED);
        auditService.record(event.entity("report", reportId).after(Map.of(
                "records", job.scope().records().size(), "emails", pack.emailIds().size(), "model", reportModelService.model())));
    }

    /** One finding per dated source of the record (date fields, the first paragraph of each email), oldest first. */
    private void timeline(Report report, Map<String, ReportSection> sections, EvidencePack pack, Map<String, Integer> markers) {
        List<Source> events = pack.timeline().stream().limit(MAX_TIMELINE).toList();
        if (events.isEmpty()) {
            return;
        }
        ReportSection section = section(report, sections, ReportSectionKind.TIMELINE, "Timeline");
        for (Source event : events) {
            ReportFinding finding = ReportFinding.builder().section(section).position(section.getFindings().size())
                    .title(event.date().toString()).severity(FindingSeverity.NEUTRAL)
                    .eventDate(event.date().atStartOfDay(ZoneOffset.UTC).toInstant()).build();
            String text = event.type() == CitationType.EMAIL_PARAGRAPH ? "Email: " + Optional.ofNullable(event.label()).orElse("") : event.text();
            ReportClaim claim = ReportClaim.builder().section(section).finding(finding).position(0).text(text)
                    .verification(ClaimVerification.SUPPORTED).build();
            String quote = event.type() == CitationType.EMAIL_PARAGRAPH ? prefix(event.text(), TIMELINE_QUOTE_CHARS) : null;
            claim.getCitations().add(citation(claim, event, quote, false, markers));
            finding.getClaims().add(claim);
            section.getFindings().add(finding);
        }
    }

    private static ReportSection section(Report report, Map<String, ReportSection> sections, ReportSectionKind kind, String title) {
        return sections.computeIfAbsent(kind + "/" + title, key -> {
            ReportSection section = ReportSection.builder().report(report).position(sections.size()).kind(kind).title(title).build();
            report.getSections().add(section);
            return section;
        });
    }

    private void addClaim(ReportSection section, ReportFinding finding, Sentence sentence, EvidencePack pack, Map<String, Integer> markers,
                          List<References.Quote> supporting) {
        List<ReportClaim> claims = finding != null ? finding.getClaims() : section.getClaims();
        ReportClaim claim = ReportClaim.builder().section(section).finding(finding).position(claims.size()).text(sentence.resolved().text())
                .verification(ClaimVerification.SUPPORTED).build();
        for (String token : sentence.resolved().refs()) {
            pack.source(token).ifPresent(source -> claim.getCitations().add(citation(claim, source, null, false, markers)));
        }
        for (References.Quote quote : supporting) {
            claim.getCitations().add(citation(claim, pack.source(quote.token()).orElseThrow(), quote.text(), true, markers));
        }
        claims.add(claim);
    }

    private ReportCitation citation(ReportClaim claim, Source source, String quote, boolean supporting, Map<String, Integer> markers) {
        int marker = markers.computeIfAbsent(source.token(), token -> markers.size() + 1);
        ReportCitation.ReportCitationBuilder citation = ReportCitation.builder().claim(claim).marker(marker).type(source.type())
                .supportingQuote(supporting);
        if (source.collectionId() != null) {
            citation.collection(collectionRepository.getReferenceById(new Collection.CollectionId(source.collectionId())));
            citation.recordId(source.recordId());
        }
        switch (source.type()) {
            case FIELD -> {
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("value", source.value());
                citation.fieldSlug(source.fieldSlug()).valueSnapshot(snapshot);
            }
            case EMAIL_PARAGRAPH -> citation.email(emailMessageRepository.getReferenceById(new EmailMessage.EmailMessageId(source.emailId())))
                    .paragraphIndex(source.paragraphIndex()).paragraphHash(source.paragraphHash())
                    .segmenterVersion(EmailParagraphs.SEGMENTER_VERSION)
                    .quote(quote != null ? quote : prefix(source.text(), PARAGRAPH_QUOTE_CHARS));
            case RELATION -> citation.relationId(source.relationId());
            case RECORD -> {
            }
        }
        return citation.build();
    }

    /** The sentences of a report, for a follow-up question about it. */
    private static List<String> sentences(Report report) {
        List<String> sentences = new ArrayList<>();
        for (ReportSection section : report.getSections()) {
            if (section.getKind() == ReportSectionKind.TIMELINE) {
                continue;
            }
            section.getClaims().stream().filter(claim -> claim.getVerification() != ClaimVerification.REMOVED)
                    .forEach(claim -> sentences.add(claim.getText()));
            section.getFindings().forEach(finding -> finding.getClaims().stream()
                    .filter(claim -> claim.getVerification() != ClaimVerification.REMOVED)
                    .forEach(claim -> sentences.add(claim.getText())));
        }
        return sentences;
    }

    /** A verbatim beginning of {@code text}, cut at a word boundary. */
    private static String prefix(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max);
        return text.substring(0, cut > 0 ? cut : max);
    }

    private static String failure(Throwable e) {
        if (e instanceof OllamaException) {
            return "The AI model did not answer. Is Ollama running?";
        }
        if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().startsWith("The model")) {
            return e.getMessage();
        }
        if (e instanceof ResourceNotFoundException) {
            return "A record of this report no longer exists.";
        }
        return "Generating the report failed.";
    }

    private Report load(Long reportId) {
        return reportRepository.findById(new Report.ReportId(reportId))
                .orElseThrow(() -> new IllegalStateException("Report " + reportId + " is gone"));
    }

    private void update(Long reportId, Consumer<Report> change) {
        transactions.executeWithoutResult(status -> {
            Report report = load(reportId);
            change.accept(report);
            reportRepository.saveAndFlush(report);
        });
    }
}

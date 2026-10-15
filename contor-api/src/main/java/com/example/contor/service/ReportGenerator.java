package com.example.contor.service;

import com.example.contor.ai.ClaimChecks;
import com.example.contor.ai.EmailParagraphs;
import com.example.contor.ai.EvidencePack;
import com.example.contor.ai.EvidencePack.Source;
import com.example.contor.ai.OllamaException;
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
import com.example.contor.service.ReportModelService.ClaimToRepair;
import com.example.contor.service.ReportModelService.ClaimToVerify;
import com.example.contor.service.ReportModelService.Repaired;
import com.example.contor.service.ReportModelService.Verdict;
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
 * Generates one report (R5) on the AI thread: collect the evidence ({@link ReportScopeService}), draft it
 * ({@link ReportModelService}), fact-check every sentence — the deterministic {@link ClaimChecks} first, then the
 * verifier with only the sentence's own sources —, repair what failed once (and check the rewrite again), turn
 * sources that disagree with each other into a CONFLICT finding, and store the report with numbered citations.
 * Sentences that stay unsupported are kept as REMOVED for the fact-check details and never shown as report text. The
 * timeline is built from the dated sources, without the model. Never throws: a failure marks the report FAILED.
 */
@Slf4j
@Service
public class ReportGenerator {

    static final int MAX_TIMELINE = 15;
    /** Record facts offered to the repair of a sentence that cited nothing. */
    static final int MAX_REPAIR_FACTS = 40;
    /** Sources a rewritten sentence may keep (a rewrite that cites everything it was offered proves nothing). */
    static final int MAX_REPAIR_REFS = 3;
    private static final int PARAGRAPH_QUOTE_CHARS = 300;
    private static final int TIMELINE_QUOTE_CHARS = 160;

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
    private record Job(ReportScope scope, String prompt, AppUser.AppUserId owner, List<String> earlier) {}

    /** One drafted sentence on its way through the fact check. */
    private static final class Work {
        final ReportSectionKind kind;
        final String sectionTitle;
        final int finding;
        final String original;
        final List<String> refs;
        String text;
        List<String> validRefs = List.of();
        ClaimVerification state;
        String note;
        String reason;
        /** The verifier (not a deterministic check) rejected the drafted sentence. */
        boolean rejectedByVerifier;

        Work(ReportSectionKind kind, String sectionTitle, int finding, ReportDraft.Claim claim) {
            this.kind = kind;
            this.sectionTitle = sectionTitle;
            this.finding = finding;
            this.original = claim.text();
            this.text = claim.text();
            this.refs = claim.refs();
        }

        /** Recommendations and open questions are advice grounded in the cited facts, not facts the sources state. */
        boolean suggestion() {
            return kind == ReportSectionKind.NEXT_STEPS || kind == ReportSectionKind.OPEN_QUESTIONS;
        }
    }

    private record Conflict(String text, List<String> refs) {}

    public void generate(Long reportId) {
        try {
            Job job = transactions.execute(status -> {
                Report report = load(reportId);
                report.setStatus(ReportStatus.COLLECTING);
                AppUser.AppUserId owner = report.getCreatedByUser() != null ? report.getCreatedByUser().getId() : null;
                List<String> earlier = report.getParentReport() != null ? sentences(report.getParentReport()) : List.of();
                reportRepository.saveAndFlush(report);
                return new Job(report.getScope(), report.getPrompt(), owner, earlier);
            });
            boolean mailAccess = job.owner() != null && aiSettingsService.settingsFor(job.owner()).isMailAccess();
            String prompt = Optional.ofNullable(job.prompt()).orElse("");
            EvidencePack pack = reportScopeService.collect(job.scope(), prompt, job.owner(), mailAccess);
            update(reportId, report -> report.setStatus(ReportStatus.DRAFTING));
            ReportDraft draft = reportModelService.draft(pack, prompt, job.earlier());
            update(reportId, report -> report.setStatus(ReportStatus.FACT_CHECKING));

            List<Work> work = work(draft);
            Map<Integer, List<ClaimChecks.Quote>> quotes = new HashMap<>();
            for (int i = 0; i < draft.findings().size(); i++) {
                quotes.put(i, ClaimChecks.check("quotes", List.of(), draft.findings().get(i).quotes(), pack).quotes());
            }
            List<Conflict> conflicts = factCheck(work, quotes, pack);
            transactions.executeWithoutResult(status -> store(reportId, job, draft, work, conflicts, quotes, pack));
        } catch (RuntimeException e) {
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

    private List<Work> work(ReportDraft draft) {
        List<Work> work = new ArrayList<>();
        draft.summary().forEach(claim -> work.add(new Work(ReportSectionKind.SUMMARY, "Executive summary", -1, claim)));
        for (int i = 0; i < draft.findings().size(); i++) {
            int finding = i;
            draft.findings().get(i).claims().forEach(claim -> work.add(new Work(ReportSectionKind.FINDINGS, "Key findings", finding, claim)));
        }
        draft.openQuestions().forEach(claim -> work.add(new Work(ReportSectionKind.OPEN_QUESTIONS, "Open questions", -1, claim)));
        draft.nextSteps().forEach(claim -> work.add(new Work(ReportSectionKind.NEXT_STEPS, "Recommendations and next steps", -1, claim)));
        draft.customSections().forEach(section ->
                section.claims().forEach(claim -> work.add(new Work(ReportSectionKind.CUSTOM, section.title(), -1, claim))));
        return work;
    }

    /** Sets every sentence's state: SUPPORTED, REPAIRED or REMOVED; returns the conflicts the verifier found. */
    private List<Conflict> factCheck(List<Work> work, Map<Integer, List<ClaimChecks.Quote>> quotes, EvidencePack pack) {
        List<ClaimToVerify> toVerify = new ArrayList<>();
        List<Integer> toRepair = new ArrayList<>();
        for (int id = 0; id < work.size(); id++) {
            Work sentence = work.get(id);
            ClaimChecks.Result result = ClaimChecks.check(sentence.text, sentence.refs, List.of(), pack);
            List<ClaimChecks.Quote> findingQuotes = quotes.getOrDefault(sentence.finding, List.of());
            if (!result.passed() && !findingQuotes.isEmpty()) {
                // The finding quotes the paragraph the sentence is based on, but the sentence forgot to cite it.
                List<String> withQuotes = new ArrayList<>(sentence.refs);
                findingQuotes.forEach(quote -> withQuotes.add(quote.token()));
                ClaimChecks.Result withQuoted = ClaimChecks.check(sentence.text, withQuotes, List.of(), pack);
                if (withQuoted.passed()) {
                    result = withQuoted;
                }
            }
            sentence.validRefs = result.refs();
            if (result.passed() && sentence.suggestion()) {
                // Advice is never stated in the sources: it stands when the facts it cites pass the deterministic checks.
                sentence.state = ClaimVerification.SUPPORTED;
            } else if (result.passed()) {
                toVerify.add(new ClaimToVerify(id, sentence.text, sources(result.refs(), pack)));
            } else {
                sentence.reason = String.join("; ", result.problems());
                toRepair.add(id);
            }
        }
        List<Conflict> conflicts = new ArrayList<>();
        for (Verdict verdict : verify(toVerify)) {
            Work sentence = work.get(verdict.id());
            if (verdict.kind() == Verdict.Kind.SUPPORTED) {
                sentence.state = ClaimVerification.SUPPORTED;
            } else if (verdict.kind() == Verdict.Kind.CONTRADICTED && verdict.conflictingRefs().size() >= 2) {
                sentence.state = ClaimVerification.REMOVED;
                sentence.note = "Its sources disagree: " + verdict.reason();
                ClaimChecks.Result reason = ClaimChecks.check(verdict.reason(), verdict.conflictingRefs(), List.of(), pack, true);
                conflicts.add(new Conflict(reason.passed() && !verdict.reason().isBlank()
                        ? "The sources disagree: " + verdict.reason()
                        : "The sources cited here disagree with each other.", verdict.conflictingRefs()));
            } else {
                sentence.reason = verdict.reason().isBlank() ? "not supported by its sources" : verdict.reason();
                sentence.rejectedByVerifier = true;
                toRepair.add(verdict.id());
            }
        }
        Map<Integer, Repaired> rewritten = toRepair.isEmpty() ? Map.of() : reportModelService.repair(toRepair.stream()
                .map(id -> new ClaimToRepair(id, work.get(id).text, work.get(id).reason, repairSources(work.get(id), pack), work.get(id).suggestion()))
                .toList());
        List<ClaimToVerify> recheck = new ArrayList<>();
        for (int id : toRepair) {
            Work sentence = work.get(id);
            Repaired repaired = rewritten.get(id);
            ClaimChecks.Result result = repaired == null ? null : ClaimChecks.check(repaired.text(),
                    repaired.refs().isEmpty() ? sentence.refs : repaired.refs().subList(0, Math.min(MAX_REPAIR_REFS, repaired.refs().size())),
                    List.of(), pack);
            boolean unchanged = repaired != null && sentence.rejectedByVerifier
                    && EmailParagraphs.normalize(repaired.text()).equals(EmailParagraphs.normalize(sentence.original));
            if (unchanged) {
                sentence.state = ClaimVerification.REMOVED;
                sentence.note = sentence.reason + "; the rewrite changed nothing";
            } else if (result != null && result.passed() && sentence.suggestion()) {
                sentence.text = repaired.text();
                sentence.validRefs = result.refs();
                sentence.state = ClaimVerification.REPAIRED;
                sentence.note = "Was: \"" + sentence.original + "\" (" + sentence.reason + ")";
            } else if (result != null && result.passed()) {
                sentence.text = repaired.text();
                sentence.validRefs = result.refs();
                recheck.add(new ClaimToVerify(id, repaired.text(), sources(result.refs(), pack)));
            } else {
                sentence.state = ClaimVerification.REMOVED;
                sentence.note = sentence.reason + (result == null ? "" : "; the rewrite still failed: " + String.join("; ", result.problems()));
            }
        }
        for (Verdict verdict : verify(recheck)) {
            Work sentence = work.get(verdict.id());
            if (verdict.kind() == Verdict.Kind.SUPPORTED) {
                sentence.state = ClaimVerification.REPAIRED;
                sentence.note = "Was: \"" + sentence.original + "\" (" + sentence.reason + ")";
            } else {
                sentence.state = ClaimVerification.REMOVED;
                sentence.note = sentence.reason + "; the rewrite was not supported either: " + verdict.reason();
                sentence.text = sentence.original;
            }
        }
        Set<String> said = new HashSet<>();
        for (Work sentence : work) {
            if (sentence.state != ClaimVerification.REMOVED && !said.add(EmailParagraphs.normalize(sentence.text))) {
                sentence.state = ClaimVerification.REMOVED;
                sentence.note = "repeats another sentence of the report";
                sentence.text = sentence.original;
            }
        }
        return conflicts;
    }

    /**
     * What a failed sentence may be rewritten from: its own valid sources, or — when it cited none — the facts of the
     * records and their connections (no email text, to keep the call small).
     */
    private static List<Source> repairSources(Work sentence, EvidencePack pack) {
        if (!sentence.validRefs.isEmpty()) {
            return sources(sentence.validRefs, pack);
        }
        return pack.sources().stream().filter(source -> source.type() != CitationType.EMAIL_PARAGRAPH).limit(MAX_REPAIR_FACTS).toList();
    }

    private List<Verdict> verify(List<ClaimToVerify> claims) {
        return claims.isEmpty() ? List.of() : reportModelService.verify(claims);
    }

    private void store(Long reportId, Job job, ReportDraft draft, List<Work> work, List<Conflict> conflicts,
                       Map<Integer, List<ClaimChecks.Quote>> quotes, EvidencePack pack) {
        Report report = load(reportId);
        report.getSections().clear();
        Map<String, Integer> markers = new HashMap<>();
        Map<String, ReportSection> sections = new LinkedHashMap<>();

        for (Work sentence : work) {
            if (sentence.kind == ReportSectionKind.SUMMARY) {
                addClaim(section(report, sections, sentence.kind, sentence.sectionTitle), null, sentence, pack, markers, List.of());
            }
        }
        Map<Integer, ReportFinding> findings = new LinkedHashMap<>();
        for (Work sentence : work) {
            if (sentence.kind != ReportSectionKind.FINDINGS) {
                continue;
            }
            ReportSection section = section(report, sections, sentence.kind, sentence.sectionTitle);
            ReportFinding finding = null;
            List<ClaimChecks.Quote> supporting = List.of();
            if (sentence.state != ClaimVerification.REMOVED) {
                finding = findings.get(sentence.finding);
                if (finding == null) {
                    ReportDraft.Finding drafted = draft.findings().get(sentence.finding);
                    finding = ReportFinding.builder().section(section).position(section.getFindings().size())
                            .title(drafted.title()).severity(drafted.severity()).build();
                    section.getFindings().add(finding);
                    findings.put(sentence.finding, finding);
                    supporting = quotes.getOrDefault(sentence.finding, List.of());
                }
            }
            addClaim(section, finding, sentence, pack, markers, supporting);
        }
        for (Conflict conflict : conflicts) {
            ReportSection section = section(report, sections, ReportSectionKind.FINDINGS, "Key findings");
            ReportFinding finding = ReportFinding.builder().section(section).position(section.getFindings().size())
                    .title("Sources disagree").severity(FindingSeverity.CONFLICT).build();
            section.getFindings().add(finding);
            ReportClaim claim = ReportClaim.builder().section(section).finding(finding).position(0).text(conflict.text())
                    .verification(ClaimVerification.CONFLICT).build();
            cite(claim, conflict.refs(), List.of(), pack, markers);
            finding.getClaims().add(claim);
        }
        timeline(report, sections, pack, markers);
        for (Work sentence : work) {
            if (sentence.kind != ReportSectionKind.SUMMARY && sentence.kind != ReportSectionKind.FINDINGS) {
                addClaim(section(report, sections, sentence.kind, sentence.sectionTitle), null, sentence, pack, markers, List.of());
            }
        }

        int removed = (int) work.stream().filter(sentence -> sentence.state == ClaimVerification.REMOVED).count();
        String anchorTitle = pack.sources().isEmpty() ? null : pack.sources().getFirst().recordTitle();
        report.setTitle(!draft.title().isBlank() ? draft.title() : anchorTitle);
        report.setScope(new ReportScope(job.scope().records(), job.scope().depth(), pack.emailIds()));
        report.setStatus(ReportStatus.READY);
        report.setFailureReason(null);
        report.setModel(reportModelService.model());
        report.setPromptVersion(ReportModelService.PROMPT_VERSION);
        report.setClaimsTotal(work.size());
        report.setClaimsRemoved(removed);
        report.setClaimsVerified(work.size() - removed);
        report.setGeneratedAt(clock.instant());
        reportRepository.saveAndFlush(report);
        AuditEvent event = job.owner() != null ? AuditEvent.user(AuditAction.REPORT_GENERATED, job.owner().id())
                : AuditEvent.system(AuditAction.REPORT_GENERATED);
        auditService.record(event.entity("report", reportId).after(Map.of(
                "records", job.scope().records().size(), "emails", pack.emailIds().size(), "model", reportModelService.model())));
    }

    /** One finding per dated source (date fields, the first paragraph of each email), oldest first. */
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
            List<ClaimChecks.Quote> quote = event.type() == CitationType.EMAIL_PARAGRAPH
                    ? List.of(new ClaimChecks.Quote(event.token(), prefix(event.text(), TIMELINE_QUOTE_CHARS))) : List.of();
            cite(claim, List.of(event.token()), quote, pack, markers);
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

    private void addClaim(ReportSection section, ReportFinding finding, Work sentence, EvidencePack pack, Map<String, Integer> markers,
                          List<ClaimChecks.Quote> supporting) {
        List<ReportClaim> claims = finding != null ? finding.getClaims() : section.getClaims();
        ReportClaim claim = ReportClaim.builder().section(section).finding(finding).position(claims.size()).text(sentence.text)
                .verification(sentence.state).verifierNote(sentence.note).build();
        if (sentence.state != ClaimVerification.REMOVED) {
            cite(claim, sentence.validRefs, List.of(), pack, markers);
            for (ClaimChecks.Quote quote : supporting) {
                claim.getCitations().add(citation(claim, pack.source(quote.token()).orElseThrow(), quote.text(), true, markers));
            }
        }
        claims.add(claim);
    }

    private void cite(ReportClaim claim, List<String> tokens, List<ClaimChecks.Quote> quotes, EvidencePack pack, Map<String, Integer> markers) {
        for (String token : tokens) {
            pack.source(token).ifPresent(source -> {
                String quote = quotes.stream().filter(q -> q.token().equals(source.token())).map(ClaimChecks.Quote::text).findFirst()
                        .orElse(null);
                claim.getCitations().add(citation(claim, source, quote, false, markers));
            });
        }
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

    private static List<Source> sources(List<String> tokens, EvidencePack pack) {
        return tokens.stream().map(pack::source).flatMap(Optional::stream).toList();
    }

    /** The surviving sentences of a report, for a follow-up question about it. */
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

    private static String failure(RuntimeException e) {
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

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
import com.example.contor.domain.ReportBlock;
import com.example.contor.domain.ReportBlockKind;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Generates one report on the AI thread: collect the sources ({@link ReportScopeService}), let the model write the
 * document ({@link ReportModelService}) and store its sections and blocks (paragraphs, lists, quotes, tables) as
 * written, each sentence, list item and table cell with references to its sources where it has any
 * ({@link References}). Nothing is verified or removed, except a sentence or list item that repeats an earlier one
 * (also one the report already said, for a follow-up). The text before the first heading is the answer ("Answer", or
 * "Summary" without a request); the timeline is built from the record's dated sources without the model, when the
 * model asks for it or there is no request (never for a follow-up: its report has it). Never throws: a failure marks
 * the report FAILED.
 */
@Slf4j
@Service
public class ReportGenerator {

    static final int MAX_TIMELINE = 15;
    private static final int PARAGRAPH_QUOTE_CHARS = 300;
    private static final int TIMELINE_QUOTE_CHARS = 160;
    /** A section the model wrote as a timeline itself; the record's timeline is then not added a second time. */
    private static final Pattern OWN_TIMELINE = Pattern.compile("(?i)timeline|chronolog|zeitverlauf|zeitachse|zeitstrahl");
    private static final Pattern QUOTE_MARKS = Pattern.compile("^[\\s\"'„“”‚‘’«»]+|[\\s\"'„“”‚‘’«»]+$");

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

    /** A section to store: its blocks with resolved references. */
    private record Planned(ReportSectionKind kind, String title, List<PlannedBlock> blocks) {}

    /** A block to store; {@code items} for everything but tables, {@code rows} for tables. */
    private record PlannedBlock(ReportBlockKind kind, List<String> columns, List<References.Resolved> items,
                                List<List<References.Resolved>> rows) {}

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
            List<Planned> planned = plan(draft, prompt.isBlank(), job.earlier(), pack);
            boolean ownTimeline = planned.stream().anyMatch(section -> OWN_TIMELINE.matcher(section.title()).find());
            boolean timeline = !job.followUp() && !ownTimeline && (draft.timeline() || prompt.isBlank());
            transactions.executeWithoutResult(status -> store(reportId, job, draft, planned, timeline, pack));
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

    /**
     * The drafted sections with their references, in reading order. A sentence or list item that repeats an earlier
     * one (also of {@code earlier}) is left out; table cells are kept as they are. Empty blocks and sections go.
     */
    private static List<Planned> plan(ReportDraft draft, boolean noRequest, List<String> earlier, EvidencePack pack) {
        Set<String> said = new HashSet<>();
        earlier.forEach(text -> said.add(EmailParagraphs.normalize(text)));
        List<Planned> sections = new ArrayList<>();
        for (int i = 0; i < draft.sections().size(); i++) {
            ReportDraft.Section section = draft.sections().get(i);
            List<PlannedBlock> blocks = new ArrayList<>();
            for (ReportDraft.Block block : section.blocks()) {
                if (block.kind() == ReportBlockKind.TABLE) {
                    List<List<References.Resolved>> rows = block.rows().stream()
                            .map(row -> row.stream().map(cell -> References.resolve(cell.text(), cell.refs(), List.of(), pack)).toList())
                            .toList();
                    if (!rows.isEmpty()) {
                        blocks.add(new PlannedBlock(block.kind(), block.columns(), List.of(), rows));
                    }
                    continue;
                }
                List<References.Resolved> items = new ArrayList<>();
                for (ReportDraft.Claim item : block.items()) {
                    References.Resolved resolved = References.resolve(item.text(), item.refs(), quotes(block.kind(), item), pack);
                    if (!resolved.text().isBlank() && said.add(EmailParagraphs.normalize(resolved.text()))) {
                        items.add(resolved);
                    }
                }
                if (!items.isEmpty()) {
                    blocks.add(new PlannedBlock(block.kind(), List.of(), items, List.of()));
                }
            }
            if (blocks.isEmpty()) {
                continue;
            }
            boolean answer = i == 0 && section.title().isBlank();
            String title = answer ? (noRequest ? "Summary" : "Answer") : section.title();
            sections.add(new Planned(answer ? ReportSectionKind.SUMMARY : ReportSectionKind.CUSTOM, title, blocks));
        }
        return sections;
    }

    /** A quote block quotes the paragraphs it cites; {@link References} keeps the quote where it is verbatim. */
    private static List<References.Quote> quotes(ReportBlockKind kind, ReportDraft.Claim item) {
        if (kind != ReportBlockKind.QUOTE) {
            return List.of();
        }
        String text = QUOTE_MARKS.matcher(item.text()).replaceAll("").strip();
        return item.refs().stream().map(ref -> new References.Quote(ref, text)).toList();
    }

    private void store(Long reportId, Job job, ReportDraft draft, List<Planned> planned, boolean withTimeline, EvidencePack pack) {
        Report report = load(reportId);
        report.getSections().clear();
        Map<String, Integer> markers = new HashMap<>();
        int total = 0;
        int withSource = 0;
        for (Planned plannedSection : planned) {
            ReportSection section = ReportSection.builder().report(report).position(report.getSections().size())
                    .kind(plannedSection.kind()).title(plannedSection.title()).build();
            report.getSections().add(section);
            for (PlannedBlock plannedBlock : plannedSection.blocks()) {
                ReportBlock block = ReportBlock.builder().section(section).position(section.getBlocks().size()).kind(plannedBlock.kind())
                        .columns(plannedBlock.columns().isEmpty() ? null : plannedBlock.columns()).build();
                section.getBlocks().add(block);
                for (References.Resolved item : plannedBlock.items()) {
                    addClaim(block, item, null, null, pack, markers);
                    total++;
                    withSource += item.refs().isEmpty() ? 0 : 1;
                }
                for (int row = 0; row < plannedBlock.rows().size(); row++) {
                    List<References.Resolved> cells = plannedBlock.rows().get(row);
                    for (int column = 0; column < cells.size(); column++) {
                        addClaim(block, cells.get(column), row, column, pack, markers);
                    }
                    total++;
                    withSource += cells.stream().anyMatch(cell -> !cell.refs().isEmpty()) ? 1 : 0;
                }
            }
        }
        if (withTimeline) {
            timeline(report, pack, markers);
        }

        String anchorTitle = pack.sources().isEmpty() ? null : pack.sources().getFirst().recordTitle();
        report.setTitle(!draft.title().isBlank() ? draft.title() : anchorTitle);
        report.setScope(new ReportScope(job.scope().records(), job.scope().depth(), pack.emailIds()));
        report.setStatus(ReportStatus.READY);
        report.setFailureReason(null);
        report.setModel(reportModelService.model());
        report.setPromptVersion(ReportModelService.PROMPT_VERSION);
        report.setClaimsTotal(total);
        report.setClaimsVerified(withSource);
        report.setClaimsRemoved(0);
        report.setGeneratedAt(clock.instant());
        reportRepository.saveAndFlush(report);
        AuditEvent event = job.owner() != null ? AuditEvent.user(AuditAction.REPORT_GENERATED, job.owner().id())
                : AuditEvent.system(AuditAction.REPORT_GENERATED);
        auditService.record(event.entity("report", reportId).after(Map.of(
                "records", job.scope().records().size(), "emails", pack.emailIds().size(), "model", reportModelService.model())));
    }

    /** One finding per dated source of the record (date fields, the first paragraph of each email), oldest first. */
    private void timeline(Report report, EvidencePack pack, Map<String, Integer> markers) {
        List<Source> events = pack.timeline().stream().limit(MAX_TIMELINE).toList();
        if (events.isEmpty()) {
            return;
        }
        ReportSection section = ReportSection.builder().report(report).position(report.getSections().size())
                .kind(ReportSectionKind.TIMELINE).title("Timeline").build();
        report.getSections().add(section);
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

    private void addClaim(ReportBlock block, References.Resolved resolved, Integer row, Integer column, EvidencePack pack,
                          Map<String, Integer> markers) {
        ReportClaim claim = ReportClaim.builder().section(block.getSection()).block(block).position(block.getClaims().size())
                .text(resolved.text()).rowIndex(row).columnIndex(column).verification(ClaimVerification.SUPPORTED).build();
        Map<String, String> quoted = new HashMap<>();
        resolved.quotes().forEach(quote -> quoted.putIfAbsent(quote.token(), quote.text()));
        for (String token : resolved.refs()) {
            pack.source(token).ifPresent(source -> claim.getCitations().add(citation(claim, source, quoted.get(token), false, markers)));
        }
        block.getClaims().add(claim);
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

    /** The sentences, list items and table rows of a report, for a follow-up question about it. */
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
            for (ReportBlock block : section.getBlocks()) {
                if (block.getKind() != ReportBlockKind.TABLE) {
                    block.getClaims().forEach(claim -> sentences.add(claim.getText()));
                    continue;
                }
                Map<Integer, List<String>> rows = new TreeMap<>();
                block.getClaims().forEach(cell -> rows.computeIfAbsent(cell.getRowIndex(), row -> new ArrayList<>()).add(cell.getText()));
                rows.values().forEach(cells -> sentences.add(String.join(" | ", cells)));
            }
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

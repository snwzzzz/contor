package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.api.model.NewCollectionReportCommand;
import com.example.contor.api.model.NewReportCommand;
import com.example.contor.api.model.NewReportQuestionCommand;
import com.example.contor.api.model.ReportDto;
import com.example.contor.api.model.ReportSummaryDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Collection;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.ReportStatus;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.ReportBusyException;
import com.example.contor.exception.ReportNotFoundException;
import com.example.contor.mapper.ReportMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.persistence.ReportRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

/**
 * Reports on a record (R6) or over a collection's records (C6): create one over a checked scope, ask a follow-up question (a report of its own with the
 * same scope), regenerate, read and delete. Generation runs on the AI thread after commit ({@link ReportGenerator}).
 * Reading marks each cited source as CURRENT, CHANGED (a field's value differs from its snapshot), DELETED or — for
 * emails of another user's mailbox — UNAVAILABLE, so nobody sees mail that is not theirs.
 */
@RequiredArgsConstructor
@Service
public class ReportService {

    public static final int MAX_PROMPT = 500;
    private static final int DEFAULT_DEPTH = 1;

    private final ReportRepository reportRepository;
    private final CollectionRepository collectionRepository;
    private final AppUserRepository appUserRepository;
    private final RelationRepository relationRepository;
    private final RecordService recordService;
    private final ReportScopeService reportScopeService;
    private final ReportGenerator reportGenerator;
    private final AiModelService aiModelService;
    private final AuditService auditService;
    private final AiExecutors aiExecutors;

    @Transactional
    public List<ReportSummaryDto> listRecordReports(Long collectionId, Long recordId) {
        recordService.getRecord(collectionId, recordId);
        return reportRepository.findByCollection_IdAndRecordIdAndParentReportIsNullOrderByCreatedAtDesc(
                        new Collection.CollectionId(collectionId), recordId).stream()
                .map(report -> ReportMapper.toSummary(report, reportRepository.findByParentReport_IdOrderByCreatedAtAsc(report.getId()).size()))
                .toList();
    }

    @Transactional
    public ReportDto createReport(Long collectionId, Long recordId, NewReportCommand command) {
        String prompt = prompt(command.getPrompt(), false);
        requireAi();
        List<RecordRef> connected = Optional.ofNullable(command.getConnectedRecords()).orElse(List.of()).stream()
                .map(ref -> new RecordRef(ref.getCollectionId(), ref.getRecordId()))
                .toList();
        int depth = command.getDepth() != null ? command.getDepth() : connected.isEmpty() ? 0 : DEFAULT_DEPTH;
        ReportScope scope = reportScopeService.scope(new RecordRef(collectionId, recordId), connected, depth);
        Report report = reportRepository.saveAndFlush(Report.builder()
                .collection(collectionRepository.getReferenceById(new Collection.CollectionId(collectionId)))
                .recordId(recordId)
                .prompt(prompt)
                .scope(scope)
                .createdByUser(currentUser())
                .build());
        queue(report.getId().id());
        return ReportMapper.toDto(report, List.of(), lookup());
    }

    @Transactional
    public List<ReportSummaryDto> listCollectionReports(Long collectionId) {
        collection(collectionId);
        return reportRepository.findByCollection_IdAndRecordIdIsNullAndParentReportIsNullOrderByCreatedAtDesc(new Collection.CollectionId(collectionId))
                .stream()
                .map(report -> ReportMapper.toSummary(report, reportRepository.findByParentReport_IdOrderByCreatedAtAsc(report.getId()).size()))
                .toList();
    }

    /** A report over the collection's records that match the table's filters and search (checked like a records query). */
    @Transactional
    public ReportDto createCollectionReport(Long collectionId, NewCollectionReportCommand command) {
        Collection collection = collection(collectionId);
        String prompt = prompt(command.getPrompt(), false);
        ReportScope.CollectionScope view = new ReportScope.CollectionScope(command.getFilters(), command.getSearch());
        recordService.condition(collectionId, view.filters(), view.search());
        requireAi();
        Report report = reportRepository.saveAndFlush(Report.builder()
                .collection(collection)
                .prompt(prompt)
                .scope(ReportScope.ofCollection(view))
                .createdByUser(currentUser())
                .build());
        queue(report.getId().id());
        return ReportMapper.toDto(report, List.of(), lookup());
    }

    /** A follow-up is asked on the report it belongs to; a question about a follow-up goes to its report. */
    @Transactional
    public ReportDto askReportQuestion(Long reportId, NewReportQuestionCommand command) {
        Report report = load(reportId);
        Report parent = report.getParentReport() != null ? report.getParentReport() : report;
        String question = prompt(command.getQuestion(), true);
        if (parent.getStatus().isActive()) {
            throw new ReportBusyException(parent.getId().id());
        }
        requireAi();
        Report followUp = reportRepository.saveAndFlush(Report.builder()
                .collection(parent.getCollection())
                .recordId(parent.getRecordId())
                .parentReport(parent)
                .prompt(question)
                .scope(parent.getScope().withoutEmails())
                .createdByUser(currentUser())
                .build());
        queue(followUp.getId().id());
        return ReportMapper.toDto(followUp, List.of(), lookup());
    }

    /** A new report with the same request and scope (a follow-up stays a follow-up of the same report). */
    @Transactional
    public ReportDto regenerateReport(Long reportId) {
        Report report = load(reportId);
        if (report.getStatus().isActive()) {
            throw new ReportBusyException(reportId);
        }
        requireAi();
        Report again = reportRepository.saveAndFlush(Report.builder()
                .collection(report.getCollection())
                .recordId(report.getRecordId())
                .parentReport(report.getParentReport())
                .prompt(report.getPrompt())
                .scope(report.getScope().withoutEmails())
                .createdByUser(currentUser())
                .build());
        queue(again.getId().id());
        return ReportMapper.toDto(again, List.of(), lookup());
    }

    @Transactional
    public ReportDto getReport(Long reportId) {
        Report report = load(reportId);
        ReportMapper.SourceLookup lookup = lookup();
        List<ReportDto> followUps = reportRepository.findByParentReport_IdOrderByCreatedAtAsc(report.getId()).stream()
                .map(followUp -> ReportMapper.toDto(followUp, List.of(), lookup))
                .toList();
        return ReportMapper.toDto(report, followUps, lookup);
    }

    /** After a restart: reports a stopped backend left in progress are generated again from the start. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resumeInterruptedReports() {
        for (Report report : reportRepository.findByStatusIn(ReportStatus.ACTIVE)) {
            report.setStatus(ReportStatus.QUEUED);
            reportRepository.saveAndFlush(report);
            queue(report.getId().id());
        }
    }

    @Transactional
    public void deleteReport(Long reportId) {
        Report report = load(reportId);
        reportRepository.delete(report);
        auditService.record(AuditEvent.currentActor(AuditAction.REPORT_DELETED).entity("report", reportId));
    }

    private Collection collection(Long collectionId) {
        return collectionRepository.findById(new Collection.CollectionId(collectionId))
                .filter(collection -> collection.getArchivedAt() == null)
                .orElseThrow(() -> new CollectionNotFoundException(collectionId));
    }

    private Report load(Long reportId) {
        return reportRepository.findById(new Report.ReportId(reportId)).orElseThrow(() -> new ReportNotFoundException(reportId));
    }

    private static String prompt(String value, boolean required) {
        String prompt = value == null ? "" : value.strip();
        if (required && prompt.isEmpty()) {
            throw new IllegalArgumentException("question: must not be empty");
        }
        if (prompt.length() > MAX_PROMPT) {
            throw new IllegalArgumentException("the request may have at most " + MAX_PROMPT + " characters");
        }
        return prompt.isEmpty() ? null : prompt;
    }

    private void requireAi() {
        if (!aiModelService.isReady()) {
            throw new AiUnavailableException("The AI model is not ready: start Ollama and download the models first");
        }
    }

    private AppUser currentUser() {
        return CurrentActor.userId().map(id -> appUserRepository.getReferenceById(new AppUser.AppUserId(id))).orElse(null);
    }

    /** The generator must see the committed report, so it is queued after commit (directly without a transaction). */
    private void queue(Long reportId) {
        Runnable submit = () -> aiExecutors.analysis().execute(() -> reportGenerator.generate(reportId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    /** Source states and current record titles, each record and relation read once per request. */
    private ReportMapper.SourceLookup lookup() {
        return new CitationSourceLookup(recordService, relationRepository, CurrentActor.userId().orElse(null));
    }
}

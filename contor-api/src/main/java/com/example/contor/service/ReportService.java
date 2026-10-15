package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.api.model.NewReportCommand;
import com.example.contor.api.model.NewReportQuestionCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.ReportDto;
import com.example.contor.api.model.ReportSummaryDto;
import com.example.contor.api.model.SourceState;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Relation;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportCitation;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.ReportStatus;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.ReportBusyException;
import com.example.contor.exception.ReportNotFoundException;
import com.example.contor.exception.ResourceNotFoundException;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Reports on a record (R6): create one over a checked scope, ask a follow-up question (a report of its own with the
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
                .scope(new ReportScope(parent.getScope().records(), parent.getScope().depth(), List.of()))
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
                .scope(new ReportScope(report.getScope().records(), report.getScope().depth(), List.of()))
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
        Long userId = CurrentActor.userId().orElse(null);
        Map<RecordRef, Optional<RecordDto>> records = new HashMap<>();
        Map<Long, Boolean> relations = new HashMap<>();
        return new ReportMapper.SourceLookup() {
            @Override
            public SourceState state(ReportCitation citation) {
                return switch (citation.getType()) {
                    case EMAIL_PARAGRAPH -> email(citation.getEmail());
                    case RELATION -> relations.computeIfAbsent(citation.getRelationId(),
                            id -> relationRepository.existsById(new Relation.RelationId(id))) ? SourceState.CURRENT : SourceState.DELETED;
                    case RECORD -> record(citation).isPresent() ? SourceState.CURRENT : SourceState.DELETED;
                    case FIELD -> record(citation)
                            .map(current -> same(citation.getValueSnapshot() != null ? citation.getValueSnapshot().get("value") : null,
                                    current.getValues().get(citation.getFieldSlug())) ? SourceState.CURRENT : SourceState.CHANGED)
                            .orElse(SourceState.DELETED);
                };
            }

            @Override
            public String recordTitle(Long collectionId, Long recordId) {
                return record(collectionId, recordId).map(RecordDto::getTitle).orElse(null);
            }

            private SourceState email(EmailMessage email) {
                if (email == null) {
                    return SourceState.DELETED;
                }
                Long owner = email.getAccount() != null && email.getAccount().getOwner() != null ? email.getAccount().getOwner().getId().id() : null;
                return Objects.equals(owner, userId) ? SourceState.CURRENT : SourceState.UNAVAILABLE;
            }

            private Optional<RecordDto> record(ReportCitation citation) {
                return citation.getCollection() == null || citation.getRecordId() == null ? Optional.empty()
                        : record(citation.getCollection().getId().id(), citation.getRecordId());
            }

            private Optional<RecordDto> record(Long collectionId, Long recordId) {
                return records.computeIfAbsent(new RecordRef(collectionId, recordId), ref -> {
                    try {
                        return Optional.of(recordService.getRecord(collectionId, recordId));
                    } catch (ResourceNotFoundException e) {
                        return Optional.empty();
                    }
                });
            }
        };
    }

    /** Equal values, whatever number type or map order JSON gave them ({@code 110} = {@code 110.0}). */
    static boolean same(Object a, Object b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    private static Object normalize(Object value) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return number.toString();
            }
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), normalize(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ReportService::normalize).collect(Collectors.toList());
        }
        return value;
    }
}

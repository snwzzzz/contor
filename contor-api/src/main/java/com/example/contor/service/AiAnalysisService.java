package com.example.contor.service;

import com.example.contor.api.model.AnalysisRunDto;
import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.AnalysisRunNotFoundException;
import com.example.contor.exception.AnalysisRunStateException;
import com.example.contor.mapper.AiMapper;
import com.example.contor.persistence.AiAnalysisRunRepository;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The current user's inbox analysis runs. Starting checks the preconditions (mail access granted, models installed,
 * no active run) and hands the run to the {@link AiAnalysisWorker} once the transaction has committed.
 */
@RequiredArgsConstructor
@Service
public class AiAnalysisService {

    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final AppUserRepository appUserRepository;
    private final AiSettingsService aiSettingsService;
    private final AiModelService aiModelService;
    private final AuditService auditService;
    private final AiAnalysisWorker aiAnalysisWorker;
    private final Clock clock;

    @Transactional
    public AnalysisRunDto startAnalysis() {
        AppUser.AppUserId userId = currentUser();
        AiSettings settings = aiSettingsService.settingsFor(userId);
        if (!settings.isMailAccess()) {
            throw new AiUnavailableException("Grant mail access in the AI setup before starting an analysis");
        }
        if (!aiModelService.isReady()) {
            throw new AiUnavailableException("The AI model is not ready: start Ollama and download the models first");
        }
        if (aiAnalysisRunRepository.existsByUser_IdAndStatusIn(userId, AnalysisRunStatus.ACTIVE)) {
            throw new AiUnavailableException("An analysis is already running");
        }

        Instant since = settings.getHistoryDays() > 0 ? clock.instant().minus(Duration.ofDays(settings.getHistoryDays())) : null;
        AiAnalysisRun run = aiAnalysisRunRepository.saveAndFlush(AiAnalysisRun.builder()
                .user(appUserRepository.getReferenceById(userId))
                .since(since)
                .build());
        auditService.record(AuditEvent.currentActor(AuditAction.AI_ANALYSIS_STARTED)
                .entity("ai_analysis_run", run.getId().id())
                .after(Map.of("historyDays", settings.getHistoryDays())));
        submitAfterCommit(run.getId().id());
        return AiMapper.toDto(run);
    }

    @Transactional
    public AnalysisRunDto getLatestAnalysis() {
        return aiAnalysisRunRepository.findFirstByUser_IdOrderByCreatedAtDesc(currentUser())
                .map(AiMapper::toDto)
                .orElseThrow(AnalysisRunNotFoundException::new);
    }

    /** Starts extraction after the discovered categories were reviewed. */
    @Transactional
    public AnalysisRunDto continueAnalysis(Long runId) {
        AiAnalysisRun run = load(runId);
        if (run.getStatus() != AnalysisRunStatus.AWAITING_REVIEW) {
            throw new AnalysisRunStateException(runId, run.getStatus());
        }
        run.setStatus(AnalysisRunStatus.EXTRACTING);
        AiAnalysisRun saved = aiAnalysisRunRepository.saveAndFlush(run);
        submitAfterCommit(runId);
        return AiMapper.toDto(saved);
    }

    /** The worker stops before the next email; a finished run is returned unchanged. */
    @Transactional
    public AnalysisRunDto cancelAnalysis(Long runId) {
        AiAnalysisRun run = load(runId);
        if (!run.getStatus().isActive()) {
            return AiMapper.toDto(run);
        }
        run.setStatus(AnalysisRunStatus.CANCELLED);
        run.setFinishedAt(clock.instant());
        AiAnalysisRun saved = aiAnalysisRunRepository.saveAndFlush(run);
        auditService.record(AuditEvent.currentActor(AuditAction.AI_ANALYSIS_CANCELLED).entity("ai_analysis_run", runId));
        return AiMapper.toDto(saved);
    }

    /** The worker must see the committed run, so it starts only after commit (directly when no transaction is active). */
    private void submitAfterCommit(Long runId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiAnalysisWorker.submit(runId);
                }
            });
        } else {
            aiAnalysisWorker.submit(runId);
        }
    }

    /** A run of the current user; other users' runs do not exist for them. */
    private AiAnalysisRun load(Long runId) {
        AppUser.AppUserId userId = currentUser();
        return aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(runId))
                .filter(run -> run.getUser().getId().equals(userId))
                .orElseThrow(() -> new AnalysisRunNotFoundException(runId));
    }

    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
    }
}

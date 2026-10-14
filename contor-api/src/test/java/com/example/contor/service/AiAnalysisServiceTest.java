package com.example.contor.service;

import com.example.contor.api.model.AnalysisRunDto;
import com.example.contor.api.model.AnalysisState;
import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.AnalysisRunNotFoundException;
import com.example.contor.exception.AnalysisRunStateException;
import com.example.contor.persistence.AiAnalysisRunRepository;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AiSettingsService aiSettingsService;

    @Mock
    private AiModelService aiModelService;

    @Mock
    private AuditService auditService;

    @Mock
    private AiAnalysisWorker aiAnalysisWorker;

    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        service = new AiAnalysisService(aiAnalysisRunRepository, appUserRepository, aiSettingsService, aiModelService,
                auditService, aiAnalysisWorker, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(appUserRepository.getReferenceById(OWNER)).thenReturn(AppUser.builder().id(OWNER).build());
        lenient().when(aiAnalysisRunRepository.saveAndFlush(any(AiAnalysisRun.class))).thenAnswer(inv -> {
            AiAnalysisRun run = inv.getArgument(0);
            if (run.getId() == null) run.setId(new AiAnalysisRun.AiAnalysisRunId(10L));
            return run;
        });
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private void settings(boolean mailAccess, int historyDays) {
        when(aiSettingsService.settingsFor(OWNER)).thenReturn(AiSettings.builder().mailAccess(mailAccess).historyDays(historyDays).build());
    }

    private static AiAnalysisRun run(long id, Long userId, AnalysisRunStatus status) {
        return AiAnalysisRun.builder().id(new AiAnalysisRun.AiAnalysisRunId(id))
                .user(AppUser.builder().id(new AppUser.AppUserId(userId)).build()).status(status).build();
    }

    @Test
    void startAnalysis_QueuesARunForTheHistoryWindow() {
        settings(true, 90);
        when(aiModelService.isReady()).thenReturn(true);

        AnalysisRunDto dto = service.startAnalysis();

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getStatus()).isEqualTo(AnalysisState.QUEUED);
        assertThat(dto.getSince()).isEqualTo(NOW.minus(java.time.Duration.ofDays(90)).atOffset(ZoneOffset.UTC));
        verify(aiAnalysisWorker).submit(10L);
        verify(auditService).record(argThat(event -> event.action() == AuditAction.AI_ANALYSIS_STARTED));
    }

    @Test
    void startAnalysis_ZeroDaysMeansEverything() {
        settings(true, 0);
        when(aiModelService.isReady()).thenReturn(true);

        assertThat(service.startAnalysis().getSince()).isNull();
    }

    @Test
    void startAnalysis_WithoutMailAccessRejected() {
        settings(false, 90);

        assertThatThrownBy(() -> service.startAnalysis()).isInstanceOf(AiUnavailableException.class).hasMessageContaining("mail access");
        verify(aiAnalysisRunRepository, never()).saveAndFlush(any());
        verify(aiAnalysisWorker, never()).submit(anyLong());
    }

    @Test
    void startAnalysis_ModelsNotReadyRejected() {
        settings(true, 90);
        when(aiModelService.isReady()).thenReturn(false);

        assertThatThrownBy(() -> service.startAnalysis()).isInstanceOf(AiUnavailableException.class).hasMessageContaining("model");
        verify(aiAnalysisRunRepository, never()).saveAndFlush(any());
    }

    @Test
    void startAnalysis_ActiveRunRejected() {
        settings(true, 90);
        when(aiModelService.isReady()).thenReturn(true);
        when(aiAnalysisRunRepository.existsByUser_IdAndStatusIn(OWNER, AnalysisRunStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.startAnalysis()).isInstanceOf(AiUnavailableException.class).hasMessageContaining("already running");
        verify(aiAnalysisRunRepository, never()).saveAndFlush(any());
    }

    @Test
    void getLatestAnalysis() {
        when(aiAnalysisRunRepository.findFirstByUser_IdOrderByCreatedAtDesc(OWNER))
                .thenReturn(Optional.of(run(10L, 1L, AnalysisRunStatus.EXTRACTING)));

        assertThat(service.getLatestAnalysis().getStatus()).isEqualTo(AnalysisState.EXTRACTING);
    }

    @Test
    void getLatestAnalysis_NotFound() {
        when(aiAnalysisRunRepository.findFirstByUser_IdOrderByCreatedAtDesc(OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestAnalysis()).isInstanceOf(AnalysisRunNotFoundException.class);
    }

    @Test
    void continueAnalysis_StartsExtraction() {
        when(aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(10L)))
                .thenReturn(Optional.of(run(10L, 1L, AnalysisRunStatus.AWAITING_REVIEW)));

        AnalysisRunDto dto = service.continueAnalysis(10L);

        assertThat(dto.getStatus()).isEqualTo(AnalysisState.EXTRACTING);
        verify(aiAnalysisWorker).submit(10L);
    }

    @Test
    void continueAnalysis_WrongStateRejected() {
        when(aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(10L)))
                .thenReturn(Optional.of(run(10L, 1L, AnalysisRunStatus.COMPLETED)));

        assertThatThrownBy(() -> service.continueAnalysis(10L)).isInstanceOf(AnalysisRunStateException.class);
        verify(aiAnalysisWorker, never()).submit(anyLong());
    }

    @Test
    void continueAnalysis_OtherUsersRunNotFound() {
        when(aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(10L)))
                .thenReturn(Optional.of(run(10L, 2L, AnalysisRunStatus.AWAITING_REVIEW)));

        assertThatThrownBy(() -> service.continueAnalysis(10L)).isInstanceOf(AnalysisRunNotFoundException.class);
    }

    @Test
    void cancelAnalysis_StopsAnActiveRun() {
        when(aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(10L)))
                .thenReturn(Optional.of(run(10L, 1L, AnalysisRunStatus.EXTRACTING)));

        AnalysisRunDto dto = service.cancelAnalysis(10L);

        assertThat(dto.getStatus()).isEqualTo(AnalysisState.CANCELLED);
        assertThat(dto.getFinishedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        verify(auditService).record(argThat(event -> event.action() == AuditAction.AI_ANALYSIS_CANCELLED));
    }

    @Test
    void cancelAnalysis_FinishedRunUnchanged() {
        when(aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(10L)))
                .thenReturn(Optional.of(run(10L, 1L, AnalysisRunStatus.COMPLETED)));

        assertThat(service.cancelAnalysis(10L).getStatus()).isEqualTo(AnalysisState.COMPLETED);
        verify(aiAnalysisRunRepository, never()).saveAndFlush(any());
    }
}

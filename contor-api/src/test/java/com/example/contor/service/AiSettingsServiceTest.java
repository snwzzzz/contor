package com.example.contor.service;

import com.example.contor.api.model.AiAccess;
import com.example.contor.api.model.AiSettingsDto;
import com.example.contor.api.model.UpdateAiSettingsCommand;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.UserRole;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSettingsServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private AiSettingsRepository aiSettingsRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuditService auditService;

    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        service = new AiSettingsService(aiSettingsRepository, appUserRepository, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static AiSettings saved() {
        return AiSettings.builder().id(new AiSettings.AiSettingsId(5L)).user(AppUser.builder().id(OWNER).build())
                .mailAccess(true).historyDays(30).categories(AiPermissionLevel.AUTO).records(AiPermissionLevel.AUTO)
                .calendar(AiPermissionLevel.SUGGEST).tasks(AiPermissionLevel.OFF).build();
    }

    @Test
    void getSettings_DefaultsWhenNothingSaved() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.empty());

        AiSettingsDto dto = service.getSettings();

        assertThat(dto.getMailAccess()).isFalse();
        assertThat(dto.getHistoryDays()).isEqualTo(90);
        assertThat(dto.getCategories()).isEqualTo(AiAccess.SUGGEST);
        assertThat(dto.getRecords()).isEqualTo(AiAccess.SUGGEST);
        assertThat(dto.getCalendar()).isEqualTo(AiAccess.SUGGEST);
        assertThat(dto.getTasks()).isEqualTo(AiAccess.SUGGEST);
        assertThat(dto.getReplies()).isEqualTo(AiAccess.OFF);
        assertThat(dto.getOnboardedAt()).isNull();
        verify(aiSettingsRepository, never()).saveAndFlush(any());
    }

    @Test
    void getSettings_Saved() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(saved()));

        AiSettingsDto dto = service.getSettings();

        assertThat(dto.getHistoryDays()).isEqualTo(30);
        assertThat(dto.getTasks()).isEqualTo(AiAccess.OFF);
    }

    @Test
    void updateSettings_CreatesRowForTheCurrentUser() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.empty());
        when(appUserRepository.getReferenceById(OWNER)).thenReturn(AppUser.builder().id(OWNER).build());
        when(aiSettingsRepository.saveAndFlush(any(AiSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsDto dto = service.updateSettings(new UpdateAiSettingsCommand().mailAccess(true).historyDays(0)
                .categories(AiAccess.AUTO).tasks(AiAccess.OFF));

        ArgumentCaptor<AiSettings> captor = ArgumentCaptor.forClass(AiSettings.class);
        verify(aiSettingsRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(OWNER);
        assertThat(dto.getMailAccess()).isTrue();
        assertThat(dto.getHistoryDays()).isZero();
        assertThat(dto.getCategories()).isEqualTo(AiAccess.AUTO);
        assertThat(dto.getRecords()).isEqualTo(AiAccess.SUGGEST);
        assertThat(dto.getTasks()).isEqualTo(AiAccess.OFF);
        assertThat(dto.getOnboardedAt()).isNull();
    }

    @Test
    void updateSettings_NullFieldsUnchanged() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(saved()));
        when(aiSettingsRepository.saveAndFlush(any(AiSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsDto dto = service.updateSettings(new UpdateAiSettingsCommand().calendar(AiAccess.AUTO));

        assertThat(dto.getCalendar()).isEqualTo(AiAccess.AUTO);
        assertThat(dto.getHistoryDays()).isEqualTo(30);
        assertThat(dto.getCategories()).isEqualTo(AiAccess.AUTO);
        assertThat(dto.getMailAccess()).isTrue();
    }

    @Test
    void updateSettings_CompleteOnboardingSetsTheTimestampOnce() {
        AiSettings settings = saved();
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(settings));
        when(aiSettingsRepository.saveAndFlush(any(AiSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.updateSettings(new UpdateAiSettingsCommand().completeOnboarding(true)).getOnboardedAt())
                .isEqualTo(NOW.atOffset(ZoneOffset.UTC));

        settings.setOnboardedAt(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(service.updateSettings(new UpdateAiSettingsCommand().completeOnboarding(true)).getOnboardedAt())
                .isEqualTo(Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC));
    }

    @Test
    void updateSettings_IsAudited() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(saved()));
        when(aiSettingsRepository.saveAndFlush(any(AiSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(new UpdateAiSettingsCommand().tasks(AiAccess.AUTO));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.AI_SETTINGS_CHANGED);
        assertThat(captor.getValue().before()).containsEntry("tasks", "OFF");
        assertThat(captor.getValue().after()).containsEntry("tasks", "AUTO");
    }

    @Test
    void updateSettings_RepliesRoundTrip() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(saved()));
        when(aiSettingsRepository.saveAndFlush(any(AiSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsDto dto = service.updateSettings(new UpdateAiSettingsCommand().replies(AiAccess.SUGGEST));

        assertThat(dto.getReplies()).isEqualTo(AiAccess.SUGGEST);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().before()).containsEntry("replies", "OFF");
        assertThat(captor.getValue().after()).containsEntry("replies", "SUGGEST");
    }

    @Test
    void updateSettings_RepliesAutoRejected() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(saved()));

        assertThatThrownBy(() -> service.updateSettings(new UpdateAiSettingsCommand().replies(AiAccess.AUTO)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(aiSettingsRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }
}

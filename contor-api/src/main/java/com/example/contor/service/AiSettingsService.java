package com.example.contor.service;

import com.example.contor.api.model.AiSettingsDto;
import com.example.contor.api.model.UpdateAiSettingsCommand;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.mapper.AiMapper;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The current user's AI permissions (setup wizard). Changes are audited; reads never create a row. */
@RequiredArgsConstructor
@Service
public class AiSettingsService {

    private final AiSettingsRepository aiSettingsRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public AiSettingsDto getSettings() {
        return AiMapper.toDto(settingsFor(currentUser()));
    }

    @Transactional
    public AiSettingsDto updateSettings(UpdateAiSettingsCommand command) {
        AppUser.AppUserId userId = currentUser();
        AiSettings settings = aiSettingsRepository.findByUser_Id(userId)
                .orElseGet(() -> AiSettings.defaults(appUserRepository.getReferenceById(userId)));
        Map<String, Object> before = snapshot(settings);

        if (command.getMailAccess() != null) settings.setMailAccess(command.getMailAccess());
        if (command.getHistoryDays() != null) settings.setHistoryDays(command.getHistoryDays());
        if (command.getCategories() != null) settings.setCategories(AiMapper.toLevel(command.getCategories()));
        if (command.getRecords() != null) settings.setRecords(AiMapper.toLevel(command.getRecords()));
        if (command.getCalendar() != null) settings.setCalendar(AiMapper.toLevel(command.getCalendar()));
        if (command.getTasks() != null) settings.setTasks(AiMapper.toLevel(command.getTasks()));
        if (command.getExternalApis() != null) settings.setExternalApis(AiMapper.toLevel(command.getExternalApis()));
        if (command.getReplies() != null) {
            AiPermissionLevel replies = AiMapper.toLevel(command.getReplies());
            if (replies == AiPermissionLevel.AUTO) {
                throw new IllegalArgumentException("Reply drafts can only be OFF or SUGGEST: a draft is never sent");
            }
            settings.setReplies(replies);
        }
        if (command.getCourtesyReplies() != null) settings.setCourtesyReplies(command.getCourtesyReplies());
        if (command.getReadHiddenMailboxes() != null) settings.setReadHiddenMailboxes(command.getReadHiddenMailboxes());
        if (command.getOwnerProfile() != null) settings.setOwnerProfile(command.getOwnerProfile().isBlank() ? null : command.getOwnerProfile().strip());
        if (Boolean.TRUE.equals(command.getCompleteOnboarding()) && settings.getOnboardedAt() == null) {
            settings.setOnboardedAt(clock.instant());
        }
        AiSettings saved = aiSettingsRepository.saveAndFlush(settings);

        auditService.record(AuditEvent.currentActor(AuditAction.AI_SETTINGS_CHANGED)
                .entity("ai_settings", saved.getId() != null ? saved.getId().id() : null)
                .before(before)
                .after(snapshot(saved)));
        return AiMapper.toDto(saved);
    }

    /** Saved settings of {@code userId}, or unsaved defaults. For the analysis pipeline (runs without a session). */
    @Transactional
    public AiSettings settingsFor(AppUser.AppUserId userId) {
        return aiSettingsRepository.findByUser_Id(userId)
                .orElseGet(() -> AiSettings.defaults(AppUser.builder().id(userId).build()));
    }

    /** Users whose new mail the AI processes in the background. */
    @Transactional
    public List<AppUser.AppUserId> onboardedUsers() {
        return aiSettingsRepository.findByMailAccessTrueAndOnboardedAtIsNotNull().stream()
                .map(settings -> settings.getUser().getId())
                .toList();
    }

    private static Map<String, Object> snapshot(AiSettings settings) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mailAccess", settings.isMailAccess());
        snapshot.put("historyDays", settings.getHistoryDays());
        snapshot.put("categories", settings.getCategories().name());
        snapshot.put("records", settings.getRecords().name());
        snapshot.put("calendar", settings.getCalendar().name());
        snapshot.put("tasks", settings.getTasks().name());
        snapshot.put("replies", settings.getReplies().name());
        snapshot.put("externalApis", settings.getExternalApis().name());
        snapshot.put("courtesyReplies", settings.isCourtesyReplies());
        snapshot.put("readHiddenMailboxes", settings.isReadHiddenMailboxes());
        snapshot.put("ownerProfile", settings.getOwnerProfile() != null);
        snapshot.put("onboarded", settings.getOnboardedAt() != null);
        return snapshot;
    }

    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
    }
}

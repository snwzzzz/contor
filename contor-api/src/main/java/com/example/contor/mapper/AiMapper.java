package com.example.contor.mapper;

import com.example.contor.api.model.AiAccess;
import com.example.contor.api.model.AiSettingsDto;
import com.example.contor.api.model.AnalysisRunDto;
import com.example.contor.api.model.AnalysisState;
import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.EmailMessage;

public class AiMapper {

    public static AiSettingsDto toDto(AiSettings settings) {
        if (settings == null) return null;

        return new AiSettingsDto()
                .mailAccess(settings.isMailAccess())
                .historyDays(settings.getHistoryDays())
                .categories(toAccess(settings.getCategories()))
                .records(toAccess(settings.getRecords()))
                .calendar(toAccess(settings.getCalendar()))
                .tasks(toAccess(settings.getTasks()))
                .replies(toAccess(settings.getReplies()))
                .courtesyReplies(settings.isCourtesyReplies())
                .onboardedAt(MapperSupport.toOffset(settings.getOnboardedAt()));
    }

    public static AnalysisRunDto toDto(AiAnalysisRun run) {
        if (run == null) return null;

        return new AnalysisRunDto()
                .id(run.getId() != null ? run.getId().id() : null)
                .status(run.getStatus() != null ? AnalysisState.valueOf(run.getStatus().name()) : null)
                .since(MapperSupport.toOffset(run.getSince()))
                .discoverySteps(run.getDiscoverySteps())
                .discoveryStepsDone(run.getDiscoveryStepsDone())
                .totalEmails(run.getTotalEmails())
                .processedEmails(run.getProcessedEmails())
                .failedEmails(run.getFailedEmails())
                .proposalsCreated(run.getProposalsCreated())
                .autoApplied(run.getAutoApplied())
                .categoriesDiscovered(run.getCategoriesDiscovered())
                .error(run.getError())
                .currentEmailSubject(run.getCurrentEmail() != null ? run.getCurrentEmail().getSubject() : null)
                .currentEmailFrom(run.getCurrentEmail() != null ? sender(run.getCurrentEmail()) : null)
                .startedAt(MapperSupport.toOffset(run.getStartedAt()))
                .finishedAt(MapperSupport.toOffset(run.getFinishedAt()))
                .createdAt(MapperSupport.toOffset(run.getCreatedAt()));
    }

    private static String sender(EmailMessage email) {
        return email.getFromName() != null && !email.getFromName().isBlank() ? email.getFromName() : email.getFromAddress();
    }

    public static AiPermissionLevel toLevel(AiAccess access) {
        return access != null ? AiPermissionLevel.valueOf(access.name()) : null;
    }

    private static AiAccess toAccess(AiPermissionLevel level) {
        return level != null ? AiAccess.valueOf(level.name()) : null;
    }
}

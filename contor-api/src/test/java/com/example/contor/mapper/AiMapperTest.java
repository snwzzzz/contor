package com.example.contor.mapper;

import com.example.contor.api.model.AiAccess;
import com.example.contor.api.model.AiSettingsDto;
import com.example.contor.api.model.AnalysisRunDto;
import com.example.contor.api.model.AnalysisState;
import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.EmailMessage;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiMapperTest {

    @Test
    void maps_settings_to_dto() {
        AiSettings settings = AiSettings.builder().id(new AiSettings.AiSettingsId(3L)).mailAccess(true).historyDays(365)
                .categories(AiPermissionLevel.AUTO).records(AiPermissionLevel.SUGGEST).calendar(AiPermissionLevel.OFF)
                .tasks(AiPermissionLevel.AUTO).replies(AiPermissionLevel.SUGGEST).externalApis(AiPermissionLevel.AUTO).courtesyReplies(true).readHiddenMailboxes(false).onboardedAt(Instant.parse("2026-01-01T00:00:00Z")).build();

        AiSettingsDto dto = AiMapper.toDto(settings);

        assertThat(dto.getMailAccess()).isTrue();
        assertThat(dto.getHistoryDays()).isEqualTo(365);
        assertThat(dto.getCategories()).isEqualTo(AiAccess.AUTO);
        assertThat(dto.getRecords()).isEqualTo(AiAccess.SUGGEST);
        assertThat(dto.getCalendar()).isEqualTo(AiAccess.OFF);
        assertThat(dto.getTasks()).isEqualTo(AiAccess.AUTO);
        assertThat(dto.getReplies()).isEqualTo(AiAccess.SUGGEST);
        assertThat(dto.getExternalApis()).isEqualTo(AiAccess.AUTO);
        assertThat(dto.getCourtesyReplies()).isTrue();
        assertThat(dto.getReadHiddenMailboxes()).isFalse();
        assertThat(dto.getOnboardedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC));
    }

    @Test
    void maps_run_to_dto() {
        AiAnalysisRun run = AiAnalysisRun.builder().id(new AiAnalysisRun.AiAnalysisRunId(7L)).status(AnalysisRunStatus.EXTRACTING)
                .since(Instant.parse("2025-10-01T00:00:00Z")).discoverySteps(11).discoveryStepsDone(4).totalEmails(120).processedEmails(40).failedEmails(2)
                .proposalsCreated(11).autoApplied(5).categoriesDiscovered(3).error("boom")
                .startedAt(Instant.parse("2026-01-01T00:00:00Z")).finishedAt(Instant.parse("2026-01-01T01:00:00Z"))
                .currentEmail(EmailMessage.builder().subject("Interview invitation").fromName("Anna Berger").fromAddress("anna@acme.com").build())
                .build();
        run.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        AnalysisRunDto dto = AiMapper.toDto(run);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getStatus()).isEqualTo(AnalysisState.EXTRACTING);
        assertThat(dto.getSince()).isEqualTo(Instant.parse("2025-10-01T00:00:00Z").atOffset(ZoneOffset.UTC));
        assertThat(dto.getDiscoverySteps()).isEqualTo(11);
        assertThat(dto.getDiscoveryStepsDone()).isEqualTo(4);
        assertThat(dto.getTotalEmails()).isEqualTo(120);
        assertThat(dto.getProcessedEmails()).isEqualTo(40);
        assertThat(dto.getFailedEmails()).isEqualTo(2);
        assertThat(dto.getProposalsCreated()).isEqualTo(11);
        assertThat(dto.getAutoApplied()).isEqualTo(5);
        assertThat(dto.getCategoriesDiscovered()).isEqualTo(3);
        assertThat(dto.getError()).isEqualTo("boom");
        assertThat(dto.getCurrentEmailSubject()).isEqualTo("Interview invitation");
        assertThat(dto.getCurrentEmailFrom()).isEqualTo("Anna Berger");
        assertThat(dto.getStartedAt()).isNotNull();
        assertThat(dto.getFinishedAt()).isNotNull();
        assertThat(dto.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC));
    }

    @Test
    void handles_null_input_and_missing_values() {
        assertThat(AiMapper.toDto((AiSettings) null)).isNull();
        assertThat(AiMapper.toDto((AiAnalysisRun) null)).isNull();

        AnalysisRunDto dto = AiMapper.toDto(AiAnalysisRun.builder().build());
        assertThat(dto.getId()).isNull();
        assertThat(dto.getSince()).isNull();
        assertThat(dto.getStatus()).isEqualTo(AnalysisState.QUEUED);
        assertThat(dto.getCurrentEmailSubject()).isNull();
        assertThat(AiMapper.toDto(AiAnalysisRun.builder().currentEmail(EmailMessage.builder().fromAddress("noreply@shop.com").build()).build())
                .getCurrentEmailFrom()).isEqualTo("noreply@shop.com");
    }
}

package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.AiAccess;
import com.example.contor.api.model.AiModelDto;
import com.example.contor.api.model.AiModelRole;
import com.example.contor.api.model.AiPullState;
import com.example.contor.api.model.AiSettingsDto;
import com.example.contor.api.model.AiStatusDto;
import com.example.contor.api.model.AnalysisRunDto;
import com.example.contor.api.model.AnalysisState;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.AnalysisRunNotFoundException;
import com.example.contor.api.model.EvalCaseExportDto;
import com.example.contor.service.AiAnalysisService;
import com.example.contor.service.AiEvalExportService;
import com.example.contor.service.AiModelService;
import com.example.contor.service.AiSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(AiController.class)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiModelService aiModelService;

    @MockitoBean
    private AiSettingsService aiSettingsService;

    @MockitoBean
    private AiAnalysisService aiAnalysisService;

    @MockitoBean
    private AiEvalExportService aiEvalExportService;

    private static AiStatusDto aiStatus() {
        return new AiStatusDto(true, false, List.of(new AiModelDto("qwen3.5:9b", List.of(AiModelRole.EXTRACTION), false, AiPullState.PULLING)
                .pullPercent(40))).version("0.40.1");
    }

    private static AnalysisRunDto run(AnalysisState state) {
        return new AnalysisRunDto(10L, state, 3, 3, 100, 25, 1, 7, 3, 2, OffsetDateTime.parse("2026-10-08T12:00:00Z"));
    }

    private static AiSettingsDto settings() {
        return new AiSettingsDto(true, 90, AiAccess.SUGGEST, AiAccess.AUTO, AiAccess.AUTO, AiAccess.OFF);
    }

    @Test
    void status_is_readable_by_any_user() throws Exception {
        when(aiModelService.status()).thenReturn(aiStatus());

        mockMvc.perform(get("/api/ai/status").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.models[0].name").value("qwen3.5:9b"))
                .andExpect(jsonPath("$.models[0].pullPercent").value(40));
    }

    @Test
    void status_requires_a_session() throws Exception {
        mockMvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void pull_is_owner_only_and_accepted() throws Exception {
        when(aiModelService.pullMissingModels()).thenReturn(aiStatus());

        mockMvc.perform(post("/api/ai/models/pull").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.models[0].pullState").value("PULLING"));
        mockMvc.perform(post("/api/ai/models/pull").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pull_without_ollama_is_a_conflict() throws Exception {
        when(aiModelService.pullMissingModels()).thenThrow(new AiUnavailableException("Ollama is not reachable"));

        mockMvc.perform(post("/api/ai/models/pull").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ollama is not reachable"));
    }

    @Test
    void reads_settings() throws Exception {
        when(aiSettingsService.getSettings()).thenReturn(settings());

        mockMvc.perform(get("/api/ai/settings").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").value("OFF"))
                .andExpect(jsonPath("$.historyDays").value(90));
    }

    @Test
    void updates_settings_as_owner_with_csrf() throws Exception {
        when(aiSettingsService.updateSettings(any())).thenReturn(settings());

        mockMvc.perform(put("/api/ai/settings").with(TestAuth.owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mailAccess\": true, \"tasks\": \"OFF\", \"completeOnboarding\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mailAccess").value(true));
        verify(aiSettingsService).updateSettings(argThat(c -> c.getMailAccess() && c.getTasks() == AiAccess.OFF && c.getCompleteOnboarding()));
    }

    @Test
    void updating_settings_needs_owner_and_csrf_and_valid_input() throws Exception {
        mockMvc.perform(put("/api/ai/settings").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ai/settings").with(TestAuth.owner()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ai/settings").with(TestAuth.owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyDays\": -1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/ai/settings").with(TestAuth.owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tasks\": \"SOMETIMES\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void starts_an_analysis() throws Exception {
        when(aiAnalysisService.startAnalysis()).thenReturn(run(AnalysisState.QUEUED));

        mockMvc.perform(post("/api/ai/analysis").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.proposalsCreated").value(7));
        mockMvc.perform(post("/api/ai/analysis").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void latest_analysis_or_404() throws Exception {
        when(aiAnalysisService.getLatestAnalysis()).thenReturn(run(AnalysisState.EXTRACTING));
        mockMvc.perform(get("/api/ai/analysis/latest").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedEmails").value(25));

        when(aiAnalysisService.getLatestAnalysis()).thenThrow(new AnalysisRunNotFoundException());
        mockMvc.perform(get("/api/ai/analysis/latest").with(TestAuth.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void continues_and_cancels_a_run() throws Exception {
        when(aiAnalysisService.continueAnalysis(10L)).thenReturn(run(AnalysisState.EXTRACTING));
        when(aiAnalysisService.cancelAnalysis(10L)).thenReturn(run(AnalysisState.CANCELLED));

        mockMvc.perform(post("/api/ai/analysis/10/continue").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXTRACTING"));
        mockMvc.perform(post("/api/ai/analysis/10/cancel").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/ai/analysis/10/cancel").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void evalExport_returns_the_cases_to_the_owner() throws Exception {
        when(aiEvalExportService.exportCases(List.of(5L, 6L))).thenReturn(new EvalCaseExportDto(List.of()));

        mockMvc.perform(get("/api/ai/eval/export").param("emailIds", "5", "6").with(TestAuth.owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isArray());
    }

    @Test
    void evalExport_is_forbidden_for_regular_users() throws Exception {
        mockMvc.perform(get("/api/ai/eval/export").param("emailIds", "5").with(TestAuth.user()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(aiEvalExportService);
    }

    @Test
    void evalExport_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/ai/eval/export").param("emailIds", "5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void evalExport_rejects_a_missing_id_list() throws Exception {
        mockMvc.perform(get("/api/ai/eval/export").with(TestAuth.owner()))
                .andExpect(status().isBadRequest());
    }
}

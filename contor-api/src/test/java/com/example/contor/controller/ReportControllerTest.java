package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.NewReportCommand;
import com.example.contor.api.model.NewReportQuestionCommand;
import com.example.contor.api.model.RecordRefDto;
import com.example.contor.api.model.ReportDto;
import com.example.contor.api.model.ReportState;
import com.example.contor.api.model.ReportSummaryDto;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.ReportBusyException;
import com.example.contor.exception.ReportNotFoundException;
import com.example.contor.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(ReportController.class)
public class ReportControllerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 14, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void list_record_reports_returns_200() throws Exception {
        when(reportService.listRecordReports(1L, 4711L)).thenReturn(List.of(
                new ReportSummaryDto(5L, ReportState.READY, 4, 3, 1, NOW).title("Order 4711").summary("It was late.")));

        mockMvc.perform(get("/api/collections/1/records/4711/reports").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].state").value("READY"))
                .andExpect(jsonPath("$[0].summary").value("It was late."))
                .andExpect(jsonPath("$[0].followUpCount").value(1));
    }

    @Test
    void create_report_returns_202() throws Exception {
        when(reportService.createReport(eq(1L), eq(4711L), any(NewReportCommand.class))).thenReturn(report(ReportState.QUEUED));

        mockMvc.perform(post("/api/collections/1/records/4711/reports").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"What went wrong?\", \"connectedRecords\": [{\"collectionId\": 2, \"recordId\": 88}], \"depth\": 1}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.state").value("QUEUED"));
    }

    @Test
    void create_report_with_an_unconnected_record_returns_400() throws Exception {
        when(reportService.createReport(eq(1L), eq(4711L), any())).thenThrow(new IllegalArgumentException("record 9 is not connected"));

        mockMvc.perform(post("/api/collections/1/records/4711/reports").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("record 9 is not connected"));
    }

    @Test
    void create_report_without_a_ready_model_returns_409() throws Exception {
        when(reportService.createReport(eq(1L), eq(4711L), any())).thenThrow(new AiUnavailableException("The AI model is not ready"));

        mockMvc.perform(post("/api/collections/1/records/4711/reports").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_report_without_csrf_returns_403() throws Exception {
        mockMvc.perform(post("/api/collections/1/records/4711/reports").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(reportService, never()).createReport(any(), any(), any());
    }

    @Test
    void create_report_with_too_deep_a_scope_returns_400() throws Exception {
        mockMvc.perform(post("/api/collections/1/records/4711/reports").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"depth\": 3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_report_returns_200() throws Exception {
        when(reportService.getReport(5L)).thenReturn(report(ReportState.READY).title("Order 4711"));

        mockMvc.perform(get("/api/reports/5").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Order 4711"))
                .andExpect(jsonPath("$.records[0].recordId").value(4711));
    }

    @Test
    void get_report_returns_404() throws Exception {
        when(reportService.getReport(5L)).thenThrow(new ReportNotFoundException(5L));

        mockMvc.perform(get("/api/reports/5").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void get_report_requires_login() throws Exception {
        mockMvc.perform(get("/api/reports/5")).andExpect(status().isUnauthorized());
    }

    @Test
    void ask_report_question_returns_202() throws Exception {
        when(reportService.askReportQuestion(eq(5L), any(NewReportQuestionCommand.class))).thenReturn(report(ReportState.QUEUED).parentReportId(5L));

        mockMvc.perform(post("/api/reports/5/questions").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"Who caused the delay?\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.parentReportId").value(5));
    }

    @Test
    void ask_report_question_while_generating_returns_409() throws Exception {
        when(reportService.askReportQuestion(eq(5L), any())).thenThrow(new ReportBusyException(5L));

        mockMvc.perform(post("/api/reports/5/questions").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"Why?\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ask_report_question_without_a_question_returns_400() throws Exception {
        mockMvc.perform(post("/api/reports/5/questions").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void regenerate_report_returns_202() throws Exception {
        when(reportService.regenerateReport(5L)).thenReturn(report(ReportState.QUEUED));

        mockMvc.perform(post("/api/reports/5/regenerate").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isAccepted());
    }

    @Test
    void delete_report_returns_204() throws Exception {
        mockMvc.perform(delete("/api/reports/5").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(reportService).deleteReport(5L);
    }

    private static ReportDto report(ReportState state) {
        return new ReportDto(5L, 1L, 4711L, state, List.of(new RecordRefDto(1L, 4711L)), 0, List.of(), List.of(),
                List.of(), List.of(), 0, 0, 0, NOW);
    }
}

package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.api.model.UpdateRecordCommand;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.service.RecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(RecordController.class)
public class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecordService recordService;

    private static RecordDto record() {
        return new RecordDto().id(5L).collectionId(1L).title("Parcel").values(Map.of("carrier", "DHL")).extra(Map.of())
                .createdAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).createdBy("USER");
    }

    @Test
    void list_records_passes_filters_sort_and_paging() throws Exception {
        when(recordService.listRecords(1L, List.of("carrier:eq:DHL", "eta:lt:2026-02-01"), "-eta", 2, 10))
                .thenReturn(new RecordPageDto(List.of(record()), 2, 10, 21L, 3));

        mockMvc.perform(get("/api/collections/1/records").with(TestAuth.user())
                        .param("filter", "carrier:eq:DHL", "eta:lt:2026-02-01")
                        .param("sort", "-eta").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].values.carrier").value("DHL"))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void list_records_uses_default_paging() throws Exception {
        when(recordService.listRecords(1L, null, null, 0, 25)).thenReturn(new RecordPageDto(List.of(), 0, 25, 0L, 0));

        mockMvc.perform(get("/api/collections/1/records").with(TestAuth.user()))
                .andExpect(status().isOk());
    }

    @Test
    void list_records_rejects_oversized_pages() throws Exception {
        mockMvc.perform(get("/api/collections/1/records").with(TestAuth.user()).param("size", "500"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recordService);
    }

    @Test
    void create_record_returns_201() throws Exception {
        when(recordService.createRecord(eq(1L), any())).thenReturn(record());

        mockMvc.perform(post("/api/collections/1/records").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NewRecordCommand().values(Map.of("carrier", "DHL")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"));
    }

    @Test
    void create_record_returns_400_with_all_violations() throws Exception {
        when(recordService.createRecord(eq(1L), any()))
                .thenThrow(new RecordValidationException(List.of("carrier: is required", "eta: must be a date (YYYY-MM-DD)")));

        mockMvc.perform(post("/api/collections/1/records").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"values\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("carrier: is required; eta: must be a date (YYYY-MM-DD)"));
    }

    @Test
    void update_record_returns_200() throws Exception {
        when(recordService.updateRecord(eq(1L), eq(5L), any())).thenReturn(record());

        mockMvc.perform(put("/api/collections/1/records/5").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRecordCommand().values(Map.of("carrier", "UPS")))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_record_returns_204() throws Exception {
        mockMvc.perform(delete("/api/collections/1/records/5").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(recordService).deleteRecord(1L, 5L);
    }

    @Test
    void get_record_returns_404() throws Exception {
        when(recordService.getRecord(1L, 9L)).thenThrow(new RecordNotFoundException(1L, 9L));

        mockMvc.perform(get("/api/collections/1/records/9").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Record 9 not found in collection 1"));
    }
}

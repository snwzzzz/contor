package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.NewSavedQueryCommand;
import com.example.contor.api.model.SavedQueryDto;
import com.example.contor.api.model.UpdateSavedQueryCommand;
import com.example.contor.exception.SavedQueryNotFoundException;
import com.example.contor.service.SavedQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(SavedQueryController.class)
public class SavedQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SavedQueryService savedQueryService;

    private static SavedQueryDto dto() {
        return new SavedQueryDto(1L, "Open", Map.of("from", "deliveries"), true);
    }

    @Test
    void list_saved_queries_returns_200() throws Exception {
        when(savedQueryService.findAll()).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/saved-queries").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dsl.from").value("deliveries"));
    }

    @Test
    void create_saved_query_returns_201() throws Exception {
        when(savedQueryService.createSavedQuery(any())).thenReturn(dto());

        mockMvc.perform(post("/api/saved-queries").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NewSavedQueryCommand("Open", Map.of("from", "deliveries")))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_saved_query_requires_a_dsl() throws Exception {
        mockMvc.perform(post("/api/saved-queries").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Open\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(savedQueryService);
    }

    @Test
    void update_saved_query_returns_200() throws Exception {
        when(savedQueryService.updateSavedQuery(eq(1L), any())).thenReturn(dto());

        mockMvc.perform(put("/api/saved-queries/1").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSavedQueryCommand().pinned(false))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_saved_query_returns_204_and_404() throws Exception {
        mockMvc.perform(delete("/api/saved-queries/1").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());
        doThrow(new SavedQueryNotFoundException(2L)).when(savedQueryService).deleteSavedQuery(2L);
        mockMvc.perform(delete("/api/saved-queries/2").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNotFound());
    }
}

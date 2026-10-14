package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.Creator;
import com.example.contor.api.model.LabelDto;
import com.example.contor.api.model.NewLabelCommand;
import com.example.contor.api.model.UpdateLabelCommand;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.LabelNotFoundException;
import com.example.contor.service.LabelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(LabelController.class)
public class LabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LabelService labelService;

    private static LabelDto dto() {
        return new LabelDto(1L, "invoices", "Invoices", "#ff0000", Creator.USER);
    }

    @Test
    void list_labels_returns_200() throws Exception {
        when(labelService.findAll()).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/labels").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("invoices"));
    }

    @Test
    void create_label_returns_201() throws Exception {
        when(labelService.createLabel(any())).thenReturn(dto());

        mockMvc.perform(post("/api/labels").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NewLabelCommand("invoices", "Invoices", "#ff0000"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("#ff0000"));
    }

    @Test
    void create_label_returns_409_when_slug_taken() throws Exception {
        when(labelService.createLabel(any())).thenThrow(new DuplicateResourceException("A label with slug 'invoices' already exists"));

        mockMvc.perform(post("/api/labels").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NewLabelCommand("invoices", "Invoices", "#ff0000"))))
                .andExpect(status().isConflict());
    }

    @Test
    void update_label_returns_200() throws Exception {
        when(labelService.updateLabel(eq(1L), any())).thenReturn(dto());

        mockMvc.perform(put("/api/labels/1").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateLabelCommand().name("Bills"))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_label_returns_204() throws Exception {
        mockMvc.perform(delete("/api/labels/1").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(labelService).deleteLabel(1L);
    }

    @Test
    void get_label_returns_404() throws Exception {
        when(labelService.findById(9L)).thenThrow(new LabelNotFoundException(9L));

        mockMvc.perform(get("/api/labels/9").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Label not found with id: 9"));
    }
}

package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.NewRuleCommand;
import com.example.contor.api.model.RuleDto;
import com.example.contor.api.model.UpdateRuleCommand;
import com.example.contor.exception.RuleNotFoundException;
import com.example.contor.service.RuleService;
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

@ControllerTest(RuleController.class)
public class RuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RuleService ruleService;

    private static RuleDto dto() {
        return new RuleDto(1L, "Shop mails", true, 100, Map.of("from", "shop@example.com"), List.of());
    }

    @Test
    void list_rules_returns_200() throws Exception {
        when(ruleService.findAll()).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/rules").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conditions.from").value("shop@example.com"));
    }

    @Test
    void create_rule_returns_201() throws Exception {
        when(ruleService.createRule(any())).thenReturn(dto());

        mockMvc.perform(post("/api/rules").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NewRuleCommand("Shop mails"))))
                .andExpect(status().isCreated());
    }

    @Test
    void update_rule_returns_200() throws Exception {
        when(ruleService.updateRule(eq(1L), any())).thenReturn(dto());

        mockMvc.perform(put("/api/rules/1").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRuleCommand().enabled(false))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_rule_returns_204() throws Exception {
        mockMvc.perform(delete("/api/rules/1").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(ruleService).deleteRule(1L);
    }

    @Test
    void get_rule_returns_404() throws Exception {
        when(ruleService.findById(9L)).thenThrow(new RuleNotFoundException(9L));

        mockMvc.perform(get("/api/rules/9").with(TestAuth.user()))
                .andExpect(status().isNotFound());
    }
}

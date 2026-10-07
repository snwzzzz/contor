package com.example.contor.controller;

import com.example.contor.service.SystemService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SystemController.class, properties = "app.version=1.2.3")
@Import(SystemService.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void get_version_returns_200() throws Exception {
        mockMvc.perform(get("/api/system/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("contor"))
                .andExpect(jsonPath("$.version").value("1.2.3"));
    }
}

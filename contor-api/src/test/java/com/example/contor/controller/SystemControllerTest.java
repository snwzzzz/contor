package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.service.SystemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(SystemController.class)
@Import(SystemService.class)
@TestPropertySource(properties = "app.version=1.2.3")
public class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void get_version_returns_200() throws Exception {
        mockMvc.perform(get("/api/system/version").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("contor"))
                .andExpect(jsonPath("$.version").value("1.2.3"));
    }

    @Test
    void get_version_returns_401_with_error_body_when_anonymous() throws Exception {
        mockMvc.perform(get("/api/system/version"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}

package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.ContactPhotoDto;
import com.example.contor.exception.SenderLogoNotFoundException;
import com.example.contor.service.ImageData;
import com.example.contor.service.SenderPhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(SenderController.class)
public class SenderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SenderPhotoService senderPhotoService;

    @Test
    void list_sender_logos_returns_the_domains_that_have_one() throws Exception {
        when(senderPhotoService.listLogos(List.of("siemens.com", "example.com"))).thenReturn(List.of("siemens.com"));

        mockMvc.perform(get("/api/senders/logos").param("domain", "siemens.com", "example.com").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("siemens.com"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void get_sender_logo_returns_the_image() throws Exception {
        when(senderPhotoService.getLogo("siemens.com")).thenReturn(new ImageData(new byte[] {1, 2}, "image/png"));

        mockMvc.perform(get("/api/senders/logos/siemens.com").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] {1, 2}))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void get_sender_logo_without_logo_returns_404() throws Exception {
        when(senderPhotoService.getLogo("example.com")).thenThrow(new SenderLogoNotFoundException("example.com"));

        mockMvc.perform(get("/api/senders/logos/example.com").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void list_contact_photos_returns_200() throws Exception {
        when(senderPhotoService.listContactPhotos()).thenReturn(List.of(new ContactPhotoDto(9L, "bob@example.com")));

        mockMvc.perform(get("/api/senders/contact-photos").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].emailAddress").value("bob@example.com"));
    }

    @Test
    void get_contact_photo_returns_the_image() throws Exception {
        when(senderPhotoService.getContactPhoto(9L)).thenReturn(new ImageData(new byte[] {3}, "image/jpeg"));

        mockMvc.perform(get("/api/senders/contact-photos/9").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[] {3}));
    }

    @Test
    void sender_photos_require_login() throws Exception {
        mockMvc.perform(get("/api/senders/logos").param("domain", "siemens.com"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/senders/contact-photos/9"))
                .andExpect(status().isUnauthorized());
    }
}

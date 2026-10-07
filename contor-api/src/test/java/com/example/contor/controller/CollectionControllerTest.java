package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.Creator;
import com.example.contor.api.model.DisplayConfigDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.api.model.UpdateCollectionCommand;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.service.CollectionSchemaService;
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

@ControllerTest(CollectionController.class)
public class CollectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CollectionSchemaService collectionSchemaService;

    private static CollectionDto dto() {
        return new CollectionDto().id(3L).slug("deliveries").name("Deliveries").system(false).matchKeys(List.of())
                .displayConfig(new DisplayConfigDto()).schemaVersion(1).createdBy(Creator.USER).fields(List.of());
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void list_collections_returns_200() throws Exception {
        when(collectionSchemaService.findAll()).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/collections").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("deliveries"));
    }

    @Test
    void create_collection_returns_201_for_the_owner() throws Exception {
        when(collectionSchemaService.createCollection(any())).thenReturn(dto());

        mockMvc.perform(post("/api/collections").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewCollectionCommand("deliveries", "Deliveries"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void schema_changes_are_forbidden_for_non_owners() throws Exception {
        mockMvc.perform(post("/api/collections").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewCollectionCommand("deliveries", "Deliveries"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
        mockMvc.perform(post("/api/collections/3/fields").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewCollectionFieldCommand("eta", "ETA", CollectionFieldType.DATE))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/collections/3/fields/4/archive").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/collections/3").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(new UpdateCollectionCommand().name("x"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(collectionSchemaService);
    }

    @Test
    void create_collection_returns_400_for_invalid_definitions() throws Exception {
        when(collectionSchemaService.createCollection(any())).thenThrow(new IllegalArgumentException("slug is not valid"));

        mockMvc.perform(post("/api/collections").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewCollectionCommand("Bad", "Bad"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("slug is not valid"));
    }

    @Test
    void create_collection_returns_409_for_taken_slug() throws Exception {
        when(collectionSchemaService.createCollection(any())).thenThrow(new DuplicateResourceException("A collection with slug 'deliveries' already exists"));

        mockMvc.perform(post("/api/collections").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewCollectionCommand("deliveries", "Deliveries"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void add_field_returns_201() throws Exception {
        when(collectionSchemaService.addField(eq(3L), any())).thenReturn(dto().schemaVersion(2));

        mockMvc.perform(post("/api/collections/3/fields").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewCollectionFieldCommand("eta", "ETA", CollectionFieldType.DATE))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value(2));
    }

    @Test
    void add_field_returns_400_for_unknown_type() throws Exception {
        mockMvc.perform(post("/api/collections/3/fields").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"eta\",\"name\":\"ETA\",\"type\":\"COLOR\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(collectionSchemaService);
    }

    @Test
    void archive_field_returns_200() throws Exception {
        when(collectionSchemaService.archiveField(3L, 4L)).thenReturn(dto());

        mockMvc.perform(post("/api/collections/3/fields/4/archive").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void update_collection_returns_200() throws Exception {
        when(collectionSchemaService.updateCollection(eq(3L), any())).thenReturn(dto().name("Shipments"));

        mockMvc.perform(put("/api/collections/3").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(new UpdateCollectionCommand().name("Shipments"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shipments"));
    }

    @Test
    void get_collection_returns_404_when_not_found() throws Exception {
        when(collectionSchemaService.findById(99L)).thenThrow(new CollectionNotFoundException(99L));

        mockMvc.perform(get("/api/collections/99").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Collection not found with id: 99"));
    }
}

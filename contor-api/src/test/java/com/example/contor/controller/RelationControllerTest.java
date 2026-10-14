package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.Creator;
import com.example.contor.api.model.NewRelationCommand;
import com.example.contor.api.model.NewRelationTypeCommand;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationOrigin;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.api.model.UpdateRelationCommand;
import com.example.contor.api.model.UpdateRelationTypeCommand;
import com.example.contor.exception.ResourceInUseException;
import com.example.contor.service.RelationService;
import com.example.contor.service.RelationTypeService;
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

@ControllerTest(RelationController.class)
public class RelationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RelationTypeService relationTypeService;

    @MockitoBean
    private RelationService relationService;

    private static RelationTypeDto type() {
        return new RelationTypeDto(3L, "works_at", "works at", true, Map.of(), Creator.USER);
    }

    private static RelationDto relation() {
        return new RelationDto(7L, 3L, 1L, 10L, 2L, 20L, Map.of(), RelationOrigin.USER);
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void list_relation_types_returns_200() throws Exception {
        when(relationTypeService.findAll()).thenReturn(List.of(type()));

        mockMvc.perform(get("/api/relation-types").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("works_at"));
    }

    @Test
    void relation_type_changes_are_owner_only() throws Exception {
        mockMvc.perform(post("/api/relation-types").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewRelationTypeCommand("knows", "knows"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/relation-types/3").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateRelationTypeCommand().name("x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/relation-types/3").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(relationTypeService);
    }

    @Test
    void create_relation_type_returns_201_for_the_owner() throws Exception {
        when(relationTypeService.createRelationType(any())).thenReturn(type());

        mockMvc.perform(post("/api/relation-types").with(TestAuth.owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewRelationTypeCommand("works_at", "works at"))))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_relation_type_in_use_returns_409() throws Exception {
        doThrow(new ResourceInUseException("Relation type 3 is used by 2 relations")).when(relationTypeService).deleteRelationType(3L);

        mockMvc.perform(delete("/api/relation-types/3").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Relation type 3 is used by 2 relations"));
    }

    @Test
    void list_relations_of_a_record_returns_200() throws Exception {
        when(relationService.listRelations(1L, 10L)).thenReturn(List.of(relation()));

        mockMvc.perform(get("/api/relations").param("collectionId", "1").param("recordId", "10").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toRecordId").value(20));
    }

    @Test
    void list_relations_requires_the_record_pair() throws Exception {
        mockMvc.perform(get("/api/relations").param("collectionId", "1").with(TestAuth.user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing request parameter 'recordId'"));
        verifyNoInteractions(relationService);
    }

    @Test
    void create_relation_returns_201_and_400() throws Exception {
        when(relationService.createRelation(any())).thenReturn(relation());
        mockMvc.perform(post("/api/relations").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewRelationCommand(3L, 1L, 10L, 2L, 20L))))
                .andExpect(status().isCreated());

        when(relationService.createRelation(any())).thenThrow(new IllegalArgumentException("A record cannot be related to itself"));
        mockMvc.perform(post("/api/relations").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NewRelationCommand(3L, 1L, 10L, 1L, 10L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_and_delete_relation() throws Exception {
        when(relationService.updateRelation(eq(7L), any())).thenReturn(relation());

        mockMvc.perform(put("/api/relations/7").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateRelationCommand().attributes(Map.of("since", "2024")))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/relations/7").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(relationService).deleteRelation(7L);
    }
}

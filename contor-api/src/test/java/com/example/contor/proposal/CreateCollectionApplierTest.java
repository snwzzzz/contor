package com.example.contor.proposal;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.service.CollectionSchemaService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CreateCollectionApplierTest {

    @Mock
    private CollectionSchemaService collectionSchemaService;

    private CreateCollectionApplier applier() {
        return new CreateCollectionApplier(collectionSchemaService, JsonMapper.builder().build());
    }

    private static Proposal proposal(Map<String, Object> payload) {
        return Proposal.builder().kind(ProposalKind.CREATE_COLLECTION).payload(payload).finalPayload(payload).build();
    }

    @Test
    void creates_the_collection_as_ai() {
        when(collectionSchemaService.createAiCollection(any())).thenReturn(new CollectionDto().id(7L));
        applier().apply(proposal(Map.of(
                "slug", "job_applications",
                "name", "Job applications",
                "description", "Applications you sent",
                "matchKeys", List.of("company"),
                "displayConfig", Map.of("defaultView", "table"),
                "fields", List.of(
                        Map.of("slug", "company", "name", "Company", "type", "TEXT", "required", true),
                        Map.of("slug", "stage", "name", "Stage", "type", "ENUM", "options", Map.of("values", List.of("applied", "interview")))))));

        ArgumentCaptor<NewCollectionCommand> captor = ArgumentCaptor.forClass(NewCollectionCommand.class);
        verify(collectionSchemaService).createAiCollection(captor.capture());
        NewCollectionCommand command = captor.getValue();
        assertThat(command.getSlug()).isEqualTo("job_applications");
        assertThat(command.getMatchKeys()).containsExactly("company");
        assertThat(command.getDisplayConfig().getDefaultView()).isEqualTo("table");
        assertThat(command.getFields()).hasSize(2);
        assertThat(command.getFields().get(1).getType()).isEqualTo(CollectionFieldType.ENUM);
        assertThat(command.getFields().get(1).getOptions()).containsKey("values");
    }

    @Test
    void only_the_owner_may_approve() {
        assertThat(applier().requiresOwner()).isTrue();
        assertThat(applier().kind()).isEqualTo(ProposalKind.CREATE_COLLECTION);
    }

    @Test
    void malformed_payloads_are_rejected() {
        assertThatThrownBy(() -> applier().apply(proposal(Map.of("name", "No slug"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
        assertThatThrownBy(() -> applier().apply(proposal(Map.of("slug", "x_y", "name", "X", "fields", "nope"))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(collectionSchemaService);
    }

    @Test
    void returns_the_created_collection_as_result() {
        when(collectionSchemaService.createAiCollection(any())).thenReturn(new CollectionDto().id(7L));

        var result = applier().apply(proposal(Map.of("slug", "orders", "name", "Orders")));

        assertThat(result).containsEntry("collectionId", 7L);
    }
}

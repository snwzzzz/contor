package com.example.contor.proposal;

import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.service.CollectionSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * CREATE_COLLECTION: the payload is a {@link NewCollectionCommand} ({@code {"slug", "name", "description"?, "icon"?,
 * "matchKeys"?, "displayConfig"?, "fields"?}}). The collection is created with {@code created_by = AI}. Schema change,
 * so only the OWNER may approve it.
 */
@RequiredArgsConstructor
@Component
public class CreateCollectionApplier implements ProposalApplier {

    private final CollectionSchemaService collectionSchemaService;
    private final ObjectMapper objectMapper;

    @Override
    public ProposalKind kind() {
        return ProposalKind.CREATE_COLLECTION;
    }

    @Override
    public void apply(Proposal proposal) {
        Map<String, Object> payload = proposal.getFinalPayload();
        if (!(payload.get("slug") instanceof String) || !(payload.get("name") instanceof String)) {
            throw new IllegalArgumentException("payload.slug and payload.name are required");
        }
        NewCollectionCommand command;
        try {
            command = objectMapper.convertValue(payload, NewCollectionCommand.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new IllegalArgumentException("payload is not a valid collection definition");
        }
        collectionSchemaService.createAiCollection(command);
    }

    @Override
    public boolean requiresOwner() {
        return true;
    }
}

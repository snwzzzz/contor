package com.example.contor.proposal;

import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CREATE_RECORD: {@code {"collectionId", "title"?, "status"?, "values"?, "extra"?}}. The record is created as AI and
 * linked to the proposal's source email ({@code CREATED_FROM}).
 */
@RequiredArgsConstructor
@Component
public class CreateRecordApplier implements ProposalApplier {

    private final RecordService recordService;
    private final RecordSourceRepository recordSourceRepository;
    private final CollectionRepository collectionRepository;
    private final ExternalApiItemRepository externalApiItemRepository;

    @Override
    public ProposalKind kind() {
        return ProposalKind.CREATE_RECORD;
    }

    @Override
    public Map<String, Object> apply(Proposal proposal) {
        Map<String, Object> payload = proposal.getFinalPayload();
        Long collectionId = ProposalPayload.requiredId(payload, "collectionId");
        NewRecordCommand command = new NewRecordCommand()
                .title(ProposalPayload.optionalString(payload, "title"))
                .status(ProposalPayload.optionalString(payload, "status"))
                .values(ProposalPayload.optionalObject(payload, "values"))
                .extra(ProposalPayload.optionalObject(payload, "extra"));

        RecordDto record = recordService.createRecord(collectionId, command, ActorType.AI);

        if (proposal.getSourceEmail() != null) {
            recordSourceRepository.saveAndFlush(RecordSource.builder()
                    .collection(collectionRepository.getReferenceById(new Collection.CollectionId(collectionId)))
                    .recordId(record.getId())
                    .email(proposal.getSourceEmail())
                    .role(RecordSourceRole.CREATED_FROM)
                    .build());
        }
        ExternalApiItem item = proposal.getSourceApiItem();
        if (item != null) {
            Collection collection = collectionRepository.getReferenceById(new Collection.CollectionId(collectionId));
            recordSourceRepository.saveAndFlush(RecordSource.builder()
                    .collection(collection)
                    .recordId(record.getId())
                    .apiItem(item)
                    .role(RecordSourceRole.CREATED_FROM)
                    .build());
            // The item now has its record: later changes of the item update it.
            item.setCollection(collection);
            item.setRecordId(record.getId());
            externalApiItemRepository.saveAndFlush(item);
        }
        return Map.of("collectionId", collectionId, "recordId", record.getId());
    }
}

package com.example.contor.proposal;

import com.example.contor.api.model.UpdateRecordCommand;
import com.example.contor.domain.Collection;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * UPDATE_RECORD: {@code {"collectionId", "recordId", "title"?, "status"?, "values"?, "extra"?}} with partial-update
 * semantics. The record is linked to the proposal's source email ({@code UPDATED_FROM}).
 */
@RequiredArgsConstructor
@Component
public class UpdateRecordApplier implements ProposalApplier {

    private final RecordService recordService;
    private final RecordSourceRepository recordSourceRepository;
    private final CollectionRepository collectionRepository;

    @Override
    public ProposalKind kind() {
        return ProposalKind.UPDATE_RECORD;
    }

    @Override
    public Map<String, Object> apply(Proposal proposal) {
        Map<String, Object> payload = proposal.getFinalPayload();
        Long collectionId = ProposalPayload.requiredId(payload, "collectionId");
        Long recordId = ProposalPayload.requiredId(payload, "recordId");
        UpdateRecordCommand command = new UpdateRecordCommand()
                .title(ProposalPayload.optionalString(payload, "title"))
                .status(ProposalPayload.optionalString(payload, "status"))
                .values(ProposalPayload.optionalObject(payload, "values"))
                .extra(ProposalPayload.optionalObject(payload, "extra"));

        recordService.updateRecord(collectionId, recordId, command);

        if (proposal.getSourceEmail() != null) {
            recordSourceRepository.saveAndFlush(RecordSource.builder()
                    .collection(collectionRepository.getReferenceById(new Collection.CollectionId(collectionId)))
                    .recordId(recordId)
                    .email(proposal.getSourceEmail())
                    .role(RecordSourceRole.UPDATED_FROM)
                    .build());
        }
        // An app's item updates its record again with every change: linked once.
        if (proposal.getSourceApiItem() != null && !recordSourceRepository.existsByCollection_IdAndRecordIdAndApiItem_IdAndRole(
                new Collection.CollectionId(collectionId), recordId, proposal.getSourceApiItem().getId(), RecordSourceRole.UPDATED_FROM)) {
            recordSourceRepository.saveAndFlush(RecordSource.builder()
                    .collection(collectionRepository.getReferenceById(new Collection.CollectionId(collectionId)))
                    .recordId(recordId)
                    .apiItem(proposal.getSourceApiItem())
                    .role(RecordSourceRole.UPDATED_FROM)
                    .build());
        }
        return Map.of("collectionId", collectionId, "recordId", recordId);
    }
}

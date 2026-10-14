package com.example.contor.proposal;

import com.example.contor.api.model.NewRelationCommand;
import com.example.contor.api.model.RelationDto;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CREATE_RELATION: {@code {"relationTypeId", "from", "to"}} where each end is {@code {"collectionId", "recordId"}} (an
 * existing record) or {@code {"proposalId"}} (the record that proposal created: read from its {@code result}, so the
 * proposal must be applied, which the dependency rule guarantees). The relation is validated by {@link RelationService}
 * (type collections, records exist), created as AI and linked to the proposal's source email. An identical relation that
 * already exists is a no-op: the proposal is applied and its result names the existing relation.
 */
@RequiredArgsConstructor
@Component
public class CreateRelationApplier implements ProposalApplier {

    private final RelationService relationService;
    private final ProposalRepository proposalRepository;

    @Override
    public ProposalKind kind() {
        return ProposalKind.CREATE_RELATION;
    }

    @Override
    public Map<String, Object> apply(Proposal proposal) {
        Map<String, Object> payload = proposal.getFinalPayload();
        Long relationTypeId = ProposalPayload.requiredId(payload, "relationTypeId");
        Record from = endpoint(payload, "from");
        Record to = endpoint(payload, "to");
        NewRelationCommand command = new NewRelationCommand(relationTypeId, from.collectionId(), from.recordId(), to.collectionId(), to.recordId());

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            RelationDto created = relationService.createAiRelation(command, proposal.getSourceEmail(), proposal.getConfidence());
            result.put("relationId", created.getId());
            result.put("created", true);
        } catch (DuplicateResourceException e) {
            result.put("relationId", existing(relationTypeId, from, to));
            result.put("created", false);
        }
        return result;
    }

    private Long existing(Long relationTypeId, Record from, Record to) {
        return relationService.listRelations(from.collectionId(), from.recordId()).stream()
                .filter(relation -> relationTypeId.equals(relation.getRelationTypeId()))
                .filter(relation -> connects(relation, from, to) || connects(relation, to, from))
                .map(RelationDto::getId)
                .findFirst()
                .orElse(null);
    }

    private static boolean connects(RelationDto relation, Record from, Record to) {
        return from.collectionId().equals(relation.getFromCollectionId()) && from.recordId().equals(relation.getFromRecordId())
                && to.collectionId().equals(relation.getToCollectionId()) && to.recordId().equals(relation.getToRecordId());
    }

    private record Record(Long collectionId, Long recordId) {
    }

    private Record endpoint(Map<String, Object> payload, String key) {
        Map<String, Object> end = ProposalPayload.optionalObject(payload, key);
        if (end == null) {
            throw new IllegalArgumentException("payload." + key + " is required");
        }
        if (end.get("proposalId") instanceof Number number) {
            long proposalId = number.longValue();
            Map<String, Object> result = proposalRepository.findById(new Proposal.ProposalId(proposalId))
                    .map(Proposal::getResult)
                    .orElseThrow(() -> new IllegalArgumentException("payload." + key + " refers to proposal " + proposalId + ", which does not exist"));
            if (!(result.get("collectionId") instanceof Number collectionId) || !(result.get("recordId") instanceof Number recordId)) {
                throw new IllegalArgumentException("payload." + key + " refers to proposal " + proposalId + ", which has not created a record");
            }
            return new Record(collectionId.longValue(), recordId.longValue());
        }
        if (end.get("collectionId") instanceof Number collectionId && end.get("recordId") instanceof Number recordId) {
            return new Record(collectionId.longValue(), recordId.longValue());
        }
        throw new IllegalArgumentException("payload." + key + " needs a proposalId or a collectionId and recordId");
    }
}

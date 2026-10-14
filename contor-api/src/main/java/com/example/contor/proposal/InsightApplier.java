package com.example.contor.proposal;

import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * INSIGHT: {@code {"insightKey", "title", "observation", "whyItMatters", "evidenceEmailIds": [..], "followUps":
 * [{"collectionId", "title", "values"}], "source"}}. Approving creates the follow-ups that are left in the (possibly
 * edited) payload as AI records, each linked to every evidence email; an insight without follow-ups is just
 * acknowledged. Result: {@code {"records": [{"collectionId", "recordId"}]}}.
 */
@RequiredArgsConstructor
@Component
public class InsightApplier implements ProposalApplier {

    private final RecordService recordService;
    private final RecordSourceRepository recordSourceRepository;
    private final CollectionRepository collectionRepository;
    private final EmailMessageRepository emailMessageRepository;

    @Override
    public ProposalKind kind() {
        return ProposalKind.INSIGHT;
    }

    @Override
    public Map<String, Object> apply(Proposal proposal) {
        Map<String, Object> payload = proposal.getFinalPayload();
        List<Map<String, Object>> followUps = maps(payload.get("followUps"), "followUps");
        if (followUps.isEmpty()) {
            return Map.of("records", List.of());
        }
        for (Map<String, Object> followUp : followUps) {
            ProposalPayload.requiredId(followUp, "collectionId");
        }
        List<EmailMessage> evidence = emailMessageRepository.findAllById(ids(payload.get("evidenceEmailIds")));

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> followUp : followUps) {
            Long collectionId = ProposalPayload.requiredId(followUp, "collectionId");
            RecordDto record = recordService.createRecord(collectionId, new NewRecordCommand()
                    .title(ProposalPayload.optionalString(followUp, "title"))
                    .values(ProposalPayload.optionalObject(followUp, "values")), ActorType.AI);
            Collection collection = collectionRepository.getReferenceById(new Collection.CollectionId(collectionId));
            for (EmailMessage email : evidence) {
                recordSourceRepository.saveAndFlush(RecordSource.builder().collection(collection).recordId(record.getId())
                        .email(email).role(RecordSourceRole.CREATED_FROM).build());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("collectionId", collectionId);
            result.put("recordId", record.getId());
            created.add(result);
        }
        return Map.of("records", created);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value, String key) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>))) {
            throw new IllegalArgumentException("payload." + key + " must be a list of objects");
        }
        return (List<Map<String, Object>>) list;
    }

    private static List<EmailMessage.EmailMessageId> ids(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Number.class::isInstance).map(id -> new EmailMessage.EmailMessageId(((Number) id).longValue())).toList();
    }
}

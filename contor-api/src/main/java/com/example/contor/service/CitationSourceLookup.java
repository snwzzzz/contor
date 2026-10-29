package com.example.contor.service;

import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.SourceState;
import com.example.contor.domain.Citation;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Relation;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.exception.ResourceNotFoundException;
import com.example.contor.mapper.ReportMapper;
import com.example.contor.persistence.RelationRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Source states and current record titles for reading cited sources (reports and chat answers), each record and
 * relation read once. A field is CHANGED when its value differs from the snapshot; an email of another user's mailbox
 * is UNAVAILABLE, so nobody sees mail that is not theirs. One instance per request.
 */
final class CitationSourceLookup implements ReportMapper.SourceLookup {

    private final RecordService recordService;
    private final RelationRepository relationRepository;
    private final Long userId;
    private final Map<RecordRef, Optional<RecordDto>> records = new HashMap<>();
    private final Map<Long, Boolean> relations = new HashMap<>();

    /** @param userId the reader; {@code null} = nobody (every email is UNAVAILABLE) */
    CitationSourceLookup(RecordService recordService, RelationRepository relationRepository, Long userId) {
        this.recordService = recordService;
        this.relationRepository = relationRepository;
        this.userId = userId;
    }

    @Override
    public SourceState state(Citation citation) {
        return switch (citation.getType()) {
            case EMAIL_PARAGRAPH -> email(citation.getEmail());
            case RELATION -> relations.computeIfAbsent(citation.getRelationId(),
                    id -> relationRepository.existsById(new Relation.RelationId(id))) ? SourceState.CURRENT : SourceState.DELETED;
            case RECORD -> record(citation).isPresent() ? SourceState.CURRENT : SourceState.DELETED;
            case APP_ITEM -> citation.getApiItem() != null ? SourceState.CURRENT : SourceState.DELETED;
            case FIELD -> record(citation)
                    .map(current -> same(citation.getValueSnapshot() != null ? citation.getValueSnapshot().get("value") : null,
                            current.getValues().get(citation.getFieldSlug())) ? SourceState.CURRENT : SourceState.CHANGED)
                    .orElse(SourceState.DELETED);
        };
    }

    @Override
    public String recordTitle(Long collectionId, Long recordId) {
        return record(collectionId, recordId).map(RecordDto::getTitle).orElse(null);
    }

    private SourceState email(EmailMessage email) {
        if (email == null) {
            return SourceState.DELETED;
        }
        Long owner = email.getAccount() != null && email.getAccount().getOwner() != null ? email.getAccount().getOwner().getId().id() : null;
        return Objects.equals(owner, userId) ? SourceState.CURRENT : SourceState.UNAVAILABLE;
    }

    private Optional<RecordDto> record(Citation citation) {
        return citation.getCollection() == null || citation.getRecordId() == null ? Optional.empty()
                : record(citation.getCollection().getId().id(), citation.getRecordId());
    }

    private Optional<RecordDto> record(Long collectionId, Long recordId) {
        return records.computeIfAbsent(new RecordRef(collectionId, recordId), ref -> {
            try {
                return Optional.of(recordService.getRecord(collectionId, recordId));
            } catch (ResourceNotFoundException e) {
                return Optional.empty();
            }
        });
    }

    /** Equal values, whatever number type or map order JSON gave them ({@code 110} = {@code 110.0}). */
    static boolean same(Object a, Object b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    private static Object normalize(Object value) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return number.toString();
            }
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), normalize(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(CitationSourceLookup::normalize).collect(Collectors.toList());
        }
        return value;
    }
}

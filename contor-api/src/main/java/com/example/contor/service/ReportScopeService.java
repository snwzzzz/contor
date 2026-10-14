package com.example.contor.service;

import com.example.contor.ai.EmailParagraphs;
import com.example.contor.ai.EvidencePack;
import com.example.contor.api.model.RecordDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Relation;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.persistence.RelationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What a report is about (R4): the anchor record and the connected records the user picked, checked to be reachable
 * through relations within the chosen depth, and the {@link EvidencePack} built from them — every record's facts, the
 * relations between the records of the scope and, with mail access, the owner's emails linked to them (record_source)
 * with their conversations, newest {@link ReportScope#MAX_EMAILS} first.
 */
@RequiredArgsConstructor
@Service
public class ReportScopeService {

    private final CollectionRepository collectionRepository;
    private final RelationRepository relationRepository;
    private final RecordSourceRepository recordSourceRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final RecordService recordService;
    private final AiProperties aiProperties;

    /**
     * The anchor first, then the connected records; each must be reachable from the anchor within {@code depth}
     * relations (400 otherwise). The anchor must exist (404).
     */
    @Transactional
    public ReportScope scope(RecordRef anchor, List<RecordRef> connected, int depth) {
        recordService.getRecord(anchor.collectionId(), anchor.recordId());
        List<RecordRef> records = new ArrayList<>(List.of(anchor));
        List<RecordRef> wanted = connected != null ? connected.stream().filter(ref -> !ref.equals(anchor)).distinct().toList() : List.of();
        if (!wanted.isEmpty()) {
            Set<RecordRef> reachable = reachable(anchor, depth);
            for (RecordRef ref : wanted) {
                if (!reachable.contains(ref)) {
                    throw new IllegalArgumentException("record " + ref.recordId() + " of collection " + ref.collectionId()
                            + " is not connected to this record within " + depth + (depth == 1 ? " step" : " steps"));
                }
                records.add(ref);
            }
        }
        return new ReportScope(records, depth, List.of());
    }

    /** The records reachable from {@code anchor} through at most {@code depth} relations, in either direction. */
    @Transactional
    public Set<RecordRef> reachable(RecordRef anchor, int depth) {
        Set<RecordRef> seen = new LinkedHashSet<>(List.of(anchor));
        List<RecordRef> frontier = List.of(anchor);
        for (int step = 0; step < depth && !frontier.isEmpty(); step++) {
            List<RecordRef> next = new ArrayList<>();
            for (RecordRef ref : frontier) {
                for (Relation relation : relationsOf(ref)) {
                    RecordRef other = otherEnd(relation, ref);
                    if (seen.add(other)) {
                        next.add(other);
                    }
                }
            }
            frontier = next;
        }
        seen.remove(anchor);
        return seen;
    }

    /** The evidence for a report over {@code scope}; emails only with {@code mailAccess} and only from {@code owner}'s mailboxes. */
    @Transactional
    public EvidencePack collect(ReportScope scope, String prompt, AppUser.AppUserId owner, boolean mailAccess) {
        EvidencePack.Builder pack = EvidencePack.builder();
        Map<RecordRef, String> titles = new LinkedHashMap<>();
        RecordRef anchor = scope.records().getFirst();
        for (RecordRef ref : scope.records()) {
            Collection collection = collectionRepository.findById(new Collection.CollectionId(ref.collectionId()))
                    .orElseThrow(() -> new CollectionNotFoundException(ref.collectionId()));
            RecordDto record = recordService.getRecord(ref.collectionId(), ref.recordId());
            String title = record.getTitle() != null && !record.getTitle().isBlank()
                    ? record.getTitle() : collection.getName() + " #" + ref.recordId();
            titles.put(ref, title);
            List<EvidencePack.FieldInput> fields = collection.getFields().stream()
                    .filter(field -> field.getArchivedAt() == null)
                    .sorted(Comparator.comparingInt(CollectionField::getPosition))
                    .map(field -> new EvidencePack.FieldInput(field.getSlug().value(), field.getName(), field.getType(),
                            record.getValues().get(field.getSlug().value())))
                    .toList();
            pack.record(new EvidencePack.RecordInput(ref.collectionId(), collection.getName(), ref.recordId(), title, ref.equals(anchor), fields));
        }
        Map<Long, Relation> relations = new LinkedHashMap<>();
        for (RecordRef ref : scope.records()) {
            for (Relation relation : relationsOf(ref)) {
                if (titles.containsKey(otherEnd(relation, ref))) {
                    relations.putIfAbsent(relation.getId().id(), relation);
                }
            }
        }
        for (Relation relation : relations.values()) {
            RecordRef from = new RecordRef(relation.getFromCollection().getId().id(), relation.getFromRecordId());
            RecordRef to = new RecordRef(relation.getToCollection().getId().id(), relation.getToRecordId());
            pack.relation(new EvidencePack.RelationInput(relation.getId().id(), from.collectionId(), from.recordId(), titles.get(from),
                    relation.getRelationType().getName(), to.collectionId(), to.recordId(), titles.get(to),
                    date(relation.getValidFrom()), date(relation.getValidTo())));
        }
        if (mailAccess) {
            emails(scope, owner).forEach(email -> pack.email(new EvidencePack.EmailInput(email.getId().id(), date(email.getReceivedAt()),
                    email.getFromName(), email.getFromAddress(), "sent".equals(email.getFolder()), email.getSubject(),
                    EmailParagraphs.split(email.getBodyText(), email.getBodyHtml()))));
        }
        return pack.build(prompt, EvidencePack.DEFAULT_BUDGET);
    }

    /** The owner's emails linked to the scope's records and the rest of their conversations, newest first. */
    private List<EmailMessage> emails(ReportScope scope, AppUser.AppUserId owner) {
        Map<Long, EmailMessage> emails = new LinkedHashMap<>();
        for (RecordRef ref : scope.records()) {
            recordSourceRepository.findByCollection_IdAndRecordId(new Collection.CollectionId(ref.collectionId()), ref.recordId()).stream()
                    .map(source -> source.getEmail())
                    .filter(email -> email.getAccount().getOwner().getId().equals(owner))
                    .forEach(email -> emails.putIfAbsent(email.getId().id(), email));
        }
        Set<String> conversations = new LinkedHashSet<>();
        for (EmailMessage email : List.copyOf(emails.values())) {
            if (email.getConversationId() != null && conversations.add(email.getAccount().getId().id() + "/" + email.getConversationId())) {
                emailMessageRepository.findThread(email.getAccount().getId(), email.getConversationId())
                        .forEach(message -> emails.putIfAbsent(message.getId().id(), message));
            }
        }
        return emails.values().stream()
                .sorted(Comparator.comparing(EmailMessage::getReceivedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ReportScope.MAX_EMAILS)
                .toList();
    }

    private List<Relation> relationsOf(RecordRef ref) {
        Collection.CollectionId collectionId = new Collection.CollectionId(ref.collectionId());
        List<Relation> relations = new ArrayList<>(relationRepository.findByFromCollection_IdAndFromRecordId(collectionId, ref.recordId()));
        relations.addAll(relationRepository.findByToCollection_IdAndToRecordId(collectionId, ref.recordId()));
        return relations;
    }

    private static RecordRef otherEnd(Relation relation, RecordRef ref) {
        RecordRef from = new RecordRef(relation.getFromCollection().getId().id(), relation.getFromRecordId());
        return from.equals(ref) ? new RecordRef(relation.getToCollection().getId().id(), relation.getToRecordId()) : from;
    }

    private LocalDate date(Instant instant) {
        return instant == null ? null : instant.atZone(Objects.requireNonNull(aiProperties.timezone())).toLocalDate();
    }
}

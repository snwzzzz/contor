package com.example.contor.service;

import com.example.contor.ai.CollectionPlan;
import com.example.contor.ai.EmailParagraphs;
import com.example.contor.ai.EmbeddingClient;
import com.example.contor.ai.EvidencePack;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.config.MailContextProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Relation;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.ResourceNotFoundException;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailEmbeddingRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.EmailSpecifications;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.persistence.RelationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * with their conversations, newest {@link ReportScope#MAX_EMAILS} first. For a report over a collection
 * ({@link #collectCollection}) the pack holds the figures, an overview and the collection's records instead.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReportScopeService {

    private final CollectionRepository collectionRepository;
    private final RelationRepository relationRepository;
    private final RecordSourceRepository recordSourceRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final RecordService recordService;
    private final EmailEmbeddingRepository emailEmbeddingRepository;
    private final AiProperties aiProperties;
    private final MailContextProperties mailContextProperties;
    private final CollectionAnalysisService collectionAnalysisService;
    private final EmbeddingClient embeddingClient;

    /** Records of a collection report shown to the model (newest first), as far as they fit the budget. */
    static final int COLLECTION_RECORDS = 60;

    /** Emails from the rest of the mailbox close in meaning to the record's own emails. */
    static final int RELATED_EMAILS = 10;
    /** Records linked to those related emails (other orders, complaints…). */
    static final int RELATED_RECORDS = 8;
    /** The record's own emails, leaving room for the related ones within {@link ReportScope#MAX_EMAILS}. */
    static final int OWN_EMAILS = ReportScope.MAX_EMAILS - RELATED_EMAILS;
    /** Seeds of the related-email search: the newest of the record's own emails. */
    private static final int SEARCH_SEEDS = 10;
    private static final Instant ALL_TIME_FROM = Instant.EPOCH;
    private static final Instant ALL_TIME_TO = Instant.parse("9999-01-01T00:00:00Z");

    /** Records of a chat answer whose connections are followed (the first ones: the focus and the best search hits). */
    static final int QUESTION_EXPANDED = 3;
    /** Newest emails from each address of a record found for a chat question (a person's own mail). */
    static final int CONTACT_EMAILS = 5;
    /** Newest emails matching each search of a chat plan (sender, recipients, subject or text). */
    static final int SEARCH_EMAILS = 5;
    /** Emails closest in meaning to a chat question. */
    static final int QUESTION_EMAILS = 10;
    /** Cosine distance up to which an email counts as close to a question (a question is shorter than an email). */
    static final double QUESTION_MAX_DISTANCE = 0.6;

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
            EvidencePack.RecordInput input = recordInput(ref, ref.equals(anchor), false);
            titles.put(ref, input.title());
            pack.record(input);
        }
        relations(pack, titles);
        if (mailAccess) {
            List<EmailMessage> own = emails(scope.records(), owner);
            own.forEach(email -> pack.email(emailInput(email, false)));
            List<EmailMessage> related = related(own, owner);
            related.forEach(email -> pack.email(emailInput(email, true)));
            relatedRecords(related, titles.keySet()).forEach(pack::record);
        }
        return pack.build(prompt, EvidencePack.DEFAULT_BUDGET);
    }

    /**
     * The evidence for a chat answer (no anchor): the records found for the question, the connections of the first
     * {@link #QUESTION_EXPANDED} of them and the relations between all of them; with {@code mailAccess} the owner's
     * emails linked to them, the newest emails from their email addresses, the newest emails matching each of
     * {@code searches} (so mail is found without records or embeddings), the emails closest in meaning to the question
     * and the records linked to those. Only mailboxes the AI may read; hidden ones only with {@code readHidden}.
     * Deleted records are skipped; without the embedding model the question's emails are left out.
     */
    @Transactional
    public EvidencePack collectQuestion(List<RecordRef> found, String question, List<String> searches, AppUser.AppUserId owner,
                                        boolean mailAccess, boolean readHidden) {
        Set<RecordRef> refs = new LinkedHashSet<>(found);
        for (RecordRef ref : found.stream().limit(QUESTION_EXPANDED).toList()) {
            for (RecordRef connected : reachable(ref, 1)) {
                if (refs.size() >= ReportScope.MAX_RECORDS) {
                    break;
                }
                refs.add(connected);
            }
        }
        EvidencePack.Builder pack = EvidencePack.builder().recordsHeading("Records found for the question:", true);
        Map<RecordRef, String> titles = new LinkedHashMap<>();
        List<EvidencePack.RecordInput> inputs = new ArrayList<>();
        for (RecordRef ref : refs) {
            try {
                EvidencePack.RecordInput input = recordInput(ref, false, false);
                titles.put(ref, input.title());
                inputs.add(input);
                pack.record(input);
            } catch (ResourceNotFoundException e) {
                // deleted since it was found or focused
            }
        }
        relations(pack, titles);
        if (mailAccess && owner != null) {
            Map<Long, EmailMessage> emails = new LinkedHashMap<>();
            emails(List.copyOf(titles.keySet()), owner).forEach(email -> emails.putIfAbsent(email.getId().id(), email));
            for (EvidencePack.RecordInput input : inputs) {
                for (EvidencePack.FieldInput field : input.fields()) {
                    if (field.type() == FieldType.EMAIL && field.value() instanceof String address && !address.isBlank()) {
                        emailMessageRepository.findFromSenderBefore(owner, address.strip(), ALL_TIME_TO, PageRequest.of(0, CONTACT_EMAILS))
                                .forEach(email -> emails.putIfAbsent(email.getId().id(), email));
                    }
                }
            }
            List<EmailMessage> searched = new ArrayList<>();
            for (String search : searches) {
                searched.addAll(emailMessageRepository.findAll(EmailSpecifications.matching(new EmailSpecifications.Criteria(owner, null, null, null, search)),
                        PageRequest.of(0, SEARCH_EMAILS, Sort.by(Sort.Direction.DESC, "receivedAt"))).getContent());
            }
            searched.forEach(email -> emails.putIfAbsent(email.getId().id(), email));
            List<EmailMessage> asked = asked(question, owner, emails.keySet());
            asked.forEach(email -> emails.putIfAbsent(email.getId().id(), email));
            List<EmailMessage> readable = emails.values().stream().filter(email -> readable(email.getAccount(), readHidden))
                    .limit(ReportScope.MAX_EMAILS).toList();
            readable.forEach(email -> pack.email(emailInput(email, false)));
            List<EmailMessage> matched = new ArrayList<>(searched);
            matched.addAll(asked);
            relatedRecords(matched.stream().filter(readable::contains).distinct().toList(), titles.keySet()).forEach(pack::record);
        }
        return pack.build(question, EvidencePack.DEFAULT_BUDGET);
    }

    /** The owner's inbox emails closest in meaning to {@code question}, not yet in {@code known}; none without the embedding model. */
    private List<EmailMessage> asked(String question, AppUser.AppUserId owner, Set<Long> known) {
        float[] vector;
        try {
            vector = embeddingClient.embed(question);
        } catch (RuntimeException e) {
            log.warn("The question could not be embedded: {}", e.getClass().getSimpleName());
            return List.of();
        }
        List<EmailMessage.EmailMessageId> ids = emailEmbeddingRepository.findNearestTo(owner, vector, aiProperties.models().embedding(),
                        QUESTION_MAX_DISTANCE, QUESTION_EMAILS + known.size()).stream()
                .map(near -> near.emailId())
                .filter(id -> !known.contains(id))
                .limit(QUESTION_EMAILS)
                .map(EmailMessage.EmailMessageId::new)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, EmailMessage> byId = new HashMap<>();
        emailMessageRepository.findAllById(ids).forEach(email -> byId.put(email.getId().id(), email));
        return ids.stream().map(id -> byId.get(id.id()))
                .filter(email -> email != null && email.getAccount().getOwner().getId().equals(owner))
                .toList();
    }

    /** Whether the AI may read mail of {@code account}: AI turned on for it, and not hidden unless hidden ones may be read. */
    private static boolean readable(MailAccount account, boolean readHidden) {
        return account != null && account.isAiEnabled() && (readHidden || !account.isHidden());
    }

    /** The relations between the records in {@code titles}, each once. */
    private void relations(EvidencePack.Builder pack, Map<RecordRef, String> titles) {
        Map<Long, Relation> relations = new LinkedHashMap<>();
        for (RecordRef ref : titles.keySet()) {
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
    }

    /**
     * The evidence for a report over a collection: the figures of {@code plan} computed over all matching records, an
     * overview of the collection, and its newest matching records as far as they fit the budget. No emails.
     */
    @Transactional
    public EvidencePack collectCollection(Long collectionId, ReportScope.CollectionScope scope, CollectionPlan plan, String prompt) {
        Collection collection = collectionRepository.findById(new Collection.CollectionId(collectionId))
                .orElseThrow(() -> new CollectionNotFoundException(collectionId));
        EvidencePack.Builder pack = EvidencePack.builder();
        collectionAnalysisService.run(plan, collectionId, scope).forEach(pack::analysis);
        pack.overview(collectionAnalysisService.overview(collectionId, scope));
        RecordPageDto page = recordService.listRecords(collectionId, scope.filters(), scope.search(), null, 0, COLLECTION_RECORDS);
        pack.recordsHeading("Records of " + collection.getName() + " (newest first, " + page.getItems().size() + " of "
                + page.getTotalElements() + " matching records):", true);
        page.getItems().forEach(record -> pack.record(recordInput(collection, record, false, false)));
        return pack.build(prompt, EvidencePack.DEFAULT_BUDGET);
    }

    private EvidencePack.RecordInput recordInput(RecordRef ref, boolean anchor, boolean related) {
        Collection collection = collectionRepository.findById(new Collection.CollectionId(ref.collectionId()))
                .orElseThrow(() -> new CollectionNotFoundException(ref.collectionId()));
        return recordInput(collection, recordService.getRecord(ref.collectionId(), ref.recordId()), anchor, related);
    }

    private static EvidencePack.RecordInput recordInput(Collection collection, RecordDto record, boolean anchor, boolean related) {
        String title = record.getTitle() != null && !record.getTitle().isBlank()
                ? record.getTitle() : collection.getName() + " #" + record.getId();
        List<EvidencePack.FieldInput> fields = collection.getFields().stream()
                .filter(field -> field.getArchivedAt() == null)
                .sorted(Comparator.comparingInt(CollectionField::getPosition))
                .map(field -> new EvidencePack.FieldInput(field.getSlug().value(), field.getName(), field.getType(),
                        record.getValues().get(field.getSlug().value())))
                .toList();
        return new EvidencePack.RecordInput(collection.getId().id(), collection.getName(), record.getId(), title, anchor, fields, related);
    }

    private EvidencePack.EmailInput emailInput(EmailMessage email, boolean related) {
        return new EvidencePack.EmailInput(email.getId().id(), date(email.getReceivedAt()), email.getFromName(), email.getFromAddress(),
                "sent".equals(email.getFolder()), email.getSubject(), EmailParagraphs.split(email.getBodyText(), email.getBodyHtml()),
                related);
    }

    /**
     * The owner's inbox emails closest in meaning to the newest of the record's own emails (embeddings, any date), at most
     * {@link #RELATED_EMAILS}, closest first: other cases with the same customer, product or problem.
     */
    private List<EmailMessage> related(List<EmailMessage> own, AppUser.AppUserId owner) {
        Set<Long> seen = new HashSet<>();
        own.forEach(email -> seen.add(email.getId().id()));
        Map<Long, Double> closest = new HashMap<>();
        for (EmailMessage email : own.stream().limit(SEARCH_SEEDS).toList()) {
            emailEmbeddingRepository.findNearest(owner, email.getId().id(), aiProperties.models().embedding(), ALL_TIME_FROM, ALL_TIME_TO,
                            mailContextProperties.relatedMaxDistance(), RELATED_EMAILS + seen.size()).stream()
                    .filter(near -> !seen.contains(near.emailId()))
                    .forEach(near -> closest.merge(near.emailId(), near.distance(), Math::min));
        }
        List<EmailMessage.EmailMessageId> ids = closest.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(RELATED_EMAILS)
                .map(entry -> new EmailMessage.EmailMessageId(entry.getKey()))
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, EmailMessage> byId = new HashMap<>();
        emailMessageRepository.findAllById(ids).forEach(email -> byId.put(email.getId().id(), email));
        return ids.stream().map(id -> byId.get(id.id()))
                .filter(email -> email != null && email.getAccount().getOwner().getId().equals(owner))
                .toList();
    }

    /** Records linked to the related emails that are not in the scope, at most {@link #RELATED_RECORDS}; deleted ones are skipped. */
    private List<EvidencePack.RecordInput> relatedRecords(List<EmailMessage> related, Set<RecordRef> inScope) {
        if (related.isEmpty()) {
            return List.of();
        }
        Set<RecordRef> refs = new LinkedHashSet<>();
        recordSourceRepository.findByEmail_IdIn(related.stream().map(EmailMessage::getId).toList()).forEach(source ->
                refs.add(new RecordRef(source.getCollection().getId().id(), source.getRecordId())));
        refs.removeAll(inScope);
        List<EvidencePack.RecordInput> records = new ArrayList<>();
        for (RecordRef ref : refs) {
            if (records.size() == RELATED_RECORDS) {
                break;
            }
            try {
                records.add(recordInput(ref, false, true));
            } catch (ResourceNotFoundException e) {
                // deleted since: not a source any more
            }
        }
        return records;
    }

    /** The owner's emails linked to the scope's records and the rest of their conversations, newest first. */
    private List<EmailMessage> emails(List<RecordRef> records, AppUser.AppUserId owner) {
        Map<Long, EmailMessage> emails = new LinkedHashMap<>();
        for (RecordRef ref : records) {
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
                .limit(OWN_EMAILS)
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

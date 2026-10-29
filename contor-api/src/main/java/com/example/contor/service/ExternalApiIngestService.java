package com.example.contor.service;

import com.example.contor.ai.ProposalDraft;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.MappingState;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.externalapi.ExternalApiMappingActivated;
import com.example.contor.externalapi.JsonItems;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.ExternalApiMappingRepository;
import com.example.contor.persistence.ProposalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns the items of an external API endpoint into records through its ACTIVE mapping (ADR 0012, no model call): a new
 * item (or one without a record) becomes a CREATE_RECORD proposal, a changed item with a record an UPDATE_RECORD; a gone
 * item changes nothing. A pending proposal of an item follows the item's changes instead of a second one. Proposals
 * follow the owner's {@code externalApis} permission (OFF: none, SUGGEST, AUTO with a fallback to a suggestion when
 * applying fails) and only for apps the assistant may learn from. Without any mapping the assistant is asked for one.
 * Like the mail worker it runs outside a transaction: each proposal is submitted in its own, so one that fails to apply
 * loses nothing else.
 */
@Slf4j
@Service
public class ExternalApiIngestService {

    private static final int MAX_TITLE = 500;

    private final ExternalApiMappingRepository externalApiMappingRepository;
    private final ExternalApiItemRepository externalApiItemRepository;
    private final AiSettingsRepository aiSettingsRepository;
    private final ProposalRepository proposalRepository;
    private final ProposalService proposalService;
    private final ExternalApiMappingService externalApiMappingService;
    private final JsonItems jsonItems;
    private final TransactionTemplate transactions;

    public ExternalApiIngestService(ExternalApiMappingRepository externalApiMappingRepository, ExternalApiItemRepository externalApiItemRepository,
                                    AiSettingsRepository aiSettingsRepository, ProposalRepository proposalRepository, ProposalService proposalService,
                                    ExternalApiMappingService externalApiMappingService, JsonItems jsonItems,
                                    PlatformTransactionManager transactionManager) {
        this.externalApiMappingRepository = externalApiMappingRepository;
        this.externalApiItemRepository = externalApiItemRepository;
        this.aiSettingsRepository = aiSettingsRepository;
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
        this.externalApiMappingService = externalApiMappingService;
        this.jsonItems = jsonItems;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** What to submit for one item, prepared inside a transaction. */
    private record Submission(ProposalDraft draft, ExternalApiItem item, AiPermissionLevel level) {
    }

    /** After a poll: the endpoint's items that were new or changed in it. */
    public void afterPoll(long endpointId, List<Long> itemIds) {
        if (itemIds.isEmpty()) return;
        boolean[] askForMapping = {false};
        List<Submission> submissions = transactions.execute(status -> {
            List<ExternalApiItem> items = externalApiItemRepository.findAllById(itemIds);
            if (items.isEmpty()) return List.of();
            ExternalApiEndpoint endpoint = items.getFirst().getEndpoint();
            List<ExternalApiMapping> mappings = externalApiMappingRepository.findByEndpoint_Id(endpoint.getId());
            Optional<AiPermissionLevel> level = level(endpoint.getApi());
            if (level.isEmpty()) return List.of();
            if (mappings.isEmpty()) {
                askForMapping[0] = true;
                return List.of();
            }
            return mappings.stream().filter(mapping -> mapping.getState() == MappingState.ACTIVE).findFirst()
                    .map(mapping -> prepare(mapping, items, level.get()))
                    .orElse(List.of());
        });
        if (askForMapping[0]) externalApiMappingService.queue(endpointId);
        submit(submissions != null ? submissions : List.of());
    }

    /** Once an approved mapping is committed, its endpoint's current items are proposed (on their own thread: a fresh transaction each). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMappingActivated(ExternalApiMappingActivated event) {
        Thread.ofVirtual().name("external-api-ingest").start(() -> {
            try {
                ingestCurrent(event.endpointId());
            } catch (RuntimeException e) {
                log.warn("Proposing the items of external API endpoint {} failed", event.endpointId(), e);
            }
        });
    }

    /** After a mapping was approved: the endpoint's current items that have no record yet. */
    public void ingestCurrent(long endpointId) {
        List<Submission> submissions = transactions.execute(status -> {
            ExternalApiEndpoint.ExternalApiEndpointId id = new ExternalApiEndpoint.ExternalApiEndpointId(endpointId);
            Optional<ExternalApiMapping> active = externalApiMappingRepository.findByEndpoint_Id(id).stream()
                    .filter(mapping -> mapping.getState() == MappingState.ACTIVE).findFirst();
            if (active.isEmpty()) return List.<Submission>of();
            Optional<AiPermissionLevel> level = level(active.get().getEndpoint().getApi());
            if (level.isEmpty()) return List.<Submission>of();
            List<ExternalApiItem> unmapped = externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(id).stream()
                    .filter(item -> item.getRecordId() == null).toList();
            return prepare(active.get(), unmapped, level.get());
        });
        submit(submissions != null ? submissions : List.of());
    }

    /** The owner's permission for the app, empty when nothing may be proposed. */
    private Optional<AiPermissionLevel> level(ExternalApi api) {
        if (!api.isAiAnalysis()) return Optional.empty();
        AiPermissionLevel level = aiSettingsRepository.findByUser_Id(api.getOwner().getId()).map(AiSettings::getExternalApis)
                .orElse(AiPermissionLevel.SUGGEST);
        return level.allowed() ? Optional.of(level) : Optional.empty();
    }

    private List<Submission> prepare(ExternalApiMapping mapping, List<ExternalApiItem> items, AiPermissionLevel level) {
        List<Submission> submissions = new ArrayList<>();
        ExternalApiEndpoint endpoint = mapping.getEndpoint();
        String reasoning = "From " + endpoint.getApi().getName() + " · " + endpoint.getName();
        for (ExternalApiItem item : items) {
            Map<String, Object> payload = payload(mapping, item);
            if (payload == null) continue;
            Optional<Proposal> pending = proposalRepository.findFirstBySourceApiItem_IdAndStatus(item.getId(), ProposalStatus.PENDING);
            if (pending.isPresent()) {
                // One suggestion per item: it follows the item's latest values.
                pending.get().setPayload(payload);
                proposalRepository.saveAndFlush(pending.get());
                continue;
            }
            ProposalKind kind = payload.containsKey("recordId") ? ProposalKind.UPDATE_RECORD : ProposalKind.CREATE_RECORD;
            submissions.add(new Submission(new ProposalDraft(kind, payload, null, reasoning), item, level));
        }
        return submissions;
    }

    /** The record the item becomes under {@code mapping}; {@code null} when no mapped value is in it. */
    private Map<String, Object> payload(ExternalApiMapping mapping, ExternalApiItem item) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ApiFieldMapping field : mapping.getFieldMap()) {
            jsonItems.value(item.getData(), new JsonPathExpr(field.path())).ifPresent(value -> values.put(field.fieldSlug(), value));
        }
        String title = mapping.getTitlePath() == null ? null : jsonItems.value(item.getData(), mapping.getTitlePath())
                .map(String::valueOf).map(text -> text.length() > MAX_TITLE ? text.substring(0, MAX_TITLE) : text).orElse(null);
        if (values.isEmpty() && title == null) return null;

        Long collectionId = mapping.getCollection().getId().id();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectionId", collectionId);
        boolean sameCollection = item.getCollection() != null && Objects.equals(item.getCollection().getId(), mapping.getCollection().getId());
        if (item.getRecordId() != null && sameCollection) payload.put("recordId", item.getRecordId());
        if (title != null) payload.put("title", title);
        payload.put("values", values);
        return payload;
    }

    private void submit(List<Submission> submissions) {
        for (Submission submission : submissions) {
            if (submission.level() == AiPermissionLevel.AUTO) {
                try {
                    proposalService.submitDraftFromApiItem(submission.draft(), submission.item(), AiPermissionLevel.AUTO);
                    continue;
                } catch (RuntimeException e) {
                    log.info("Auto-applying a {} from external API item {} failed, keeping it as a suggestion: {}",
                            submission.draft().kind(), submission.item().getId(), e.getClass().getSimpleName());
                }
            }
            try {
                proposalService.submitDraftFromApiItem(submission.draft(), submission.item(), AiPermissionLevel.SUGGEST);
            } catch (RuntimeException e) {
                log.warn("Proposing a {} from external API item {} failed", submission.draft().kind(), submission.item().getId(), e);
            }
        }
    }
}

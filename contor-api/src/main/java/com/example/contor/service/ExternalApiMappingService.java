package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.MappingState;
import com.example.contor.domain.ProposalKind;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.ExternalApiMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks the assistant how the items of an endpoint without any mapping become records ({@link ApiMappingModelService},
 * on the AI thread, one run per endpoint at a time) and stores the answer as a PROPOSED mapping with a MAP_API_ENDPOINT
 * proposal to approve it. Learn once, then map in code (ADR 0012): approved, the mapping turns items into records without
 * the model. When nothing fits, the endpoint is asked about again only after {@link #RETRY_AFTER}. Model calls run
 * outside transactions.
 */
@Slf4j
@Service
public class ExternalApiMappingService {

    static final Duration RETRY_AFTER = Duration.ofDays(1);

    private final ExternalApiEndpointRepository externalApiEndpointRepository;
    private final ExternalApiMappingRepository externalApiMappingRepository;
    private final ExternalApiItemRepository externalApiItemRepository;
    private final CollectionSchemaService collectionSchemaService;
    private final CollectionRepository collectionRepository;
    private final ApiMappingModelService apiMappingModelService;
    private final ProposalService proposalService;
    private final AiModelService aiModelService;
    private final AiSettingsRepository aiSettingsRepository;
    private final AiExecutors aiExecutors;
    private final AiProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> askedAt = new ConcurrentHashMap<>();

    public ExternalApiMappingService(ExternalApiEndpointRepository externalApiEndpointRepository,
                                     ExternalApiMappingRepository externalApiMappingRepository, ExternalApiItemRepository externalApiItemRepository,
                                     CollectionSchemaService collectionSchemaService, CollectionRepository collectionRepository,
                                     ApiMappingModelService apiMappingModelService, ProposalService proposalService, AiModelService aiModelService,
                                     AiSettingsRepository aiSettingsRepository, AiExecutors aiExecutors, AiProperties properties,
                                     PlatformTransactionManager transactionManager, Clock clock) {
        this.externalApiEndpointRepository = externalApiEndpointRepository;
        this.externalApiMappingRepository = externalApiMappingRepository;
        this.externalApiItemRepository = externalApiItemRepository;
        this.collectionSchemaService = collectionSchemaService;
        this.collectionRepository = collectionRepository;
        this.apiMappingModelService = apiMappingModelService;
        this.proposalService = proposalService;
        this.aiModelService = aiModelService;
        this.aiSettingsRepository = aiSettingsRepository;
        this.aiExecutors = aiExecutors;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** What the model is asked about one endpoint, read inside a transaction. */
    private record Question(String apiName, String endpointName, String description, List<Map<String, Object>> samples,
                            List<CollectionDto> collections) {
    }

    /** Asks for a mapping of the endpoint on the AI thread; a run already queued for it is enough. */
    public void queue(long endpointId) {
        if (!running.add(endpointId)) return;
        aiExecutors.analysis().execute(() -> {
            try {
                propose(endpointId);
            } catch (RuntimeException e) {
                log.warn("Proposing a mapping for external API endpoint {} failed", endpointId, e);
            } finally {
                running.remove(endpointId);
            }
        });
    }

    /** Asks the model now (the AI thread's job). */
    public void propose(long endpointId) {
        Instant now = clock.instant();
        Instant asked = askedAt.get(endpointId);
        if ((asked != null && now.isBefore(asked.plus(RETRY_AFTER))) || !aiModelService.isReady()) return;
        ExternalApiEndpoint.ExternalApiEndpointId id = new ExternalApiEndpoint.ExternalApiEndpointId(endpointId);
        Question question = transactions.execute(status -> question(id));
        if (question == null) return;

        askedAt.put(endpointId, now);
        Optional<ApiMappingModelService.MappingDraft> draft = apiMappingModelService.propose(question.apiName(), question.endpointName(),
                question.description(), question.samples(), question.collections());
        if (draft.isEmpty()) {
            log.info("No collection fits the items of external API endpoint {}", endpointId);
            return;
        }
        transactions.executeWithoutResult(status -> store(id, draft.get()));
    }

    /** {@code null} when the endpoint is gone, mapped already, without items, or the assistant may not learn from it. */
    private Question question(ExternalApiEndpoint.ExternalApiEndpointId id) {
        ExternalApiEndpoint endpoint = externalApiEndpointRepository.findById(id).orElse(null);
        if (endpoint == null || !externalApiMappingRepository.findByEndpoint_Id(id).isEmpty() || !allowed(endpoint.getApi())) return null;
        List<Map<String, Object>> samples = externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(id).stream()
                .limit(ApiMappingModelService.MAX_SAMPLES).<Map<String, Object>>map(item -> new LinkedHashMap<>(item.getData())).toList();
        if (samples.isEmpty()) return null;
        return new Question(endpoint.getApi().getName(), endpoint.getName(), endpoint.getDescription(), samples,
                collectionSchemaService.findAll());
    }

    private boolean allowed(ExternalApi api) {
        return api.isAiAnalysis() && aiSettingsRepository.findByUser_Id(api.getOwner().getId()).map(AiSettings::getExternalApis)
                .orElse(AiPermissionLevel.SUGGEST).allowed();
    }

    private void store(ExternalApiEndpoint.ExternalApiEndpointId id, ApiMappingModelService.MappingDraft draft) {
        // Asked meanwhile, or the endpoint was removed: nothing to store.
        ExternalApiEndpoint endpoint = externalApiEndpointRepository.findById(id).orElse(null);
        if (endpoint == null || !externalApiMappingRepository.findByEndpoint_Id(id).isEmpty()) return;
        ExternalApiMapping mapping = externalApiMappingRepository.saveAndFlush(ExternalApiMapping.builder()
                .endpoint(endpoint)
                .collection(collectionRepository.getReferenceById(new Collection.CollectionId(draft.collectionId())))
                .titlePath(draft.titlePath())
                .fieldMap(new ArrayList<>(draft.fields()))
                .state(MappingState.PROPOSED)
                .build());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mappingId", mapping.getId().id());
        payload.put("endpointId", id.id());
        payload.put("collectionId", draft.collectionId());
        // For the proposal card; the applier ignores it.
        payload.put("title", endpoint.getApi().getName() + " · " + endpoint.getName());
        if (draft.titlePath() != null) payload.put("titlePath", draft.titlePath().value());
        payload.put("fields", draft.fields().stream().map(field -> Map.of("fieldSlug", field.fieldSlug(), "path", field.path())).toList());
        proposalService.submitDraft(new ProposalDraft(ProposalKind.MAP_API_ENDPOINT, payload, draft.confidence(), draft.reasoning()), null,
                AiPermissionLevel.SUGGEST, properties.models().extraction(), ApiMappingModelService.PROMPT_VERSION);
    }
}

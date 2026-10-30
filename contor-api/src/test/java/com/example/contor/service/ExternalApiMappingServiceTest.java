package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.MappingState;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.Slug;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.ExternalApiMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalApiMappingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final ExternalApiEndpoint.ExternalApiEndpointId ORDERS_ID = new ExternalApiEndpoint.ExternalApiEndpointId(30L);
    private static final List<CollectionDto> CATALOG = List.of(new CollectionDto().id(4L).slug("orders").name("Orders"));

    @Mock
    private ExternalApiEndpointRepository externalApiEndpointRepository;
    @Mock
    private ExternalApiMappingRepository externalApiMappingRepository;
    @Mock
    private ExternalApiItemRepository externalApiItemRepository;
    @Mock
    private CollectionSchemaService collectionSchemaService;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private ApiMappingModelService apiMappingModelService;
    @Mock
    private ProposalService proposalService;
    @Mock
    private AiModelService aiModelService;
    @Mock
    private AiSettingsRepository aiSettingsRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    /** A clock the test moves. */
    private final Instant[] now = {NOW};
    private final List<Runnable> queued = new ArrayList<>();
    private ExternalApiMappingService service;
    private ExternalApi shop;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 16384, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0];
            }
        };
        service = new ExternalApiMappingService(externalApiEndpointRepository, externalApiMappingRepository, externalApiItemRepository,
                collectionSchemaService, collectionRepository, apiMappingModelService, proposalService, aiModelService, aiSettingsRepository,
                new AiExecutors(queued::add, Runnable::run), properties, transactionManager, clock);
        shop = ExternalApi.builder().id(new ExternalApi.ExternalApiId(7L)).owner(AppUser.builder().id(OWNER).build()).name("Shop")
                .slug(new Slug("shop")).baseUrl(new BaseUrl("https://api.shop.example")).build();
        var orders = ExternalApiEndpoint.builder().id(ORDERS_ID).api(shop).name("Orders").path(new ApiPath("/orders")).description("Open orders").build();
        when(externalApiEndpointRepository.findById(ORDERS_ID)).thenReturn(Optional.of(orders));
        when(externalApiMappingRepository.findByEndpoint_Id(ORDERS_ID)).thenReturn(List.of());
        when(externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(ORDERS_ID)).thenReturn(List.of(
                ExternalApiItem.builder().id(9L).endpoint(orders).itemKey("SO-1").data(new HashMap<>(Map.of("number", "SO-1", "total", 3))).build()));
        when(collectionSchemaService.findAll()).thenReturn(CATALOG);
        when(aiModelService.isReady()).thenReturn(true);
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(AiSettings.builder().build()));
        when(collectionRepository.getReferenceById(new Collection.CollectionId(4L))).thenReturn(Collection.builder().id(new Collection.CollectionId(4L)).build());
        when(externalApiMappingRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ExternalApiMapping mapping = inv.getArgument(0);
            mapping.setId(new ExternalApiMapping.ExternalApiMappingId(40L));
            return mapping;
        });
    }

    private void modelProposes() {
        when(apiMappingModelService.propose(eq("Shop"), eq("Orders"), eq("Open orders"), anyList(), eq(CATALOG))).thenReturn(Optional.of(
                new ApiMappingModelService.MappingDraft(4L, new JsonPathExpr("$.number"), List.of(new ApiFieldMapping("total", "$.total")),
                        new BigDecimal("0.9"), "Shop orders")));
    }

    @Test
    void propose_StoresAProposedMappingAndAProposalToApproveIt() {
        modelProposes();

        service.propose(30L);

        ArgumentCaptor<ExternalApiMapping> mapping = ArgumentCaptor.forClass(ExternalApiMapping.class);
        verify(externalApiMappingRepository).saveAndFlush(mapping.capture());
        assertThat(mapping.getValue().getState()).isEqualTo(MappingState.PROPOSED);
        assertThat(mapping.getValue().getTitlePath().value()).isEqualTo("$.number");
        assertThat(mapping.getValue().getFieldMap()).containsExactly(new ApiFieldMapping("total", "$.total"));
        ArgumentCaptor<ProposalDraft> draft = ArgumentCaptor.forClass(ProposalDraft.class);
        verify(proposalService).submitDraft(draft.capture(), isNull(), eq(AiPermissionLevel.SUGGEST), eq("extractor"),
                eq(ApiMappingModelService.PROMPT_VERSION));
        assertThat(draft.getValue().kind()).isEqualTo(ProposalKind.MAP_API_ENDPOINT);
        assertThat(draft.getValue().payload()).isEqualTo(Map.of("mappingId", 40L, "endpointId", 30L, "collectionId", 4L,
                "title", "Shop · Orders", "titlePath", "$.number", "fields", List.of(Map.of("fieldSlug", "total", "path", "$.total"))));
        assertThat(draft.getValue().reasoning()).isEqualTo("Shop orders");
        assertThat(draft.getValue().confidence()).isEqualByComparingTo(new BigDecimal("0.9"));
    }

    @Test
    void propose_NothingWhileTheModelIsNotReadyOrTheEndpointHasAMapping() {
        when(aiModelService.isReady()).thenReturn(false);
        service.propose(30L);

        when(aiModelService.isReady()).thenReturn(true);
        when(externalApiMappingRepository.findByEndpoint_Id(ORDERS_ID)).thenReturn(List.of(ExternalApiMapping.builder().state(MappingState.OFF).build()));
        service.propose(30L);

        verifyNoInteractions(apiMappingModelService, proposalService);
    }

    @Test
    void propose_NothingForAppsTheAssistantMayNotLearnFrom() {
        shop.setAiAnalysis(false);
        service.propose(30L);

        shop.setAiAnalysis(true);
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(AiSettings.builder().externalApis(AiPermissionLevel.OFF).build()));
        service.propose(30L);

        verifyNoInteractions(apiMappingModelService, proposalService);
    }

    @Test
    void propose_WhenNothingFitsItAsksAgainOnlyAfterADay() {
        when(apiMappingModelService.propose(anyString(), anyString(), any(), anyList(), anyList())).thenReturn(Optional.empty());

        service.propose(30L);
        now[0] = NOW.plus(Duration.ofHours(23));
        service.propose(30L);
        now[0] = NOW.plus(Duration.ofHours(25));
        service.propose(30L);

        verify(apiMappingModelService, times(2)).propose(anyString(), anyString(), any(), anyList(), anyList());
        verify(externalApiMappingRepository, never()).saveAndFlush(any());
    }

    @Test
    void queue_RunsOneProposalPerEndpointAtATime() {
        modelProposes();

        service.queue(30L);
        service.queue(30L);

        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        service.queue(30L);
        assertThat(queued).hasSize(2);
    }
}

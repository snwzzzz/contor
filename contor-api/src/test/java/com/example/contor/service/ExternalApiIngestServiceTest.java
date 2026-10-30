package com.example.contor.service;

import com.example.contor.ai.ProposalDraft;
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
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.domain.Slug;
import com.example.contor.externalapi.JsonItems;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.ExternalApiMappingRepository;
import com.example.contor.persistence.ProposalRepository;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalApiIngestServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final ExternalApiEndpoint.ExternalApiEndpointId ORDERS_ID = new ExternalApiEndpoint.ExternalApiEndpointId(30L);

    @Mock
    private ExternalApiMappingRepository externalApiMappingRepository;
    @Mock
    private ExternalApiItemRepository externalApiItemRepository;
    @Mock
    private AiSettingsRepository aiSettingsRepository;
    @Mock
    private ProposalRepository proposalRepository;
    @Mock
    private ProposalService proposalService;
    @Mock
    private ExternalApiMappingService externalApiMappingService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ExternalApiIngestService service;
    private ExternalApi shop;
    private ExternalApiEndpoint orders;
    private ExternalApiMapping mapping;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ExternalApiIngestService(externalApiMappingRepository, externalApiItemRepository, aiSettingsRepository,
                proposalRepository, proposalService, externalApiMappingService, new JsonItems(JsonMapper.builder().build()), transactionManager);
        shop = ExternalApi.builder().id(new ExternalApi.ExternalApiId(7L)).owner(AppUser.builder().id(OWNER).build()).name("Shop")
                .slug(new Slug("shop")).baseUrl(new BaseUrl("https://api.shop.example")).build();
        orders = ExternalApiEndpoint.builder().id(ORDERS_ID).api(shop).name("Orders").path(new ApiPath("/orders")).build();
        mapping = ExternalApiMapping.builder().id(new ExternalApiMapping.ExternalApiMappingId(40L)).endpoint(orders)
                .collection(Collection.builder().id(new Collection.CollectionId(4L)).build())
                .titlePath(new JsonPathExpr("$.number"))
                .fieldMap(new ArrayList<>(List.of(new ApiFieldMapping("total", "$.total"), new ApiFieldMapping("customer", "$.customer.name"))))
                .state(MappingState.ACTIVE).build();
        when(externalApiMappingRepository.findByEndpoint_Id(ORDERS_ID)).thenReturn(List.of(mapping));
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(AiSettings.builder().build()));
        when(proposalRepository.findFirstBySourceApiItem_IdAndStatus(any(), eq(ProposalStatus.PENDING))).thenReturn(Optional.empty());
    }

    private ExternalApiItem item(long id, Map<String, Object> data) {
        var item = ExternalApiItem.builder().id(id).endpoint(orders).itemKey("k" + id).itemHash("h").data(new HashMap<>(data)).build();
        when(externalApiItemRepository.findAllById(List.of(id))).thenReturn(List.of(item));
        return item;
    }

    private ProposalDraft submitted(AiPermissionLevel level) {
        ArgumentCaptor<ProposalDraft> draft = ArgumentCaptor.forClass(ProposalDraft.class);
        verify(proposalService).submitDraftFromApiItem(draft.capture(), any(), eq(level));
        return draft.getValue();
    }

    @Test
    void afterPoll_NewItemsBecomeRecordProposals() {
        var item = item(9L, Map.of("number", "SO-1", "total", 3, "customer", Map.of("name", "Anna")));

        service.afterPoll(30L, List.of(9L));

        var draft = submitted(AiPermissionLevel.SUGGEST);
        assertThat(draft.kind()).isEqualTo(ProposalKind.CREATE_RECORD);
        assertThat(draft.payload()).isEqualTo(Map.of("collectionId", 4L, "title", "SO-1", "values", Map.of("total", 3, "customer", "Anna")));
        assertThat(draft.reasoning()).isEqualTo("From Shop · Orders");
        verify(proposalService).submitDraftFromApiItem(any(), eq(item), eq(AiPermissionLevel.SUGGEST));
    }

    @Test
    void afterPoll_ChangedItemsWithARecordBecomeUpdates() {
        var item = item(9L, Map.of("number", "SO-1", "total", 5));
        item.setCollection(mapping.getCollection());
        item.setRecordId(12L);

        service.afterPoll(30L, List.of(9L));

        var draft = submitted(AiPermissionLevel.SUGGEST);
        assertThat(draft.kind()).isEqualTo(ProposalKind.UPDATE_RECORD);
        assertThat(draft.payload()).isEqualTo(Map.of("collectionId", 4L, "recordId", 12L, "title", "SO-1", "values", Map.of("total", 5)));
    }

    @Test
    void afterPoll_APendingProposalOfTheItemFollowsItsChanges() {
        item(9L, Map.of("number", "SO-1", "total", 7));
        var pending = Proposal.builder().kind(ProposalKind.CREATE_RECORD).payload(new HashMap<>(Map.of("collectionId", 4L, "title", "SO-1",
                "values", Map.of("total", 3)))).build();
        when(proposalRepository.findFirstBySourceApiItem_IdAndStatus(9L, ProposalStatus.PENDING)).thenReturn(Optional.of(pending));

        service.afterPoll(30L, List.of(9L));

        assertThat(pending.getPayload()).containsEntry("values", Map.of("total", 7));
        verify(proposalRepository).saveAndFlush(pending);
        verify(proposalService, never()).submitDraftFromApiItem(any(), any(), any());
    }

    @Test
    void afterPoll_AutoFallsBackToASuggestionWhenApplyingFails() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(AiSettings.builder().externalApis(AiPermissionLevel.AUTO).build()));
        item(9L, Map.of("number", "SO-1", "total", "not a number"));
        when(proposalService.submitDraftFromApiItem(any(), any(), eq(AiPermissionLevel.AUTO))).thenThrow(new IllegalArgumentException("total: not a number"));

        service.afterPoll(30L, List.of(9L));

        verify(proposalService).submitDraftFromApiItem(any(), any(), eq(AiPermissionLevel.SUGGEST));
    }

    @Test
    void afterPoll_OffOrNoLearningProposesNothing() {
        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(AiSettings.builder().externalApis(AiPermissionLevel.OFF).build()));
        item(9L, Map.of("number", "SO-1"));
        service.afterPoll(30L, List.of(9L));

        when(aiSettingsRepository.findByUser_Id(OWNER)).thenReturn(Optional.of(AiSettings.builder().build()));
        shop.setAiAnalysis(false);
        service.afterPoll(30L, List.of(9L));

        verifyNoInteractions(proposalService, externalApiMappingService);
    }

    @Test
    void afterPoll_ItemsWithoutAnyMappedValueAreSkipped() {
        item(9L, Map.of("other", "x"));

        service.afterPoll(30L, List.of(9L));

        verify(proposalService, never()).submitDraftFromApiItem(any(), any(), any());
    }

    @Test
    void afterPoll_WithoutAnyMappingAsksTheAssistantForOne() {
        when(externalApiMappingRepository.findByEndpoint_Id(ORDERS_ID)).thenReturn(List.of());
        when(externalApiItemRepository.findAllById(anyList())).thenReturn(List.of(ExternalApiItem.builder().id(9L).endpoint(orders).build()));

        service.afterPoll(30L, List.of(9L));

        verify(externalApiMappingService).queue(30L);
        verifyNoInteractions(proposalService);
    }

    @Test
    void afterPoll_AProposedOrSwitchedOffMappingWaits() {
        mapping.setState(MappingState.PROPOSED);
        item(9L, Map.of("number", "SO-1"));

        service.afterPoll(30L, List.of(9L));

        verifyNoInteractions(proposalService, externalApiMappingService);
    }

    @Test
    void ingestCurrent_ProposesRecordsForTheItemsThatHaveNoneYet() {
        var fresh = ExternalApiItem.builder().id(9L).endpoint(orders).itemKey("k9").data(new HashMap<>(Map.of("number", "SO-1"))).build();
        var mapped = ExternalApiItem.builder().id(10L).endpoint(orders).itemKey("k10").data(new HashMap<>(Map.of("number", "SO-2")))
                .recordId(3L).collection(mapping.getCollection()).build();
        when(externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(ORDERS_ID)).thenReturn(List.of(fresh, mapped));

        service.ingestCurrent(30L);

        verify(proposalService).submitDraftFromApiItem(any(), eq(fresh), eq(AiPermissionLevel.SUGGEST));
        verify(proposalService, never()).submitDraftFromApiItem(any(), eq(mapped), any());
    }
}

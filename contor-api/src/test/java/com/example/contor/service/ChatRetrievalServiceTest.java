package com.example.contor.service;

import com.example.contor.ai.ChatPlan;
import com.example.contor.ai.EvidencePack;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.Slug;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Arrays;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatRetrievalServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private RecordService recordService;
    @Mock
    private ReportScopeService reportScopeService;
    @Mock
    private AiSettingsService aiSettingsService;
    @Mock
    private ExternalApiItemRepository externalApiItemRepository;

    private ChatRetrievalService chatRetrievalService;

    @BeforeEach
    void setUp() {
        chatRetrievalService = new ChatRetrievalService(collectionRepository, recordService, reportScopeService, aiSettingsService,
                externalApiItemRepository, JsonMapper.builder().build());
        Collection people = collection(3L, "people", "People");
        Collection customers = collection(4L, "customers", "Customers");
        Collection archived = collection(5L, "old", "Old");
        archived.setArchivedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(collectionRepository.findAll()).thenReturn(List.of(people, customers, archived));
        when(recordService.listRecords(anyLong(), isNull(), any(), isNull(), eq(0), anyInt())).thenReturn(page());
    }

    @Test
    void catalog_NamesTheActiveCollections() {
        assertThat(chatRetrievalService.collectionSlugs()).containsExactly("people", "customers");
        assertThat(chatRetrievalService.catalog()).contains("people: People", "customers: Customers").doesNotContain("old");
    }

    @Test
    void findRecords_SearchesEveryCollectionAndRanksExactTitlesFirst() {
        when(recordService.listRecords(eq(3L), isNull(), eq("Kessler"), isNull(), eq(0), anyInt()))
                .thenReturn(page(record(10L, "Anna Kessler"), record(11L, "Kessler")));
        when(recordService.listRecords(eq(4L), isNull(), eq("Kessler"), isNull(), eq(0), anyInt()))
                .thenReturn(page(record(20L, "Kessler Maschinenbau")));

        var found = chatRetrievalService.findRecords(new ChatPlan(List.of("Kessler"), List.of()), List.of());

        assertThat(found).containsExactly(new RecordRef(3L, 11L), new RecordRef(3L, 10L), new RecordRef(4L, 20L));
        verify(recordService, never()).listRecords(eq(5L), any(), any(), any(), any(), any());
    }

    @Test
    void findRecords_PutsTheFocusFirstAndDropsDuplicates() {
        when(recordService.listRecords(eq(3L), isNull(), eq("Anna"), isNull(), eq(0), anyInt())).thenReturn(page(record(10L, "Anna Kessler")));

        var found = chatRetrievalService.findRecords(new ChatPlan(List.of("Anna"), List.of()),
                List.of(new RecordRef(4L, 20L), new RecordRef(3L, 10L)));

        assertThat(found).containsExactly(new RecordRef(4L, 20L), new RecordRef(3L, 10L));
    }

    @Test
    void findRecords_AddsTheNewestRecordsOfThePlannedCollections() {
        when(recordService.listRecords(eq(4L), isNull(), isNull(), isNull(), eq(0), anyInt()))
                .thenReturn(page(record(21L, "Dijkstra"), record(20L, "Kessler Maschinenbau")));

        var found = chatRetrievalService.findRecords(new ChatPlan(List.of(), List.of("customers")), List.of());

        assertThat(found).containsExactly(new RecordRef(4L, 21L), new RecordRef(4L, 20L));
    }

    @Test
    void findRecords_KeepsAtMostTheScopeLimit() {
        RecordDto[] many = java.util.stream.LongStream.rangeClosed(1, 40).mapToObj(id -> record(id, "Customer " + id)).toArray(RecordDto[]::new);
        when(recordService.listRecords(eq(4L), isNull(), isNull(), isNull(), eq(0), anyInt())).thenReturn(page(many));

        assertThat(chatRetrievalService.findRecords(new ChatPlan(List.of(), List.of("customers")), List.of()))
                .hasSize(ChatRetrievalService.MAX_RECORDS);
    }

    @Test
    void collect_UsesTheMailOnlyWithMailAccessAndHiddenMailboxesOnlyWhenAllowed() {
        var records = List.of(new RecordRef(3L, 10L));
        EvidencePack pack = mock(EvidencePack.class);
        when(reportScopeService.collectQuestion(eq(records), eq("Who is Anna?"), eq(List.of("Anna")), eq(OWNER), eq(true), eq(true), eq(List.of())))
                .thenReturn(pack);
        AiSettings settings = new AiSettings();
        settings.setMailAccess(true);
        settings.setReadHiddenMailboxes(true);
        when(aiSettingsService.settingsFor(OWNER)).thenReturn(settings);

        assertThat(chatRetrievalService.collect(records, List.of("Anna"), "Who is Anna?", OWNER)).isSameAs(pack);

        settings.setMailAccess(false);
        settings.setReadHiddenMailboxes(false);
        chatRetrievalService.collect(records, List.of("Anna"), "Who is Anna?", OWNER);
        verify(reportScopeService).collectQuestion(records, "Who is Anna?", List.of("Anna"), OWNER, false, false, List.of());
    }

    @Test
    void mailAccess_FollowsTheSettings() {
        AiSettings settings = new AiSettings();
        when(aiSettingsService.settingsFor(OWNER)).thenReturn(settings);

        assertThat(chatRetrievalService.mailAccess(OWNER)).isFalse();
        settings.setMailAccess(true);
        assertThat(chatRetrievalService.mailAccess(OWNER)).isTrue();
        assertThat(chatRetrievalService.mailAccess(null)).isFalse();
    }

    private static Collection collection(Long id, String slug, String name) {
        return Collection.builder().id(new Collection.CollectionId(id)).slug(new Slug(slug)).name(name).build();
    }

    private static RecordDto record(Long id, String title) {
        return new RecordDto().id(id).title(title).values(Map.of());
    }

    private static RecordPageDto page(RecordDto... records) {
        return new RecordPageDto(Arrays.asList(records), 0, records.length, (long) records.length, 1);
    }

    @Test
    void collect_AddsTheItemsOfTheUsersAppsThatMatchTheSearches() {
        AiSettings settings = new AiSettings();
        when(aiSettingsService.settingsFor(OWNER)).thenReturn(settings);
        var api = ExternalApi.builder().id(new ExternalApi.ExternalApiId(7L)).name("Shop").build();
        var endpoint = ExternalApiEndpoint.builder().api(api).name("Orders").build();
        var order = ExternalApiItem.builder().id(9L).endpoint(endpoint).itemKey("SO-155").data(new HashMap<>(Map.of("state", "shipped")))
                .lastSeenAt(Instant.parse("2026-10-28T12:00:00Z")).build();
        when(externalApiItemRepository.searchForChat(1L, "%so-155%", ChatRetrievalService.APP_ITEMS_PER_SEARCH)).thenReturn(List.of(order));
        when(externalApiItemRepository.searchForChat(1L, "%100\\%\\_off%", ChatRetrievalService.APP_ITEMS_PER_SEARCH)).thenReturn(List.of(order));

        chatRetrievalService.collect(List.of(), List.of("SO-155", "100%_off"), "Where is SO-155?", OWNER);

        verify(reportScopeService).collectQuestion(List.of(), "Where is SO-155?", List.of("SO-155", "100%_off"), OWNER, false, true,
                List.of(new EvidencePack.AppItemInput(9L, 7L, "Shop", "Orders", "SO-155", "{\"state\":\"shipped\"}", LocalDate.of(2026, 10, 28))));
    }
}

package com.example.contor.service;

import com.example.contor.config.ExternalApiProperties;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.ApiStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.CallOutcome;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiCall;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ExternalApiSnapshot;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.Slug;
import com.example.contor.externalapi.ApiAuthException;
import com.example.contor.externalapi.ApiResponse;
import com.example.contor.externalapi.ExternalApiClient;
import com.example.contor.externalapi.JsonItems;
import com.example.contor.persistence.ExternalApiCallRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.persistence.ExternalApiSnapshotRepository;
import com.example.contor.security.OutboundRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalApiPollerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final ExternalApi.ExternalApiId SHOP_ID = new ExternalApi.ExternalApiId(7L);
    private static final ExternalApiEndpoint.ExternalApiEndpointId ORDERS_ID = new ExternalApiEndpoint.ExternalApiEndpointId(30L);
    private static final String TWO_ORDERS = "{\"data\":[{\"id\":1,\"total\":3},{\"id\":2,\"total\":4}]}";

    @Mock
    private ExternalApiRepository externalApiRepository;
    @Mock
    private ExternalApiEndpointRepository externalApiEndpointRepository;
    @Mock
    private ExternalApiCallRepository externalApiCallRepository;
    @Mock
    private ExternalApiSnapshotRepository externalApiSnapshotRepository;
    @Mock
    private ExternalApiItemRepository externalApiItemRepository;
    @Mock
    private ExternalApiClient externalApiClient;

    private ExternalApiPoller poller;
    private ExternalApi shop;
    private ExternalApiEndpoint orders;

    @BeforeEach
    void setUp() {
        var properties = new ExternalApiProperties(true, Duration.ofMinutes(1), Duration.ofSeconds(10), DataSize.ofMegabytes(1), 4, 30, 5);
        poller = new ExternalApiPoller(externalApiRepository, externalApiEndpointRepository, externalApiCallRepository,
                externalApiSnapshotRepository, externalApiItemRepository, externalApiClient,
                new JsonItems(JsonMapper.builder().build()), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        shop = ExternalApi.builder().id(SHOP_ID).owner(AppUser.builder().build()).name("Shop").slug(new Slug("shop"))
                .baseUrl(new BaseUrl("https://api.shop.example/v1")).pollIntervalMinutes(60).nextCallAt(NOW).build();
        orders = ExternalApiEndpoint.builder().id(ORDERS_ID).api(shop).name("Orders").path(new ApiPath("/orders"))
                .itemsPath(new JsonPathExpr("$.data[*]")).itemKeyPath(new JsonPathExpr("$.id")).build();
        when(externalApiRepository.findById(SHOP_ID)).thenReturn(Optional.of(shop));
        when(externalApiEndpointRepository.findByApi_IdAndEnabledTrue(SHOP_ID)).thenReturn(List.of(orders));
        when(externalApiSnapshotRepository.findFirstByEndpoint_IdOrderByFetchedAtDesc(ORDERS_ID)).thenReturn(Optional.empty());
        when(externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(ORDERS_ID)).thenReturn(List.of());
        when(externalApiItemRepository.findByEndpoint_IdAndItemKey(any(), any())).thenReturn(Optional.empty());
        when(externalApiCallRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(externalApiSnapshotRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(externalApiItemRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(externalApiRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void answer(int status, String body) {
        when(externalApiClient.call(shop, orders)).thenReturn(new ApiResponse(status, "application/json", body, false, null));
    }

    private ExternalApiCall recordedCall() {
        ArgumentCaptor<ExternalApiCall> call = ArgumentCaptor.forClass(ExternalApiCall.class);
        verify(externalApiCallRepository).saveAndFlush(call.capture());
        return call.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<ExternalApiItem> savedItems() {
        ArgumentCaptor<List<ExternalApiItem>> items = ArgumentCaptor.forClass(List.class);
        verify(externalApiItemRepository).saveAllAndFlush(items.capture());
        return items.getValue();
    }

    private ExternalApiItem item(String key, String data) {
        var parsed = new JsonItems(JsonMapper.builder().build())
                .extract("[" + data + "]", new JsonPathExpr("$[*]"), new JsonPathExpr("$.id")).orElseThrow().getFirst();
        return ExternalApiItem.builder().endpoint(orders).itemKey(key).itemHash(parsed.hash()).data(new HashMap<>(parsed.data()))
                .firstSeenAt(NOW.minus(Duration.ofDays(1))).lastSeenAt(NOW.minus(Duration.ofHours(1))).build();
    }

    @Test
    void findDue_EnabledPolledAppsWhoseCallIsDueWithTheirHost() {
        when(externalApiRepository.findByEnabledTrueAndStatusInAndNextCallAtLessThanEqual(ApiStatus.POLLED, NOW)).thenReturn(List.of(shop));

        assertThat(poller.findDue()).containsExactly(new ExternalApiPoller.DueApi(7L, "api.shop.example"));
    }

    @Test
    void poll_NewAnswerStoresCallSnapshotAndItems() {
        answer(200, TWO_ORDERS);

        poller.poll(7L);

        var call = recordedCall();
        assertThat(call.getOutcome()).isEqualTo(CallOutcome.OK);
        assertThat(call.getHttpStatus()).isEqualTo(200);
        assertThat(call.getStartedAt()).isEqualTo(NOW);
        assertThat(call.getItemsNew()).isEqualTo(2);
        ArgumentCaptor<ExternalApiSnapshot> snapshot = ArgumentCaptor.forClass(ExternalApiSnapshot.class);
        verify(externalApiSnapshotRepository).saveAndFlush(snapshot.capture());
        assertThat(snapshot.getValue().getBody()).isEqualTo(TWO_ORDERS);
        assertThat(snapshot.getValue().getContentHash()).isEqualTo(JsonItems.hash(TWO_ORDERS));
        assertThat(savedItems()).extracting(ExternalApiItem::getItemKey).containsExactly("1", "2");
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.ACTIVE);
        assertThat(shop.getLastCalledAt()).isEqualTo(NOW);
        assertThat(shop.getLastError()).isNull();
        assertThat(shop.getNextCallAt()).isBetween(NOW.plus(Duration.ofMinutes(54)), NOW.plus(Duration.ofMinutes(66)));
    }

    @Test
    void poll_UnchangedAnswerOnlyRecordsTheCall() {
        answer(200, TWO_ORDERS);
        when(externalApiSnapshotRepository.findFirstByEndpoint_IdOrderByFetchedAtDesc(ORDERS_ID))
                .thenReturn(Optional.of(ExternalApiSnapshot.builder().contentHash(JsonItems.hash(TWO_ORDERS)).build()));

        poller.poll(7L);

        assertThat(recordedCall().getOutcome()).isEqualTo(CallOutcome.UNCHANGED);
        verify(externalApiSnapshotRepository, never()).saveAndFlush(any());
        verify(externalApiItemRepository, never()).saveAllAndFlush(anyList());
        assertThat(shop.getLastCalledAt()).isEqualTo(NOW);
    }

    @Test
    void poll_CountsNewChangedAndGoneItems() {
        var one = item("1", "{\"id\":1,\"total\":1}");
        var two = item("2", "{\"id\":2,\"total\":4}");
        var three = item("3", "{\"id\":3}");
        when(externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(ORDERS_ID)).thenReturn(List.of(one, two, three));
        answer(200, "{\"data\":[{\"id\":1,\"total\":3},{\"id\":2,\"total\":4},{\"id\":4}]}");

        poller.poll(7L);

        var call = recordedCall();
        assertThat(List.of(call.getItemsNew(), call.getItemsChanged(), call.getItemsGone())).containsExactly(1, 1, 1);
        assertThat(one.getData()).containsEntry("total", 3);
        assertThat(one.getLastSeenAt()).isEqualTo(NOW);
        assertThat(two.getLastSeenAt()).isEqualTo(NOW);
        assertThat(three.getGoneAt()).isEqualTo(NOW);
        assertThat(savedItems()).extracting(ExternalApiItem::getItemKey).containsExactlyInAnyOrder("1", "2", "3", "4");
    }

    @Test
    void poll_ItemThatComesBackCountsAsNew() {
        var back = item("1", "{\"id\":1,\"total\":3}");
        back.setGoneAt(NOW.minus(Duration.ofDays(2)));
        when(externalApiItemRepository.findByEndpoint_IdAndItemKey(ORDERS_ID, "1")).thenReturn(Optional.of(back));
        answer(200, "{\"data\":[{\"id\":1,\"total\":3}]}");

        poller.poll(7L);

        assertThat(recordedCall().getItemsNew()).isEqualTo(1);
        assertThat(back.getGoneAt()).isNull();
        assertThat(back.getFirstSeenAt()).isEqualTo(NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void poll_KeepsOnlyTheLastSnapshots() {
        answer(200, TWO_ORDERS);
        var kept = new ArrayList<ExternalApiSnapshot>();
        for (int i = 0; i < 7; i++) kept.add(ExternalApiSnapshot.builder().id((long) i).build());
        when(externalApiSnapshotRepository.findByEndpoint_IdOrderByFetchedAtDesc(ORDERS_ID)).thenReturn(kept);

        poller.poll(7L);

        verify(externalApiSnapshotRepository).deleteAll(kept.subList(5, 7));
    }

    @Test
    void poll_TruncatedAnswerKeepsTheSnapshotButReadsNoItems() {
        when(externalApiClient.call(shop, orders)).thenReturn(new ApiResponse(200, "application/json", "{\"data\":[", true, null));

        poller.poll(7L);

        var call = recordedCall();
        assertThat(call.getOutcome()).isEqualTo(CallOutcome.OK);
        assertThat(call.getError()).isEqualTo("The answer is larger than 1 MB, so no items were read");
        verify(externalApiItemRepository, never()).saveAllAndFlush(anyList());
        verify(externalApiSnapshotRepository).saveAndFlush(any());
    }

    @Test
    void poll_AnswerThatIsNoJsonKeepsTheItems() {
        answer(200, "<html>maintenance</html>");

        poller.poll(7L);

        assertThat(recordedCall().getError()).isEqualTo("The answer is no JSON, so no items were read");
        verify(externalApiItemRepository, never()).saveAllAndFlush(anyList());
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.ACTIVE);
    }

    @Test
    void poll_RejectedCredentialsNeedSetupAndStopTheRun() {
        var customers = ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(31L)).api(shop).name("Customers")
                .path(new ApiPath("/customers")).build();
        when(externalApiEndpointRepository.findByApi_IdAndEnabledTrue(SHOP_ID)).thenReturn(List.of(orders, customers));
        answer(401, "{\"error\":\"bad key\"}");

        poller.poll(7L);

        var call = recordedCall();
        assertThat(call.getOutcome()).isEqualTo(CallOutcome.FAILED);
        assertThat(call.getHttpStatus()).isEqualTo(401);
        assertThat(call.getError()).isEqualTo("The app refused the credentials (HTTP 401)");
        verify(externalApiClient, never()).call(shop, customers);
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.NEEDS_SETUP);
        assertThat(shop.getNextCallAt()).isNull();
        assertThat(shop.getLastError()).isEqualTo("The app refused the credentials (HTTP 401)");
    }

    @Test
    void poll_RejectedTokenRequestNeedsSetup() {
        when(externalApiClient.call(shop, orders)).thenThrow(new ApiAuthException("auth.shop.example refused the client credentials", true));

        poller.poll(7L);

        assertThat(recordedCall().getHttpStatus()).isNull();
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.NEEDS_SETUP);
        assertThat(shop.getLastError()).isEqualTo("auth.shop.example refused the client credentials");
    }

    @Test
    void poll_MissingCredentialsNeedSetup() {
        when(externalApiClient.call(shop, orders)).thenThrow(new IllegalArgumentException("The API key is missing"));

        poller.poll(7L);

        assertThat(shop.getStatus()).isEqualTo(ApiStatus.NEEDS_SETUP);
        assertThat(shop.getLastError()).isEqualTo("The API key is missing");
    }

    @Test
    void poll_FailureBacksOffExponentially() {
        shop.setFailureCount(1);
        answer(500, "oops");

        poller.poll(7L);

        assertThat(recordedCall().getError()).isEqualTo("The app answered with HTTP 500");
        assertThat(shop.getFailureCount()).isEqualTo(2);
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.ACTIVE);
        assertThat(shop.getNextCallAt()).isEqualTo(NOW.plus(Duration.ofHours(4)));
    }

    @Test
    void poll_ThirdFailureMarksTheAppFailingAndTheBackoffIsCapped() {
        shop.setFailureCount(2);
        when(externalApiClient.call(shop, orders)).thenThrow(new OutboundRequestException("api.shop.example did not answer in time"));

        poller.poll(7L);

        var call = recordedCall();
        assertThat(call.getHttpStatus()).isNull();
        assertThat(call.getError()).isEqualTo("api.shop.example did not answer in time");
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.FAILING);
        assertThat(shop.getNextCallAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));

        shop.setFailureCount(6);
        poller.poll(7L);
        assertThat(shop.getNextCallAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void poll_TenthFailurePausesTheApp() {
        shop.setFailureCount(9);
        shop.setStatus(ApiStatus.FAILING);
        answer(503, "");

        poller.poll(7L);

        assertThat(shop.getStatus()).isEqualTo(ApiStatus.PAUSED);
        assertThat(shop.getNextCallAt()).isNull();
    }

    @Test
    void poll_RateLimitWaitsAsAskedWithoutCountingAFailure() {
        shop.setFailureCount(1);
        when(externalApiClient.call(shop, orders)).thenReturn(new ApiResponse(429, null, "", false, Duration.ofHours(2)));

        poller.poll(7L);

        assertThat(recordedCall().getError()).isEqualTo("Too many requests (HTTP 429)");
        assertThat(shop.getFailureCount()).isEqualTo(1);
        assertThat(shop.getNextCallAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void poll_SuccessClearsAnEarlierFailure() {
        shop.setFailureCount(4);
        shop.setStatus(ApiStatus.FAILING);
        shop.setLastError("The app answered with HTTP 500");
        answer(200, TWO_ORDERS);

        poller.poll(7L);

        assertThat(shop.getStatus()).isEqualTo(ApiStatus.ACTIVE);
        assertThat(shop.getFailureCount()).isZero();
        assertThat(shop.getLastError()).isNull();
    }

    @Test
    void poll_DailyLimitReachedCallsNothing() {
        shop.setDailyRequestLimit(10);
        when(externalApiCallRepository.countByEndpoint_Api_IdAndStartedAtGreaterThanEqual(SHOP_ID, NOW.minus(Duration.ofDays(1))))
                .thenReturn(10L);

        poller.poll(7L);

        verify(externalApiClient, never()).call(any(), any());
        assertThat(shop.getNextCallAt()).isBetween(NOW.plus(Duration.ofMinutes(54)), NOW.plus(Duration.ofMinutes(66)));
    }

    @Test
    void poll_AppThatIsNoLongerDueIsLeftAlone() {
        shop.setStatus(ApiStatus.PAUSED);

        poller.poll(7L);

        verify(externalApiClient, never()).call(any(), any());
        verify(externalApiRepository, never()).saveAndFlush(any());
    }

    @Test
    void poll_WithoutEnabledEndpointsOnlySchedulesTheNextCall() {
        when(externalApiEndpointRepository.findByApi_IdAndEnabledTrue(SHOP_ID)).thenReturn(List.of());

        poller.poll(7L);

        verify(externalApiClient, never()).call(any(), any());
        assertThat(shop.getLastCalledAt()).isNull();
        assertThat(shop.getNextCallAt()).isAfter(NOW);
    }

    @Test
    void poll_DeletedAppIsSkipped() {
        when(externalApiRepository.findById(SHOP_ID)).thenReturn(Optional.empty());

        poller.poll(7L);

        verify(externalApiClient, never()).call(any(), any());
    }

    @Test
    void pruneHistory_RemovesCallsOlderThanTheHistory() {
        when(externalApiCallRepository.deleteStartedBefore(NOW.minus(Duration.ofDays(30)))).thenReturn(3);

        assertThat(poller.pruneHistory()).isEqualTo(3);
    }

    @Test
    void poll_StoredItemDataIsAMap() {
        answer(200, "{\"data\":[{\"id\":1,\"tags\":[\"a\"]}]}");

        poller.poll(7L);

        assertThat(savedItems().getFirst().getData()).isEqualTo(Map.of("id", 1, "tags", List.of("a")));
    }
}

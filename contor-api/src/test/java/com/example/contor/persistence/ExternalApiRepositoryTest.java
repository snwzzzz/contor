package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiHttpMethod;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.ApiStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.CallOutcome;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EndpointSource;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiCall;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.ExternalApiSnapshot;
import com.example.contor.domain.HttpsUrl;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.MappingState;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class ExternalApiRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Autowired
    private ExternalApiRepository externalApiRepository;

    @Autowired
    private ExternalApiEndpointRepository externalApiEndpointRepository;

    @Autowired
    private ExternalApiMappingRepository externalApiMappingRepository;

    @Autowired
    private ExternalApiCallRepository externalApiCallRepository;

    @Autowired
    private ExternalApiSnapshotRepository externalApiSnapshotRepository;

    @Autowired
    private ExternalApiItemRepository externalApiItemRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AppUser owner;
    private AppUser other;

    @BeforeEach
    void setUp() {
        owner = appUserRepository.save(Fixtures.user("owner"));
        other = appUserRepository.save(Fixtures.user("other"));
    }

    @Test
    void a_new_app_gets_the_defaults() {
        var saved = externalApiRepository.saveAndFlush(Fixtures.externalApi(owner, "shop"));
        entityManager.clear();

        var reloaded = externalApiRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getAuthKind()).isEqualTo(ApiAuthKind.NONE);
        assertThat(reloaded.getAuthConfig()).isEqualTo(ApiAuthConfig.NONE);
        assertThat(reloaded.getHeaders()).isEmpty();
        assertThat(reloaded.getSecret()).isNull();
        assertThat(reloaded.getPollIntervalMinutes()).isEqualTo(360);
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.isAiAnalysis()).isTrue();
        assertThat(reloaded.getStatus()).isEqualTo(ApiStatus.ACTIVE);
        assertThat(reloaded.getFailureCount()).isZero();
        assertThat(reloaded.getDailyRequestLimit()).isEqualTo(200);
        assertThat(reloaded.getNextCallAt()).isNull();
    }

    @Test
    void stores_auth_headers_and_urls_and_encrypts_the_secret() {
        var api = Fixtures.externalApi(owner, "shop");
        api.setDescription("Orders of the web shop");
        api.setDocsUrl(new HttpsUrl("https://docs.shop.example/api"));
        api.setAuthKind(ApiAuthKind.API_KEY_HEADER);
        api.setAuthConfig(new ApiAuthConfig("X-Api-Key", null, null, null, null, null));
        api.setSecret("{\"apiKey\":\"key-plain\"}");
        api.setHeaders(List.of(new ApiHeader("Accept", "application/json"), new ApiHeader("X-Shop-Version", "2024-10")));
        var saved = externalApiRepository.saveAndFlush(api);
        entityManager.clear();

        String stored = jdbcTemplate.queryForObject("select secret_enc from \"external_api\" where id = ?", String.class, saved.getId().id());
        assertThat(stored).startsWith("v1:").doesNotContain("key-plain");
        var reloaded = externalApiRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSecret()).isEqualTo("{\"apiKey\":\"key-plain\"}");
        assertThat(reloaded.getAuthConfig().headerName()).isEqualTo("X-Api-Key");
        assertThat(reloaded.getHeaders()).containsExactly(new ApiHeader("Accept", "application/json"), new ApiHeader("X-Shop-Version", "2024-10"));
        assertThat(reloaded.getBaseUrl().value()).isEqualTo("https://api.shop.example/v1");
        assertThat(reloaded.getDocsUrl().value()).isEqualTo("https://docs.shop.example/api");
    }

    @Test
    void finds_apps_only_for_their_owner() {
        var shop = externalApiRepository.save(Fixtures.externalApi(owner, "shop"));
        externalApiRepository.save(Fixtures.externalApi(owner, "jobs"));
        externalApiRepository.save(Fixtures.externalApi(other, "shop"));
        externalApiRepository.flush();

        assertThat(externalApiRepository.findByOwner_IdOrderByNameAsc(owner.getId())).extracting(ExternalApi::getName)
                .containsExactly("jobs", "shop");
        assertThat(externalApiRepository.findByIdAndOwner_Id(shop.getId(), owner.getId())).isPresent();
        assertThat(externalApiRepository.findByIdAndOwner_Id(shop.getId(), other.getId())).isEmpty();
        assertThat(externalApiRepository.existsByOwner_IdAndSlug(owner.getId(), new Slug("jobs"))).isTrue();
        assertThat(externalApiRepository.existsByOwner_IdAndSlug(other.getId(), new Slug("jobs"))).isFalse();
    }

    @Test
    void finds_enabled_healthy_apps_that_are_due() {
        var due = app("due", api -> api.setNextCallAt(NOW.minusSeconds(60)));
        var failing = app("failing", api -> {
            api.setNextCallAt(NOW);
            api.setStatus(ApiStatus.FAILING);
        });
        app("later", api -> api.setNextCallAt(NOW.plusSeconds(60)));
        app("never", api -> api.setNextCallAt(null));
        app("disabled", api -> {
            api.setNextCallAt(NOW.minusSeconds(60));
            api.setEnabled(false);
        });
        app("paused", api -> {
            api.setNextCallAt(NOW.minusSeconds(60));
            api.setStatus(ApiStatus.PAUSED);
        });
        app("setup", api -> {
            api.setNextCallAt(NOW.minusSeconds(60));
            api.setStatus(ApiStatus.NEEDS_SETUP);
        });

        assertThat(externalApiRepository.findByEnabledTrueAndStatusInAndNextCallAtLessThanEqual(ApiStatus.POLLED, NOW))
                .extracting(ExternalApi::getId).containsExactlyInAnyOrder(due.getId(), failing.getId());
    }

    @Test
    void slugs_are_unique_per_owner() {
        externalApiRepository.saveAndFlush(Fixtures.externalApi(owner, "shop"));

        assertThatThrownBy(() -> externalApiRepository.saveAndFlush(Fixtures.externalApi(owner, "shop")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_external_api_owner_slug");
    }

    @Test
    void the_interval_is_at_least_15_minutes() {
        var api = Fixtures.externalApi(owner, "shop");
        api.setPollIntervalMinutes(5);

        assertThatThrownBy(() -> externalApiRepository.saveAndFlush(api))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_external_api_poll_interval");
    }

    @Test
    void stores_the_endpoints_of_an_app() {
        var api = externalApiRepository.save(Fixtures.externalApi(owner, "shop"));
        var orders = Fixtures.endpoint(api, "orders");
        orders.setQuery(Map.of("status", "open", "limit", "50"));
        orders.setItemsPath(new JsonPathExpr("$.data.orders[*]"));
        orders.setItemKeyPath(new JsonPathExpr("$.id"));
        orders.setDescription("Open orders");
        orders.setSource(EndpointSource.DOCS);
        var search = Fixtures.endpoint(api, "search");
        search.setMethod(ApiHttpMethod.POST);
        search.setBody(Map.of("query", "{ orders { id } }"));
        var disabled = Fixtures.endpoint(api, "archive");
        disabled.setEnabled(false);
        externalApiEndpointRepository.saveAll(List.of(orders, search, disabled));
        externalApiEndpointRepository.flush();
        entityManager.clear();

        var reloaded = externalApiEndpointRepository.findByApi_IdOrderByNameAsc(api.getId());
        assertThat(reloaded).extracting(ExternalApiEndpoint::getName).containsExactly("archive", "orders", "search");
        var reloadedOrders = reloaded.get(1);
        assertThat(reloadedOrders.getMethod()).isEqualTo(ApiHttpMethod.GET);
        assertThat(reloadedOrders.getPath()).isEqualTo(new ApiPath("/orders"));
        assertThat(reloadedOrders.getQuery()).containsEntry("status", "open").containsEntry("limit", "50");
        assertThat(reloadedOrders.getBody()).isNull();
        assertThat(reloadedOrders.getItemsPath()).isEqualTo(new JsonPathExpr("$.data.orders[*]"));
        assertThat(reloadedOrders.getItemKeyPath()).isEqualTo(new JsonPathExpr("$.id"));
        assertThat(reloadedOrders.getSource()).isEqualTo(EndpointSource.DOCS);
        assertThat(reloaded.get(2).getBody()).containsEntry("query", "{ orders { id } }");
        assertThat(reloaded.get(0).getSource()).isEqualTo(EndpointSource.USER);
        assertThat(externalApiEndpointRepository.findByApi_IdAndEnabledTrue(api.getId())).extracting(ExternalApiEndpoint::getName)
                .containsExactlyInAnyOrder("orders", "search");
    }

    @Test
    void finds_an_endpoint_only_within_its_app() {
        var shop = externalApiRepository.save(Fixtures.externalApi(owner, "shop"));
        var jobs = externalApiRepository.save(Fixtures.externalApi(owner, "jobs"));
        var orders = externalApiEndpointRepository.saveAndFlush(Fixtures.endpoint(shop, "orders"));

        assertThat(externalApiEndpointRepository.findByIdAndApi_Id(orders.getId(), shop.getId())).isPresent();
        assertThat(externalApiEndpointRepository.findByIdAndApi_Id(orders.getId(), jobs.getId())).isEmpty();
    }

    @Test
    void a_get_endpoint_has_no_body() {
        var api = externalApiRepository.save(Fixtures.externalApi(owner, "shop"));
        var endpoint = Fixtures.endpoint(api, "orders");
        endpoint.setBody(Map.of("query", "x"));

        assertThatThrownBy(() -> externalApiEndpointRepository.saveAndFlush(endpoint))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_external_api_endpoint_body");
    }

    @Test
    void endpoint_names_are_unique_per_app() {
        var api = externalApiRepository.save(Fixtures.externalApi(owner, "shop"));
        externalApiEndpointRepository.saveAndFlush(Fixtures.endpoint(api, "orders"));

        assertThatThrownBy(() -> externalApiEndpointRepository.saveAndFlush(Fixtures.endpoint(api, "orders")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_external_api_endpoint_api_name");
    }

    @Test
    void stores_mappings_and_finds_them_by_state() {
        var endpoint = endpoint();
        var orders = collectionRepository.save(Fixtures.collection("orders"));
        var customers = collectionRepository.save(Fixtures.collection("customers"));
        externalApiMappingRepository.save(ExternalApiMapping.builder().endpoint(endpoint).collection(orders)
                .titlePath(new JsonPathExpr("$.number"))
                .fieldMap(List.of(new ApiFieldMapping("total", "$.totals.gross"), new ApiFieldMapping("customer", "$.customer.name")))
                .state(MappingState.ACTIVE).build());
        externalApiMappingRepository.save(ExternalApiMapping.builder().endpoint(endpoint).collection(customers).build());
        externalApiMappingRepository.flush();
        entityManager.clear();

        assertThat(externalApiMappingRepository.findByEndpoint_Id(endpoint.getId())).hasSize(2);
        var active = externalApiMappingRepository.findByEndpoint_IdAndState(endpoint.getId(), MappingState.ACTIVE);
        assertThat(active).singleElement().satisfies(mapping -> {
            assertThat(mapping.getCollection().getId()).isEqualTo(orders.getId());
            assertThat(mapping.getTitlePath()).isEqualTo(new JsonPathExpr("$.number"));
            assertThat(mapping.getFieldMap()).containsExactly(new ApiFieldMapping("total", "$.totals.gross"),
                    new ApiFieldMapping("customer", "$.customer.name"));
        });
        assertThat(externalApiMappingRepository.findByEndpoint_IdAndState(endpoint.getId(), MappingState.PROPOSED))
                .singleElement().satisfies(mapping -> assertThat(mapping.getFieldMap()).isEmpty());
    }

    @Test
    void keeps_the_call_history_newest_first_and_prunes_old_calls() {
        var endpoint = endpoint();
        externalApiCallRepository.saveAll(List.of(
                call(endpoint, NOW.minusSeconds(40 * 86400L), CallOutcome.OK),
                call(endpoint, NOW.minusSeconds(3600), CallOutcome.UNCHANGED),
                call(endpoint, NOW.minusSeconds(60), CallOutcome.FAILED)));
        externalApiCallRepository.flush();
        var api = endpoint.getApi();

        assertThat(externalApiCallRepository.findTop50ByEndpoint_Api_IdOrderByStartedAtDesc(api.getId()))
                .extracting(ExternalApiCall::getOutcome).containsExactly(CallOutcome.FAILED, CallOutcome.UNCHANGED, CallOutcome.OK);
        assertThat(externalApiCallRepository.countByEndpoint_Api_IdAndStartedAtGreaterThanEqual(api.getId(), NOW.minusSeconds(86400)))
                .isEqualTo(2);

        assertThat(externalApiCallRepository.deleteStartedBefore(NOW.minusSeconds(30 * 86400L))).isEqualTo(1);
        assertThat(externalApiCallRepository.findTop50ByEndpoint_Api_IdOrderByStartedAtDesc(api.getId())).hasSize(2);
    }

    @Test
    void stores_a_failed_call_with_its_status_and_counts() {
        var endpoint = endpoint();
        var failed = call(endpoint, NOW, CallOutcome.FAILED);
        failed.setHttpStatus(503);
        failed.setError("Service unavailable");
        var saved = externalApiCallRepository.saveAndFlush(failed);
        var ok = call(endpoint, NOW, CallOutcome.OK);
        ok.setItemsNew(3);
        ok.setItemsChanged(1);
        ok.setItemsGone(2);
        externalApiCallRepository.saveAndFlush(ok);
        entityManager.clear();

        var reloaded = externalApiCallRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getHttpStatus()).isEqualTo(503);
        assertThat(reloaded.getError()).isEqualTo("Service unavailable");
        assertThat(reloaded.getDurationMs()).isEqualTo(120);
        assertThat(externalApiCallRepository.findById(ok.getId()).orElseThrow())
                .extracting(ExternalApiCall::getItemsNew, ExternalApiCall::getItemsChanged, ExternalApiCall::getItemsGone)
                .containsExactly(3, 1, 2);
    }

    @Test
    void finds_the_latest_snapshot_of_an_endpoint() {
        var endpoint = endpoint();
        externalApiSnapshotRepository.saveAll(List.of(snapshot(endpoint, NOW.minusSeconds(3600), "old"),
                snapshot(endpoint, NOW, "new")));
        externalApiSnapshotRepository.flush();
        entityManager.clear();

        var latest = externalApiSnapshotRepository.findFirstByEndpoint_IdOrderByFetchedAtDesc(endpoint.getId()).orElseThrow();
        assertThat(latest.getContentHash()).isEqualTo("new");
        assertThat(latest.getBody()).isEqualTo("{\"orders\":[]}");
        assertThat(latest.getContentType()).isEqualTo("application/json");
        assertThat(latest.isTruncated()).isFalse();
        assertThat(externalApiSnapshotRepository.findByEndpoint_IdOrderByFetchedAtDesc(endpoint.getId()))
                .extracting(ExternalApiSnapshot::getContentHash).containsExactly("new", "old");
    }

    @Test
    void stores_items_with_the_record_they_became() {
        var endpoint = endpoint();
        Collection orders = collectionRepository.save(Fixtures.collection("orders"));
        var linked = item(endpoint, "SO-1");
        linked.setCollection(orders);
        linked.setRecordId(4711L);
        var gone = item(endpoint, "SO-2");
        gone.setGoneAt(NOW);
        externalApiItemRepository.saveAll(List.of(linked, gone, item(endpoint, "SO-3")));
        externalApiItemRepository.flush();
        entityManager.clear();

        var reloaded = externalApiItemRepository.findByEndpoint_IdAndItemKey(endpoint.getId(), "SO-1").orElseThrow();
        assertThat(reloaded.getData()).containsEntry("number", "SO-1");
        assertThat(reloaded.getCollection().getId()).isEqualTo(orders.getId());
        assertThat(reloaded.getRecordId()).isEqualTo(4711L);
        assertThat(reloaded.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(endpoint.getId()))
                .extracting(ExternalApiItem::getItemKey).containsExactlyInAnyOrder("SO-1", "SO-3");
    }

    @Test
    void an_item_links_to_a_collection_and_a_record_together() {
        var item = item(endpoint(), "SO-1");
        item.setRecordId(4711L);

        assertThatThrownBy(() -> externalApiItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_external_api_item_record");
    }

    @Test
    void deleting_an_app_removes_its_endpoints_and_their_history() {
        var endpoint = endpoint();
        externalApiCallRepository.saveAndFlush(call(endpoint, NOW, CallOutcome.OK));
        externalApiSnapshotRepository.saveAndFlush(snapshot(endpoint, NOW, "h"));
        externalApiItemRepository.saveAndFlush(item(endpoint, "SO-1"));
        entityManager.clear();

        externalApiRepository.delete(externalApiRepository.findById(endpoint.getApi().getId()).orElseThrow());
        externalApiRepository.flush();

        for (String table : List.of("external_api", "external_api_endpoint", "external_api_call", "external_api_snapshot", "external_api_item")) {
            assertThat(jdbcTemplate.queryForObject("select count(*) from \"" + table + "\"", Long.class)).as(table).isZero();
        }
    }

    private ExternalApi app(String slug, Consumer<ExternalApi> change) {
        var api = Fixtures.externalApi(owner, slug);
        change.accept(api);
        return externalApiRepository.saveAndFlush(api);
    }

    private ExternalApiEndpoint endpoint() {
        var api = externalApiRepository.save(Fixtures.externalApi(owner, "shop"));
        return externalApiEndpointRepository.saveAndFlush(Fixtures.endpoint(api, "orders"));
    }

    private static ExternalApiCall call(ExternalApiEndpoint endpoint, Instant startedAt, CallOutcome outcome) {
        return ExternalApiCall.builder().endpoint(endpoint).startedAt(startedAt).durationMs(120).httpStatus(200).outcome(outcome).build();
    }

    private static ExternalApiSnapshot snapshot(ExternalApiEndpoint endpoint, Instant fetchedAt, String hash) {
        return ExternalApiSnapshot.builder().endpoint(endpoint).fetchedAt(fetchedAt).contentHash(hash)
                .contentType("application/json").body("{\"orders\":[]}").build();
    }

    private static ExternalApiItem item(ExternalApiEndpoint endpoint, String key) {
        return ExternalApiItem.builder().endpoint(endpoint).itemKey(key).itemHash("hash-" + key)
                .data(Map.of("number", key)).firstSeenAt(NOW).lastSeenAt(NOW).build();
    }
}

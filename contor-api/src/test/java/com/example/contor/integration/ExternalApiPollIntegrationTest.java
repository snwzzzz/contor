package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.ApiStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.Email;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.security.OutboundRequestGuard;
import com.example.contor.service.ExternalApiPollScheduler;
import com.example.contor.service.ExternalApiPoller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The scheduled poll end to end against real JPA and Postgres: calls, snapshots and the item diff across several polls,
 * on the scheduler's own threads and transactions (so nothing here is rolled back; the app goes with its owner). The
 * guard is the one stub: the client, auth handlers and storage are real.
 */
// The test runs the poll itself: no scheduled tick may poll its due app at the same time.
@SpringBootTest(properties = "app.external-apis.enabled=false")
@Import(PostgresTestcontainersConfiguration.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExternalApiPollIntegrationTest {

    @Autowired
    private ExternalApiPollScheduler scheduler;

    @Autowired
    private ExternalApiPoller poller;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ExternalApiRepository externalApiRepository;

    @Autowired
    private ExternalApiEndpointRepository externalApiEndpointRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The network: everything after the guard is real. */
    @MockitoBean
    private OutboundRequestGuard guard;

    private AppUser owner;
    private ExternalApi shop;
    private ExternalApiEndpoint orders;

    @BeforeEach
    void setUp() {
        owner = appUserRepository.saveAndFlush(AppUser.builder().username(new Username("poll-owner")).email(new Email("poll-owner@example.com"))
                .passwordHash("$argon2id$test").role(UserRole.OWNER).build());
        shop = externalApiRepository.saveAndFlush(ExternalApi.builder().owner(owner).name("Shop").slug(new Slug("shop"))
                .baseUrl(new BaseUrl("https://api.shop.example/v1")).pollIntervalMinutes(60)
                .nextCallAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build());
        orders = externalApiEndpointRepository.saveAndFlush(ExternalApiEndpoint.builder().api(shop).name("Orders")
                .path(new ApiPath("/orders")).itemsPath(new JsonPathExpr("$.data[*]")).itemKeyPath(new JsonPathExpr("$.id")).build());
    }

    @AfterEach
    void tearDown() {
        // The app and everything under it are deleted with their owner.
        jdbcTemplate.update("delete from app_user where id = ?", owner.getId().id());
    }

    private void answer(int status, String body) {
        when(guard.send(any())).thenReturn(new OutboundRequestGuard.Response(status, Map.of("content-type", "application/json"),
                body.getBytes(StandardCharsets.UTF_8), false));
    }

    private void makeDue() {
        jdbcTemplate.update("update external_api set next_call_at = ? where id = ?", Timestamp.from(Instant.now().minusSeconds(60)),
                shop.getId().id());
    }

    private Map<String, Object> lastCall() {
        return jdbcTemplate.queryForMap("select outcome, http_status, items_new, items_changed, items_gone from external_api_call "
                + "where endpoint_id = ? order by id desc limit 1", orders.getId().id());
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, orders.getId().id());
        return value == null ? 0 : value;
    }

    @Test
    void polls_record_calls_snapshots_and_the_item_diff_and_rejected_credentials_stop_them() {
        answer(200, "{\"data\":[{\"id\":1,\"total\":3},{\"id\":2,\"total\":4}]}");
        scheduler.pollDue();

        assertThat(lastCall()).containsEntry("outcome", "OK").containsEntry("http_status", 200).containsEntry("items_new", 2);
        assertThat(count("select count(*) from external_api_snapshot where endpoint_id = ?")).isEqualTo(1);
        assertThat(count("select count(*) from external_api_item where endpoint_id = ? and gone_at is null")).isEqualTo(2);
        var polled = externalApiRepository.findById(shop.getId()).orElseThrow();
        assertThat(polled.getLastCalledAt()).isNotNull();
        assertThat(polled.getNextCallAt()).isAfter(Instant.now().plus(50, ChronoUnit.MINUTES));
        assertThat(poller.findDue()).extracting(ExternalApiPoller.DueApi::id).doesNotContain(shop.getId().id());

        makeDue();
        scheduler.pollDue();
        assertThat(lastCall()).containsEntry("outcome", "UNCHANGED");
        assertThat(count("select count(*) from external_api_snapshot where endpoint_id = ?")).isEqualTo(1);

        makeDue();
        answer(200, "{\"data\":[{\"id\":1,\"total\":5},{\"id\":3,\"total\":1}]}");
        scheduler.pollDue();
        assertThat(lastCall()).containsEntry("outcome", "OK").containsEntry("items_new", 1).containsEntry("items_changed", 1)
                .containsEntry("items_gone", 1);
        assertThat(count("select count(*) from external_api_snapshot where endpoint_id = ?")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("select item_key from external_api_item where endpoint_id = ? and gone_at is not null",
                String.class, orders.getId().id())).isEqualTo(List.of("2"));
        assertThat(jdbcTemplate.queryForObject("select data ->> 'total' from external_api_item where endpoint_id = ? and item_key = '1'",
                String.class, orders.getId().id())).isEqualTo("5");

        makeDue();
        answer(401, "{\"error\":\"bad key\"}");
        scheduler.pollDue();
        assertThat(lastCall()).containsEntry("outcome", "FAILED").containsEntry("http_status", 401);
        var rejected = externalApiRepository.findById(shop.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ApiStatus.NEEDS_SETUP);
        assertThat(rejected.getNextCallAt()).isNull();
        assertThat(rejected.getLastError()).isEqualTo("The app refused the credentials (HTTP 401)");
        assertThat(count("select count(*) from external_api_call where endpoint_id = ?")).isEqualTo(4);
    }
}

package com.example.contor.mapper;

import com.example.contor.api.model.ApiAuthType;
import com.example.contor.api.model.ApiCallResult;
import com.example.contor.api.model.ApiHeaderDto;
import com.example.contor.api.model.ApiMappingStatus;
import com.example.contor.api.model.ApiState;
import com.example.contor.api.model.DiscoveryState;
import com.example.contor.api.model.EndpointMethod;
import com.example.contor.api.model.EndpointOrigin;
import com.example.contor.api.model.ExternalApiDto;
import com.example.contor.api.model.ExternalApiEndpointDto;
import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiHttpMethod;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.ApiSecret;
import com.example.contor.domain.ApiStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.CallOutcome;
import com.example.contor.domain.Collection;
import com.example.contor.domain.DiscoveryStatus;
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
import com.example.contor.externalapi.ApiDiscovery;
import com.example.contor.externalapi.DiscoveredEndpoint;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ExternalApiMapperTest {

    private static final Instant CREATED = Instant.parse("2026-10-29T09:00:00Z");

    @Test
    void maps_an_app_without_secret_values() {
        var api = ExternalApi.builder().id(new ExternalApi.ExternalApiId(7L)).name("Shop").slug(new Slug("shop"))
                .description("Orders").baseUrl(new BaseUrl("https://api.shop.example/v1"))
                .docsUrl(new HttpsUrl("https://docs.shop.example")).authKind(ApiAuthKind.API_KEY_HEADER)
                .authConfig(new ApiAuthConfig("X-Api-Key", null, null, null, null, null))
                .secret("{\"value\":\"key-123\"}")
                .headers(List.of(new ApiHeader("Accept", "application/json"))).pollIntervalMinutes(60).enabled(false)
                .aiAnalysis(false).status(ApiStatus.FAILING).lastCalledAt(CREATED).nextCallAt(CREATED.plusSeconds(3600))
                .failureCount(2).lastError("Service unavailable (503)").dailyRequestLimit(50)
                .endpoints(new ArrayList<>(List.of(ExternalApiEndpoint.builder().name("orders").path(new ApiPath("/orders")).build())))
                .build();
        api.setCreatedAt(CREATED);
        api.setLastModified(CREATED);

        ExternalApiDto dto = ExternalApiMapper.toDto(api, new ApiSecret("key-123", List.of(new ApiHeader("X-Shop-Token", "t-1"))));

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("Shop");
        assertThat(dto.getSlug()).isEqualTo("shop");
        assertThat(dto.getDescription()).isEqualTo("Orders");
        assertThat(dto.getBaseUrl()).isEqualTo("https://api.shop.example/v1");
        assertThat(dto.getDocsUrl()).isEqualTo("https://docs.shop.example");
        assertThat(dto.getAuthKind()).isEqualTo(ApiAuthType.API_KEY_HEADER);
        assertThat(dto.getAuthConfig().getHeaderName()).isEqualTo("X-Api-Key");
        assertThat(dto.getHasSecret()).isTrue();
        assertThat(dto.getHeaders()).containsExactly(new ApiHeaderDto("Accept", "application/json"));
        assertThat(dto.getSecretHeaderNames()).containsExactly("X-Shop-Token");
        assertThat(dto.getPollIntervalMinutes()).isEqualTo(60);
        assertThat(dto.getEnabled()).isFalse();
        assertThat(dto.getAiAnalysis()).isFalse();
        assertThat(dto.getStatus()).isEqualTo(ApiState.FAILING);
        assertThat(dto.getLastCalledAt()).isEqualTo(OffsetDateTime.of(2026, 10, 29, 9, 0, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getNextCallAt()).isEqualTo(OffsetDateTime.of(2026, 10, 29, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getFailureCount()).isEqualTo(2);
        assertThat(dto.getLastError()).isEqualTo("Service unavailable (503)");
        assertThat(dto.getDailyRequestLimit()).isEqualTo(50);
        assertThat(dto.getEndpointCount()).isEqualTo(1);
        assertThat(dto.getCreatedAt()).isNotNull();
        assertThat(dto.toString()).doesNotContain("key-123").doesNotContain("t-1");
    }

    @Test
    void an_app_without_secret_has_no_secret_and_no_secret_headers() {
        var dto = ExternalApiMapper.toDto(api(), ApiSecret.EMPTY);

        assertThat(dto.getHasSecret()).isFalse();
        assertThat(dto.getSecretHeaderNames()).isEmpty();
        assertThat(dto.getAuthKind()).isEqualTo(ApiAuthType.NONE);
        assertThat(dto.getAuthConfig().getHeaderName()).isNull();
    }

    @Test
    void handles_null_input_and_null_id() {
        assertThat(ExternalApiMapper.toDto(null, ApiSecret.EMPTY)).isNull();
        assertThat(ExternalApiMapper.toDto(api(), ApiSecret.EMPTY).getId()).isNull();
        assertThat(ExternalApiMapper.toDto(api(), ApiSecret.EMPTY).getDocsUrl()).isNull();
        assertThat(ExternalApiMapper.toDto((ExternalApiEndpoint) null)).isNull();
    }

    @Test
    void maps_an_endpoint() {
        var api = api();
        api.setId(new ExternalApi.ExternalApiId(7L));
        var endpoint = ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(3L)).api(api).name("search")
                .method(ApiHttpMethod.POST).path(new ApiPath("/search")).query(Map.of("limit", "50"))
                .body(Map.of("query", "{ orders { id } }")).itemsPath(new JsonPathExpr("$.data[*]"))
                .itemKeyPath(new JsonPathExpr("$.id")).description("Search orders").source(EndpointSource.DOCS).enabled(false)
                .build();
        endpoint.setCreatedAt(CREATED);

        ExternalApiEndpointDto dto = ExternalApiMapper.toDto(endpoint);

        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getApiId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("search");
        assertThat(dto.getMethod()).isEqualTo(EndpointMethod.POST);
        assertThat(dto.getPath()).isEqualTo("/search");
        assertThat(dto.getQuery()).containsEntry("limit", "50");
        assertThat(dto.getBody()).containsEntry("query", "{ orders { id } }");
        assertThat(dto.getItemsPath()).isEqualTo("$.data[*]");
        assertThat(dto.getItemKeyPath()).isEqualTo("$.id");
        assertThat(dto.getDescription()).isEqualTo("Search orders");
        assertThat(dto.getSource()).isEqualTo(EndpointOrigin.DOCS);
        assertThat(dto.getEnabled()).isFalse();
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @Test
    void maps_an_endpoint_without_optional_parts() {
        var dto = ExternalApiMapper.toDto(ExternalApiEndpoint.builder().name("orders").path(new ApiPath("/orders")).build());

        assertThat(dto.getId()).isNull();
        assertThat(dto.getApiId()).isNull();
        assertThat(dto.getMethod()).isEqualTo(EndpointMethod.GET);
        assertThat(dto.getQuery()).isEmpty();
        assertThat(dto.getBody()).isNull();
        assertThat(dto.getItemsPath()).isNull();
        assertThat(dto.getItemKeyPath()).isNull();
        assertThat(dto.getSource()).isEqualTo(EndpointOrigin.USER);
    }

    @Test
    void maps_a_call_with_its_endpoint() {
        var endpoint = ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(30L)).name("Orders")
                .path(new ApiPath("/orders")).build();
        var call = ExternalApiCall.builder().id(5L).endpoint(endpoint).startedAt(Instant.parse("2026-10-29T12:00:00Z")).durationMs(120)
                .httpStatus(200).outcome(CallOutcome.OK).error("The answer is no JSON, so no items were read")
                .itemsNew(2).itemsChanged(1).itemsGone(3).build();

        var dto = ExternalApiMapper.toDto(call);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getEndpointId()).isEqualTo(30L);
        assertThat(dto.getEndpointName()).isEqualTo("Orders");
        assertThat(dto.getStartedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 29, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getDurationMs()).isEqualTo(120);
        assertThat(dto.getHttpStatus()).isEqualTo(200);
        assertThat(dto.getOutcome()).isEqualTo(ApiCallResult.OK);
        assertThat(dto.getError()).isEqualTo("The answer is no JSON, so no items were read");
        assertThat(List.of(dto.getItemsNew(), dto.getItemsChanged(), dto.getItemsGone())).containsExactly(2, 1, 3);
    }

    @Test
    void maps_a_failed_call_without_answer_and_null_input() {
        var dto = ExternalApiMapper.toDto(ExternalApiCall.builder().startedAt(Instant.parse("2026-10-29T12:00:00Z"))
                .outcome(CallOutcome.FAILED).build());

        assertThat(dto.getId()).isNull();
        assertThat(dto.getEndpointId()).isNull();
        assertThat(dto.getHttpStatus()).isNull();
        assertThat(dto.getOutcome()).isEqualTo(ApiCallResult.FAILED);
        assertThat(ExternalApiMapper.toDto((ExternalApiCall) null)).isNull();
    }

    @Test
    void maps_a_discovery_with_its_suggestions() {
        var discovery = new ApiDiscovery("d-1", new AppUser.AppUserId(1L), DiscoveryStatus.DONE, EndpointSource.DOCS,
                List.of(new DiscoveredEndpoint("Orders", new ApiPath("/orders"), Map.of("status", "open"), new JsonPathExpr("$.data[*]"),
                        new JsonPathExpr("$.id"), "Open orders", EndpointSource.DOCS)), null, Instant.parse("2026-10-29T12:00:00Z"));

        var dto = ExternalApiMapper.toDto(discovery);

        assertThat(dto.getId()).isEqualTo("d-1");
        assertThat(dto.getState()).isEqualTo(DiscoveryState.DONE);
        assertThat(dto.getSource()).isEqualTo(EndpointOrigin.DOCS);
        assertThat(dto.getStartedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 29, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getEndpoints()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.getName()).isEqualTo("Orders");
            assertThat(endpoint.getMethod()).isEqualTo(EndpointMethod.GET);
            assertThat(endpoint.getPath()).isEqualTo("/orders");
            assertThat(endpoint.getQuery()).containsEntry("status", "open");
            assertThat(endpoint.getItemsPath()).isEqualTo("$.data[*]");
            assertThat(endpoint.getItemKeyPath()).isEqualTo("$.id");
            assertThat(endpoint.getDescription()).isEqualTo("Open orders");
            assertThat(endpoint.getSource()).isEqualTo(EndpointOrigin.DOCS);
        });
    }

    @Test
    void maps_a_running_or_failed_discovery_and_null_input() {
        var dto = ExternalApiMapper.toDto(new ApiDiscovery("d-2", new AppUser.AppUserId(1L), DiscoveryStatus.FAILED, null, List.of(),
                "The documentation answered with HTTP 404", Instant.parse("2026-10-29T12:00:00Z")));

        assertThat(dto.getState()).isEqualTo(DiscoveryState.FAILED);
        assertThat(dto.getSource()).isNull();
        assertThat(dto.getEndpoints()).isEmpty();
        assertThat(dto.getError()).isEqualTo("The documentation answered with HTTP 404");
        assertThat(ExternalApiMapper.toDto((ApiDiscovery) null)).isNull();
    }

    private static ExternalApi api() {
        return ExternalApi.builder().name("Shop").slug(new Slug("shop")).baseUrl(new BaseUrl("https://api.shop.example")).build();
    }

    @Test
    void maps_an_endpoint_with_the_summary_of_its_mapping() {
        var endpoint = ExternalApiEndpoint.builder().name("orders").path(new ApiPath("/orders")).build();
        var mapping = ExternalApiMapping.builder().id(new ExternalApiMapping.ExternalApiMappingId(40L))
                .collection(Collection.builder().id(new Collection.CollectionId(4L)).build()).state(MappingState.ACTIVE)
                .fieldMap(new ArrayList<>(List.of(new ApiFieldMapping("total", "$.total"), new ApiFieldMapping("customer", "$.c")))).build();

        var dto = ExternalApiMapper.toDto(endpoint, mapping);

        assertThat(dto.getMapping().getId()).isEqualTo(40L);
        assertThat(dto.getMapping().getCollectionId()).isEqualTo(4L);
        assertThat(dto.getMapping().getState()).isEqualTo(ApiMappingStatus.ACTIVE);
        assertThat(dto.getMapping().getFieldCount()).isEqualTo(2);
        assertThat(ExternalApiMapper.toDto(endpoint, null).getMapping()).isNull();
    }

    @Test
    void maps_an_answer_and_an_item() {
        var snapshot = ExternalApiSnapshot.builder().fetchedAt(Instant.parse("2026-10-29T12:00:00Z")).contentType("application/json")
                .body("x".repeat(25_000)).truncated(false).build();
        var item = ExternalApiItem.builder().id(9L).itemKey("1058424").data(new HashMap<>(Map.of("title", "Hope")))
                .firstSeenAt(Instant.parse("2026-10-29T12:00:00Z")).lastSeenAt(Instant.parse("2026-10-29T13:00:00Z"))
                .collection(Collection.builder().id(new Collection.CollectionId(4L)).build()).recordId(3L).build();

        var answer = ExternalApiMapper.toDto(snapshot);
        var itemDto = ExternalApiMapper.toDto(item);

        assertThat(answer.getBody()).hasSize(ExternalApiMapper.ANSWER_CHARS);
        assertThat(answer.getTruncated()).as("cut here").isTrue();
        assertThat(answer.getContentType()).isEqualTo("application/json");
        assertThat(itemDto.getKey()).isEqualTo("1058424");
        assertThat(itemDto.getData()).containsEntry("title", "Hope");
        assertThat(itemDto.getCollectionId()).isEqualTo(4L);
        assertThat(itemDto.getRecordId()).isEqualTo(3L);
        assertThat(itemDto.getGoneAt()).isNull();
        assertThat(ExternalApiMapper.toDto((ExternalApiSnapshot) null)).isNull();
        assertThat(ExternalApiMapper.toDto((ExternalApiItem) null)).isNull();
    }
}

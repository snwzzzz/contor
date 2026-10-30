package com.example.contor.mapper;

import com.example.contor.api.model.ApiAuthConfigDto;
import com.example.contor.api.model.ApiAuthType;
import com.example.contor.api.model.ApiCallResult;
import com.example.contor.api.model.ApiDiscoveryDto;
import com.example.contor.api.model.ApiHeaderDto;
import com.example.contor.api.model.ApiMappingStatus;
import com.example.contor.api.model.ApiState;
import com.example.contor.api.model.DiscoveredEndpointDto;
import com.example.contor.api.model.DiscoveryState;
import com.example.contor.api.model.EndpointMappingDto;
import com.example.contor.api.model.EndpointMethod;
import com.example.contor.api.model.EndpointOrigin;
import com.example.contor.api.model.ExternalApiAnswerDto;
import com.example.contor.api.model.ExternalApiCallDto;
import com.example.contor.api.model.ExternalApiDto;
import com.example.contor.api.model.ExternalApiEndpointDto;
import com.example.contor.api.model.ExternalApiItemDto;
import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiSecret;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiCall;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.ExternalApiSnapshot;
import com.example.contor.externalapi.ApiDiscovery;
import com.example.contor.externalapi.DiscoveredEndpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * External APIs ("Apps") to their DTOs. Secret values never leave: the app's DTO says only whether a secret is stored
 * and which secret headers there are, from the decrypted {@link ApiSecret} the service passes in.
 */
public class ExternalApiMapper {

    /** Characters of a stored answer in its DTO. */
    public static final int ANSWER_CHARS = 20_000;

    public static ExternalApiDto toDto(ExternalApi api, ApiSecret secret) {
        if (api == null) return null;
        ApiAuthConfig config = api.getAuthConfig() != null ? api.getAuthConfig() : ApiAuthConfig.NONE;

        return new ExternalApiDto()
                .id(api.getId() != null ? api.getId().id() : null)
                .name(api.getName())
                .slug(api.getSlug() != null ? api.getSlug().value() : null)
                .description(api.getDescription())
                .baseUrl(api.getBaseUrl() != null ? api.getBaseUrl().value() : null)
                .docsUrl(api.getDocsUrl() != null ? api.getDocsUrl().value() : null)
                .authKind(ApiAuthType.valueOf(api.getAuthKind().name()))
                .authConfig(new ApiAuthConfigDto()
                        .headerName(config.headerName())
                        .queryParam(config.queryParam())
                        .tokenUrl(config.tokenUrl())
                        .scope(config.scope())
                        .username(config.username())
                        .clientId(config.clientId()))
                .hasSecret(secret.hasValue())
                .headers(new ArrayList<>(api.getHeaders().stream().map(ExternalApiMapper::toDto).toList()))
                .secretHeaderNames(new ArrayList<>(secret.headers().stream().map(ApiHeader::name).toList()))
                .pollIntervalMinutes(api.getPollIntervalMinutes())
                .enabled(api.isEnabled())
                .aiAnalysis(api.isAiAnalysis())
                .status(ApiState.valueOf(api.getStatus().name()))
                .lastCalledAt(MapperSupport.toOffset(api.getLastCalledAt()))
                .nextCallAt(MapperSupport.toOffset(api.getNextCallAt()))
                .failureCount(api.getFailureCount())
                .lastError(api.getLastError())
                .dailyRequestLimit(api.getDailyRequestLimit())
                .endpointCount(api.getEndpoints().size())
                .createdAt(MapperSupport.toOffset(api.getCreatedAt()))
                .lastModified(MapperSupport.toOffset(api.getLastModified()));
    }

    public static ExternalApiEndpointDto toDto(ExternalApiEndpoint endpoint) {
        return toDto(endpoint, null);
    }

    /** The endpoint with the summary of {@code mapping} (the one that counts for it, or {@code null}). */
    public static ExternalApiEndpointDto toDto(ExternalApiEndpoint endpoint, ExternalApiMapping mapping) {
        if (endpoint == null) return null;

        return new ExternalApiEndpointDto()
                .id(endpoint.getId() != null ? endpoint.getId().id() : null)
                .apiId(endpoint.getApi() != null && endpoint.getApi().getId() != null ? endpoint.getApi().getId().id() : null)
                .name(endpoint.getName())
                .method(EndpointMethod.valueOf(endpoint.getMethod().name()))
                .path(endpoint.getPath() != null ? endpoint.getPath().value() : null)
                .query(new LinkedHashMap<>(endpoint.getQuery()))
                .body(endpoint.getBody() != null ? new LinkedHashMap<>(endpoint.getBody()) : null)
                .itemsPath(endpoint.getItemsPath() != null ? endpoint.getItemsPath().value() : null)
                .itemKeyPath(endpoint.getItemKeyPath() != null ? endpoint.getItemKeyPath().value() : null)
                .description(endpoint.getDescription())
                .source(EndpointOrigin.valueOf(endpoint.getSource().name()))
                .enabled(endpoint.isEnabled())
                .createdAt(MapperSupport.toOffset(endpoint.getCreatedAt()))
                .lastModified(MapperSupport.toOffset(endpoint.getLastModified()))
                .mapping(mapping == null ? null : new EndpointMappingDto()
                        .id(mapping.getId() != null ? mapping.getId().id() : null)
                        .collectionId(mapping.getCollection() != null && mapping.getCollection().getId() != null
                                ? mapping.getCollection().getId().id() : null)
                        .state(ApiMappingStatus.valueOf(mapping.getState().name()))
                        .fieldCount(mapping.getFieldMap().size()));
    }

    public static ExternalApiAnswerDto toDto(ExternalApiSnapshot snapshot) {
        if (snapshot == null) return null;
        String body = snapshot.getBody() != null ? snapshot.getBody() : "";
        boolean cut = body.length() > ANSWER_CHARS;
        return new ExternalApiAnswerDto()
                .fetchedAt(MapperSupport.toOffset(snapshot.getFetchedAt()))
                .contentType(snapshot.getContentType())
                .body(cut ? body.substring(0, ANSWER_CHARS) : body)
                .truncated(snapshot.isTruncated() || cut);
    }

    public static ExternalApiItemDto toDto(ExternalApiItem item) {
        if (item == null) return null;
        return new ExternalApiItemDto()
                .id(item.getId())
                .key(item.getItemKey())
                .data(new LinkedHashMap<>(item.getData()))
                .firstSeenAt(MapperSupport.toOffset(item.getFirstSeenAt()))
                .lastSeenAt(MapperSupport.toOffset(item.getLastSeenAt()))
                .goneAt(MapperSupport.toOffset(item.getGoneAt()))
                .collectionId(item.getCollection() != null && item.getCollection().getId() != null ? item.getCollection().getId().id() : null)
                .recordId(item.getRecordId());
    }

    public static ExternalApiCallDto toDto(ExternalApiCall call) {
        if (call == null) return null;
        ExternalApiEndpoint endpoint = call.getEndpoint();

        return new ExternalApiCallDto()
                .id(call.getId())
                .endpointId(endpoint != null && endpoint.getId() != null ? endpoint.getId().id() : null)
                .endpointName(endpoint != null ? endpoint.getName() : null)
                .startedAt(MapperSupport.toOffset(call.getStartedAt()))
                .durationMs(call.getDurationMs())
                .httpStatus(call.getHttpStatus())
                .outcome(ApiCallResult.valueOf(call.getOutcome().name()))
                .error(call.getError())
                .itemsNew(call.getItemsNew())
                .itemsChanged(call.getItemsChanged())
                .itemsGone(call.getItemsGone());
    }

    public static ApiDiscoveryDto toDto(ApiDiscovery discovery) {
        if (discovery == null) return null;

        return new ApiDiscoveryDto()
                .id(discovery.id())
                .state(DiscoveryState.valueOf(discovery.status().name()))
                .source(discovery.source() != null ? EndpointOrigin.valueOf(discovery.source().name()) : null)
                .endpoints(new ArrayList<>(discovery.endpoints().stream().map(ExternalApiMapper::toDto).toList()))
                .error(discovery.error())
                .startedAt(MapperSupport.toOffset(discovery.startedAt()));
    }

    private static DiscoveredEndpointDto toDto(DiscoveredEndpoint endpoint) {
        return new DiscoveredEndpointDto()
                .name(endpoint.name())
                .method(EndpointMethod.GET)
                .path(endpoint.path().value())
                .query(new LinkedHashMap<>(endpoint.query()))
                .itemsPath(endpoint.itemsPath() != null ? endpoint.itemsPath().value() : null)
                .itemKeyPath(endpoint.itemKeyPath() != null ? endpoint.itemKeyPath().value() : null)
                .description(endpoint.description())
                .source(EndpointOrigin.valueOf(endpoint.source().name()));
    }

    private static ApiHeaderDto toDto(ApiHeader header) {
        return new ApiHeaderDto(header.name(), header.value());
    }
}

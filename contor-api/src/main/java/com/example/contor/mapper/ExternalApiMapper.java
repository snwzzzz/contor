package com.example.contor.mapper;

import com.example.contor.api.model.ApiAuthConfigDto;
import com.example.contor.api.model.ApiAuthType;
import com.example.contor.api.model.ApiHeaderDto;
import com.example.contor.api.model.ApiState;
import com.example.contor.api.model.EndpointMethod;
import com.example.contor.api.model.EndpointOrigin;
import com.example.contor.api.model.ExternalApiDto;
import com.example.contor.api.model.ExternalApiEndpointDto;
import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiSecret;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * External APIs ("Apps") to their DTOs. Secret values never leave: the app's DTO says only whether a secret is stored
 * and which secret headers there are, from the decrypted {@link ApiSecret} the service passes in.
 */
public class ExternalApiMapper {

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
                .lastModified(MapperSupport.toOffset(endpoint.getLastModified()));
    }

    private static ApiHeaderDto toDto(ApiHeader header) {
        return new ApiHeaderDto(header.name(), header.value());
    }
}

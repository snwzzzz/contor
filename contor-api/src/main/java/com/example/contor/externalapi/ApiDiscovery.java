package com.example.contor.externalapi;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.DiscoveryStatus;
import com.example.contor.domain.EndpointSource;

import java.time.Instant;
import java.util.List;

/**
 * One "Read the docs" run of a user (in memory, {@link ApiDiscoveryStore}); nothing of it is saved.
 *
 * @param source    DOCS when an OpenAPI document was parsed, MODEL when the model read the docs; {@code null} until done
 * @param endpoints the suggestions once DONE
 * @param error     why it FAILED
 */
public record ApiDiscovery(String id, AppUser.AppUserId ownerId, DiscoveryStatus status, EndpointSource source,
                           List<DiscoveredEndpoint> endpoints, String error, Instant startedAt) {

    public ApiDiscovery {
        endpoints = List.copyOf(endpoints);
    }

    ApiDiscovery done(EndpointSource source, List<DiscoveredEndpoint> endpoints) {
        return new ApiDiscovery(id, ownerId, DiscoveryStatus.DONE, source, endpoints, null, startedAt);
    }

    ApiDiscovery failed(String error) {
        return new ApiDiscovery(id, ownerId, DiscoveryStatus.FAILED, null, List.of(), error, startedAt);
    }
}

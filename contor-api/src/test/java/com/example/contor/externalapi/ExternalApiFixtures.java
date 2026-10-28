package com.example.contor.externalapi;

import com.example.contor.security.OutboundRequestGuard;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

import static org.mockito.Mockito.mock;

/** Every real auth handler (the token handler with a mocked guard) and their registry, for tests. */
final class ExternalApiFixtures {

    private ExternalApiFixtures() {
    }

    static List<ApiAuthHandler> authHandlers() {
        return List.of(new NoAuthHandler(), new ApiKeyHeaderAuthHandler(), new ApiKeyQueryAuthHandler(), new BearerAuthHandler(),
                new BasicAuthHandler(), new ClientCredentialsAuthHandler(mock(OutboundRequestGuard.class), JsonMapper.builder().build(),
                        Clock.systemUTC()));
    }

    static ApiAuthRegistry authRegistry() {
        return new ApiAuthRegistry(authHandlers());
    }
}

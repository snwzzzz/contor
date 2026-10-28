package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthKind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApiAuthRegistryTest {

    @Test
    void resolves_a_handler_for_every_auth_kind() {
        var registry = ExternalApiFixtures.authRegistry();

        for (ApiAuthKind kind : ApiAuthKind.values()) {
            assertThat(registry.handler(kind).kind()).isEqualTo(kind);
        }
    }

    @Test
    void refuses_to_start_without_a_handler_for_a_kind() {
        assertThatThrownBy(() -> new ApiAuthRegistry(List.of(new NoAuthHandler(), new BearerAuthHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_KEY_HEADER");
    }

    @Test
    void refuses_two_handlers_for_one_kind() {
        var handlers = new ArrayList<>(ExternalApiFixtures.authHandlers());
        handlers.add(new BearerAuthHandler());

        assertThatThrownBy(() -> new ApiAuthRegistry(handlers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEARER");
    }
}

package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthKind;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects every {@link ApiAuthHandler} bean, keyed by kind; fails at startup when a kind has none or two. */
@Component
public class ApiAuthRegistry {

    private final Map<ApiAuthKind, ApiAuthHandler> handlers = new EnumMap<>(ApiAuthKind.class);

    public ApiAuthRegistry(List<ApiAuthHandler> handlers) {
        for (ApiAuthHandler handler : handlers) {
            ApiAuthHandler previous = this.handlers.putIfAbsent(handler.kind(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two ApiAuthHandlers for " + handler.kind() + ": "
                        + previous.getClass().getSimpleName() + ", " + handler.getClass().getSimpleName());
            }
        }
        Set<ApiAuthKind> missing = EnumSet.allOf(ApiAuthKind.class);
        missing.removeAll(this.handlers.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No ApiAuthHandler for " + missing);
        }
    }

    public ApiAuthHandler handler(ApiAuthKind kind) {
        return handlers.get(kind);
    }
}

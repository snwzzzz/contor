package com.example.contor.proposal;

import java.util.Map;

/** Typed access to proposal payloads (JSON maps); every problem is an {@link IllegalArgumentException} naming the key. */
final class ProposalPayload {

    private ProposalPayload() {
    }

    static Long requiredId(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() <= 0) {
            throw new IllegalArgumentException("payload." + key + " is required");
        }
        return ((Number) value).longValue();
    }

    static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("payload." + key + " must be a string");
        }
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> optionalObject(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value != null && !(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("payload." + key + " must be an object");
        }
        return (Map<String, Object>) value;
    }
}

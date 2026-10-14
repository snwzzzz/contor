package com.example.contor.fieldtype;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads typed values from a field's JSON options. */
final class FieldOptions {

    private FieldOptions() {
    }

    static Optional<Object> get(Map<String, Object> options, String key) {
        return options == null ? Optional.empty() : Optional.ofNullable(options.get(key));
    }

    /** A non-empty list of distinct, non-blank strings, or the given error. */
    static List<String> distinctStrings(Map<String, Object> options, String key, String missingMessage, String invalidMessage) {
        Object raw = get(options, key).orElse(null);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(missingMessage);
        }
        var seen = new HashSet<String>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank() || !seen.add(text)) {
                throw new IllegalArgumentException(invalidMessage);
            }
        }
        return list.stream().map(String.class::cast).toList();
    }

    static Optional<BigDecimal> decimal(Map<String, Object> options, String key) {
        return get(options, key).map(value -> {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(key + " must be a number");
            }
            return new BigDecimal(number.toString());
        });
    }

    static Optional<Long> positiveLong(Map<String, Object> options, String key, String message) {
        return get(options, key).map(value -> {
            if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() <= 0) {
                throw new IllegalArgumentException(message);
            }
            return ((Number) value).longValue();
        });
    }
}

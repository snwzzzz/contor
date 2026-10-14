package com.example.contor.fieldtype;

import java.math.BigDecimal;

/** Numeric helpers: JSON numbers in, plain decimals without trailing zeros out. */
final class Decimals {

    private Decimals() {
    }

    static BigDecimal fromJson(Object value, String message) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(message);
        }
        if (number instanceof Double d && (d.isNaN() || d.isInfinite())
                || number instanceof Float f && (f.isNaN() || f.isInfinite())) {
            throw new IllegalArgumentException(message);
        }
        return new BigDecimal(number.toString());
    }

    static BigDecimal parse(String raw, String message) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 19.9900 → 19.99, 100 stays 100 (never 1E+2). */
    static BigDecimal normalize(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}

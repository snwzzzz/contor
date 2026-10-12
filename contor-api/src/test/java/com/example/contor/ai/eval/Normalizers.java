package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionFieldType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Brings a field value to a canonical string so that equal facts written differently compare equal (time zones,
 * phone formatting, money decimals, case and accents). A value that cannot be parsed falls back to its trimmed text:
 * it then simply does not match, instead of failing the evaluation.
 */
final class Normalizers {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOT_PHONE = Pattern.compile("[^0-9+]");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private Normalizers() {
    }

    static String normalize(CollectionFieldType type, Object value) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case EMAIL -> trimmed(value).toLowerCase(Locale.ROOT);
            case URL -> trimmed(value).toLowerCase(Locale.ROOT).replaceAll("/+$", "");
            case ENUM, STATUS -> trimmed(value).toLowerCase(Locale.ROOT);
            case PHONE -> phone(value);
            case DATE -> date(value);
            case DATETIME -> dateTime(value);
            case MONEY -> money(value);
            case NUMBER -> number(value);
            default -> text(value);
        };
    }

    /** Lower-case, accent-free, single-spaced text. */
    static String text(Object value) {
        String decomposed = Normalizer.normalize(String.valueOf(value), Normalizer.Form.NFD);
        return WHITESPACE.matcher(MARKS.matcher(decomposed).replaceAll("")).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }

    /** Word tokens of {@link #text(Object)}, for title similarity. */
    static List<String> tokens(Object value) {
        return NOT_WORD.splitAsStream(text(value)).filter(token -> !token.isEmpty()).toList();
    }

    private static String trimmed(Object value) {
        return String.valueOf(value).strip();
    }

    private static String phone(Object value) {
        String digits = NOT_PHONE.matcher(trimmed(value)).replaceAll("");
        return digits.startsWith("00") ? "+" + digits.substring(2) : digits;
    }

    private static String date(Object value) {
        try {
            return LocalDate.parse(trimmed(value)).toString();
        } catch (DateTimeParseException e) {
            return trimmed(value);
        }
    }

    private static String dateTime(Object value) {
        try {
            return OffsetDateTime.parse(trimmed(value)).toInstant().toString();
        } catch (DateTimeParseException e) {
            return trimmed(value);
        }
    }

    private static String money(Object value) {
        if (value instanceof Map<?, ?> map && map.get("amount") != null && map.get("currency") != null) {
            try {
                BigDecimal amount = new BigDecimal(String.valueOf(map.get("amount"))).setScale(2, RoundingMode.HALF_UP);
                return amount.toPlainString() + " " + String.valueOf(map.get("currency")).strip().toUpperCase(Locale.ROOT);
            } catch (NumberFormatException e) {
                // fall through to the text form
            }
        }
        return trimmed(value);
    }

    private static String number(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).strip()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return trimmed(value);
        }
    }
}

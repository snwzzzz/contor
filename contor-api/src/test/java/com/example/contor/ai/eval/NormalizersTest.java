package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionFieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NormalizersTest {

    @Test
    void text_is_case_accent_and_whitespace_insensitive() {
        assertThat(Normalizers.normalize(CollectionFieldType.TEXT, "  Müller   GmbH "))
                .isEqualTo(Normalizers.normalize(CollectionFieldType.TEXT, "muller gmbh"))
                .isEqualTo("muller gmbh");
    }

    @Test
    void email_is_lowercased_and_trimmed() {
        assertThat(Normalizers.normalize(CollectionFieldType.EMAIL, " Anna@Acme.COM ")).isEqualTo("anna@acme.com");
    }

    @Test
    void phone_keeps_digits_and_leading_plus_and_turns_00_into_plus() {
        assertThat(Normalizers.normalize(CollectionFieldType.PHONE, "+43 (660) 123-4567")).isEqualTo("+436601234567");
        assertThat(Normalizers.normalize(CollectionFieldType.PHONE, "0043 660 1234567")).isEqualTo("+436601234567");
    }

    @Test
    void date_is_compared_as_iso_date() {
        assertThat(Normalizers.normalize(CollectionFieldType.DATE, "2026-10-09")).isEqualTo("2026-10-09");
    }

    @Test
    void datetime_with_different_offsets_is_the_same_instant() {
        assertThat(Normalizers.normalize(CollectionFieldType.DATETIME, "2026-10-09T09:00:00+02:00"))
                .isEqualTo(Normalizers.normalize(CollectionFieldType.DATETIME, "2026-10-09T07:00:00Z"));
    }

    @Test
    void money_compares_amount_to_cents_and_currency_case_insensitively() {
        assertThat(Normalizers.normalize(CollectionFieldType.MONEY, Map.of("amount", 12.5, "currency", "eur")))
                .isEqualTo(Normalizers.normalize(CollectionFieldType.MONEY, Map.of("amount", "12.50", "currency", "EUR")));
    }

    @Test
    void number_ignores_trailing_zeros() {
        assertThat(Normalizers.normalize(CollectionFieldType.NUMBER, 3.0)).isEqualTo(Normalizers.normalize(CollectionFieldType.NUMBER, "3"));
    }

    @Test
    void enum_and_status_are_case_insensitive() {
        assertThat(Normalizers.normalize(CollectionFieldType.ENUM, "High")).isEqualTo("high");
        assertThat(Normalizers.normalize(CollectionFieldType.STATUS, " Open")).isEqualTo("open");
    }

    @Test
    void unparseable_values_fall_back_to_the_trimmed_text_instead_of_failing() {
        assertThat(Normalizers.normalize(CollectionFieldType.DATE, " next week ")).isEqualTo("next week");
        assertThat(Normalizers.normalize(CollectionFieldType.MONEY, "twelve euros")).isEqualTo("twelve euros");
    }

    @Test
    void null_stays_null() {
        assertThat(Normalizers.normalize(CollectionFieldType.TEXT, null)).isNull();
    }
}

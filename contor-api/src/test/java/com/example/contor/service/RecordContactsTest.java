package com.example.contor.service;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.RecordDto;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RecordContactsTest {

    private static CollectionFieldDto field(String slug, CollectionFieldType type) {
        return new CollectionFieldDto(1L, slug, slug, type, new LinkedHashMap<>(), false, false, 1);
    }

    private static CollectionDto collection(CollectionFieldDto... fields) {
        return new CollectionDto().id(1L).slug("people").fields(List.of(fields));
    }

    private static RecordDto record(Map<String, Object> values) {
        return new RecordDto(5L, 1L, new HashMap<>(values), new HashMap<>(), OffsetDateTime.parse("2026-10-01T00:00:00Z"), "AI");
    }

    @Test
    void collects_addresses_and_domains_from_the_record() {
        var contacts = RecordContacts.of(
                collection(field("email", CollectionFieldType.EMAIL), field("private_email", CollectionFieldType.EMAIL),
                        field("website", CollectionFieldType.URL), field("domain", CollectionFieldType.TEXT),
                        field("name", CollectionFieldType.TEXT)),
                record(Map.of("email", "Anna@Acme.com", "private_email", "anna@gmail.com", "website", "https://www.acme.com/about",
                        "domain", " Acme.DE ", "name", "anna@not-an-email-field.com")));

        assertThat(contacts.addresses()).containsExactly("anna@acme.com", "anna@gmail.com");
        assertThat(contacts.domains()).containsExactly("acme.com", "acme.de");
    }

    @Test
    void ignores_empty_values_and_broken_urls() {
        var contacts = RecordContacts.of(
                collection(field("email", CollectionFieldType.EMAIL), field("website", CollectionFieldType.URL)),
                record(Map.of("email", "", "website", "not a url")));

        assertThat(contacts.addresses()).isEmpty();
        assertThat(contacts.domains()).isEmpty();
    }

    @Test
    void archived_fields_do_not_count() {
        CollectionFieldDto archived = field("email", CollectionFieldType.EMAIL).archivedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        assertThat(RecordContacts.of(collection(archived), record(Map.of("email", "anna@acme.com"))).addresses()).isEmpty();
    }
}

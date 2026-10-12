package com.example.contor.service;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.RecordDto;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * How a record can show up in mail: the addresses of its EMAIL fields, and its domains (a field named {@code domain},
 * hosts of URL fields without {@code www.}). Lower case, without duplicates, in field order.
 */
public record RecordContacts(List<String> addresses, List<String> domains) {

    private static final String DOMAIN_FIELD = "domain";

    public static RecordContacts of(CollectionDto collection, RecordDto record) {
        Set<String> addresses = new LinkedHashSet<>();
        Set<String> domains = new LinkedHashSet<>();
        for (CollectionFieldDto field : collection.getFields() != null ? collection.getFields() : List.<CollectionFieldDto>of()) {
            Object value = record.getValues() != null ? record.getValues().get(field.getSlug()) : null;
            if (field.getArchivedAt() != null || !(value instanceof String text) || text.isBlank()) {
                continue;
            }
            String normalized = text.strip().toLowerCase(Locale.ROOT);
            if (field.getType() == CollectionFieldType.EMAIL) {
                addresses.add(normalized);
            } else if (field.getType() == CollectionFieldType.URL) {
                hostOf(normalized).ifPresent(domains::add);
            } else if (DOMAIN_FIELD.equals(field.getSlug())) {
                domains.add(normalized);
            }
        }
        return new RecordContacts(List.copyOf(addresses), List.copyOf(domains));
    }

    public boolean isEmpty() {
        return addresses.isEmpty() && domains.isEmpty();
    }

    private static Optional<String> hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? Optional.empty() : Optional.of(host.startsWith("www.") ? host.substring(4) : host);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

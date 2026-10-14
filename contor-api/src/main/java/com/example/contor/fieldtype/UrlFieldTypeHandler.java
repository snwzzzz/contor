package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** Absolute http(s) URL (no javascript:, data:, file: ...), at most 2048 characters. */
@Component
public class UrlFieldTypeHandler extends StringFieldTypeHandler {

    private static final int MAX_LENGTH = 2048;
    private static final Set<String> SCHEMES = Set.of("http", "https");
    private static final String MESSAGE = "must be an http(s) URL";

    @Override
    public FieldType fieldType() {
        return FieldType.URL;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof String text) || text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(MESSAGE);
        }
        try {
            URI uri = new URI(text);
            if (uri.getScheme() == null || !SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getHost() == null) {
                throw new IllegalArgumentException(MESSAGE);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}

package com.example.contor.domain;

import java.util.regex.Pattern;

public record Username(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");

    public Username {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("username cannot be null or empty");
        if (!PATTERN.matcher(value).matches())
            throw new IllegalArgumentException("username is not valid");
    }
}

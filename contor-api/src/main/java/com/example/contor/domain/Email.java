package com.example.contor.domain;

import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public Email {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("email cannot be null or empty");
        if (value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches())
            throw new IllegalArgumentException("email is not valid");
    }
}

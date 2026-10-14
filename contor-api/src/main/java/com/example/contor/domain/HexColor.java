package com.example.contor.domain;

import java.util.regex.Pattern;

public record HexColor(String value) {

    private static final Pattern PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public HexColor {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("color cannot be null or empty");
        if (!PATTERN.matcher(value).matches())
            throw new IllegalArgumentException("color is not valid");
    }
}

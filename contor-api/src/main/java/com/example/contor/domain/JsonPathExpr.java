package com.example.contor.domain;

import java.util.regex.Pattern;

/**
 * Where to find something in an external API's JSON answer, in a small JSONPath subset: {@code $}, then member names
 * ({@code .orders}), indexes ({@code [0]}) and every element ({@code [*]}). No filters, scripts or recursion, so a
 * path from a model answer can only select, never run anything.
 */
public record JsonPathExpr(String value) {

    private static final int MAX_LENGTH = 200;
    private static final Pattern PATTERN = Pattern.compile("^\\$(?:\\.[A-Za-z_][A-Za-z0-9_-]*|\\[(?:\\*|\\d{1,6})])*$");

    public JsonPathExpr {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("JSON path cannot be null or empty");
        if (value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches())
            throw new IllegalArgumentException("JSON path is not valid");
    }
}

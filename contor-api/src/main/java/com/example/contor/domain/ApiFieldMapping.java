package com.example.contor.domain;

/**
 * One field of an {@link ExternalApiMapping}: the collection field {@code fieldSlug} gets the value at {@code path}
 * (relative to the item, e.g. {@code $.totals.gross}). Both are validated ({@link Slug}, {@link JsonPathExpr}).
 */
public record ApiFieldMapping(String fieldSlug, String path) {

    public ApiFieldMapping {
        new Slug(fieldSlug);
        new JsonPathExpr(path);
    }
}

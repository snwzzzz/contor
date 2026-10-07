package com.example.contor.persistence.dynamic;

import com.example.contor.domain.Slug;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identifiers of generated tables and their constraints. Inputs are always derived from validated {@link Slug}s;
 * jOOQ quotes every identifier on top of that.
 */
public final class DynamicNames {

    public static final String TABLE_PREFIX = "c_";
    /** PostgreSQL truncates identifiers longer than this, which could make two constraint names collide. */
    private static final int MAX_IDENTIFIER_LENGTH = 63;
    private static final int HASH_LENGTH = 8;

    private DynamicNames() {
    }

    public static String tableName(Slug slug) {
        return TABLE_PREFIX + slug.value();
    }

    /** {@code <prefix>_<table>_<column>}, shortened with a hash suffix when it would exceed 63 characters. */
    public static String constraintName(String prefix, String table, String column) {
        String name = prefix + "_" + table + "_" + column;
        if (name.length() <= MAX_IDENTIFIER_LENGTH) {
            return name;
        }
        return name.substring(0, MAX_IDENTIFIER_LENGTH - HASH_LENGTH - 1) + "_" + hash(name);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

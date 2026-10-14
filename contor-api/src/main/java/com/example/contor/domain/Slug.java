package com.example.contor.domain;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Machine name for collections, fields, labels and relation types. Generated table and column
 * names are derived only from slugs, so this is the identifier-safety boundary for dynamic SQL.
 */
public record Slug(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,40}$");

    /** PostgreSQL reserved key words, keywords that cannot be column names, and system column names. */
    private static final Set<String> RESERVED = Set.of(
            "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "asymmetric", "authorization",
            "binary", "both", "case", "cast", "check", "collate", "collation", "column", "concurrently",
            "constraint", "create", "cross", "current_catalog", "current_date", "current_role",
            "current_schema", "current_time", "current_timestamp", "current_user", "default", "deferrable",
            "desc", "distinct", "do", "else", "end", "except", "false", "fetch", "for", "foreign", "freeze",
            "from", "full", "grant", "group", "having", "ilike", "in", "initially", "inner", "intersect",
            "into", "is", "isnull", "join", "lateral", "leading", "left", "like", "limit", "localtime",
            "localtimestamp", "natural", "not", "notnull", "null", "offset", "on", "only", "or", "order",
            "outer", "overlaps", "placing", "primary", "references", "returning", "right", "select",
            "session_user", "similar", "some", "symmetric", "system_user", "table", "tablesample", "then",
            "to", "trailing", "true", "union", "unique", "user", "using", "variadic", "verbose", "when",
            "where", "window", "with",
            "oid", "tableoid", "xmin", "xmax", "cmin", "cmax", "ctid");

    public Slug {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("slug cannot be null or empty");
        if (!PATTERN.matcher(value).matches())
            throw new IllegalArgumentException("slug is not valid");
        if (RESERVED.contains(value))
            throw new IllegalArgumentException("slug is a reserved word: " + value);
    }
}

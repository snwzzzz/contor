package com.example.contor.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Path of an external API endpoint below its {@link BaseUrl}: starts with one {@code /}, RFC 3986 path characters and
 * percent escapes only, no {@code .} or {@code ..} segments (also escaped), no query or fragment (the query is stored
 * separately). A confirmed path is all the model can make contor call.
 */
public record ApiPath(String value) {

    private static final int MAX_LENGTH = 1000;
    private static final Pattern PATTERN = Pattern.compile("^/(?:[A-Za-z0-9\\-._~!$&'()*+,;=:@/]|%[0-9A-Fa-f]{2})*$");

    public ApiPath {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("path cannot be null or empty");
        if (value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches() || value.startsWith("//"))
            throw new IllegalArgumentException("path is not valid");
        for (String segment : value.toLowerCase(Locale.ROOT).replace("%2e", ".").split("/", -1)) {
            if (segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("path cannot contain . or .. segments");
        }
    }
}

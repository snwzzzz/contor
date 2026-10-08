package com.example.contor.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /** Microsoft consumer mail domains: outlook.*, hotmail.*, live.* (incl. variants like .co.uk) and msn.com. */
    private static final Pattern OUTLOOK_DOMAIN = Pattern.compile("^((outlook|hotmail|live)(\\.[a-z]{2,3}){1,2}|msn\\.com)$");

    public Email {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("email cannot be null or empty");
        if (value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches())
            throw new IllegalArgumentException("email is not valid");
    }

    public boolean isOutlook() {
        return OUTLOOK_DOMAIN.matcher(value.substring(value.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT)).matches();
    }
}

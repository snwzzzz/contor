package com.example.contor.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Public DNS name of a mail sender's domain (lower case, at least two labels, no IPs, ports or paths). */
public record SenderDomain(String value) {

    private static final int MAX_LENGTH = 253;
    private static final Pattern PATTERN = Pattern.compile("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    public SenderDomain {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("domain cannot be null or empty");
        value = value.toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches())
            throw new IllegalArgumentException("domain is not valid");
    }

    /** Domain part of an email address, if it is a valid domain. */
    public static Optional<SenderDomain> ofAddress(String address) {
        if (address == null) return Optional.empty();
        int at = address.lastIndexOf('@');
        return at < 0 ? Optional.empty() : parse(address.substring(at + 1));
    }

    public static Optional<SenderDomain> parse(String value) {
        try {
            return Optional.of(new SenderDomain(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The last two labels (news.mail.microsoft.com -> microsoft.com), if this is a subdomain. */
    public Optional<SenderDomain> parent() {
        String[] labels = value.split("\\.");
        return labels.length > 2
                ? Optional.of(new SenderDomain(labels[labels.length - 2] + "." + labels[labels.length - 1]))
                : Optional.empty();
    }
}

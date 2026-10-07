package com.example.contor.security;

/**
 * Use for anything personal or secret that ends up in a log line. Passwords, tokens and email bodies
 * are never logged in clear text; email addresses only masked.
 */
public final class LogMasker {

    private static final String REDACTED = "[REDACTED]";

    private LogMasker() {
    }

    /** {@code john.doe@example.com} → {@code j***@e***.com}. */
    public static String email(String address) {
        if (address == null) return "null";
        int at = address.indexOf('@');
        int dot = address.lastIndexOf('.');
        if (at < 1 || dot < at + 2) return "***";
        return address.charAt(0) + "***@" + address.charAt(at + 1) + "***" + address.substring(dot);
    }

    /** Tokens, passwords, keys: never even partially. */
    public static String secret(String value) {
        return value == null ? "null" : REDACTED;
    }

    /** Free text such as email bodies: only its length. */
    public static String text(String value) {
        return value == null ? "null" : "[" + value.length() + " chars]";
    }
}

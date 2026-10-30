package com.example.contor.domain;

/** Methods contor sends to an external API (read-only, ADR 0012): POST only for endpoints the user marked as a read query. */
public enum ApiHttpMethod {
    GET, POST
}

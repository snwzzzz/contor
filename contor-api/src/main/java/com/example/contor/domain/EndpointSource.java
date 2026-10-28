package com.example.contor.domain;

/** Who added an external API endpoint: the user, the parsed documentation (OpenAPI), or the model reading the docs. */
public enum EndpointSource {
    USER, DOCS, MODEL
}

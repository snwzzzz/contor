package com.example.contor.domain;

/** One raw email header. Stored as a JSON list because header names may repeat (e.g. Received). */
public record EmailHeader(String name, String value) {
}

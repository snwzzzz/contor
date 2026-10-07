package com.example.contor.domain;

/** Recipient of an email as stored in the {@code to_recipients}/{@code cc_recipients} JSON columns. */
public record EmailRecipient(String name, String address) {
}

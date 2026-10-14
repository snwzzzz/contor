package com.example.contor.domain;

/** Mail backend a mailbox is synced from. */
public enum MailProvider {
    MICROSOFT_GRAPH,
    /** The dev-only fictional demo mailbox (business dataset): stored once, never synced. */
    DEMO
}

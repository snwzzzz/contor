package com.example.contor.domain;

/** Mail backend a mailbox is synced from; each synced one has a MailOAuthClient and a MailboxClient bean. */
public enum MailProvider {
    MICROSOFT_GRAPH,
    GMAIL,
    /** The dev-only fictional demo mailbox (business dataset): stored once, never synced. */
    DEMO;

    /** Whether the mailbox lives at the provider and is synced from there (everything but the demo mailbox). */
    public boolean synced() {
        return this != DEMO;
    }
}

package com.example.contor.domain;

/** Which parts of an email a text search looks at. */
public enum SearchField {
    /** Subject, sender, recipients and body. */
    ALL,
    SUBJECT,
    /** Sender name/address and the to/cc recipients. */
    PEOPLE,
    /** Body and preview text. */
    CONTENT
}

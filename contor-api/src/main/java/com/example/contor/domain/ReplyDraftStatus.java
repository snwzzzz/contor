package com.example.contor.domain;

/** Lifecycle of a kept reply draft. Later: IN_OUTLOOK (written to Outlook's Drafts folder), SENT. */
public enum ReplyDraftStatus {
    READY, DISCARDED
}

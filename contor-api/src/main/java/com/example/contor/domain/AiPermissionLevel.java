package com.example.contor.domain;

/** What the AI may do in one area: never, propose for review, or apply directly. */
public enum AiPermissionLevel {
    OFF, SUGGEST, AUTO;

    public boolean allowed() {
        return this != OFF;
    }
}

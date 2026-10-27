package com.example.contor.domain;

import java.util.Set;

/** Lifecycle of a chat answer: queued, looking up the sources, writing, then DONE or FAILED. Questions are DONE. */
public enum ChatMessageState {
    PENDING, RETRIEVING, WRITING, DONE, FAILED;

    /** Still being answered: no new question in the conversation until it is done. */
    public static final Set<ChatMessageState> ACTIVE = Set.of(PENDING, RETRIEVING, WRITING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}

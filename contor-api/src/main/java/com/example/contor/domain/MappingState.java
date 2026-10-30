package com.example.contor.domain;

/** An endpoint-to-collection mapping: PROPOSED until the user approves it, ACTIVE while polls apply it, OFF when switched off. */
public enum MappingState {
    PROPOSED, ACTIVE, OFF
}

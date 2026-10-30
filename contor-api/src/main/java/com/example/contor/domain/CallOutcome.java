package com.example.contor.domain;

/** Result of one external API call: OK (new answer), UNCHANGED (same content hash), FAILED. */
public enum CallOutcome {
    OK, UNCHANGED, FAILED
}

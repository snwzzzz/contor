package com.example.contor.domain;

/** Audited actions. Stored as text in {@code audit_log.action} (no CHECK: old entries keep their names when this grows). */
public enum AuditAction {
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    /** Passwordless sign-in as the dev account (dev profile only). */
    AUTH_DEV_LOGIN,
    AUTH_LOCKOUT,
    AUTH_LOGOUT,
    AUTH_PASSWORD_CHANGED,
    AUTH_TOTP_ENABLED,
    AUTH_TOTP_DISABLED,
    SETUP_COMPLETED,
    COLLECTION_CREATED,
    COLLECTION_UPDATED,
    COLLECTION_FIELD_ADDED,
    COLLECTION_FIELD_ARCHIVED,
    PROPOSAL_APPROVED,
    PROPOSAL_REJECTED,
    /** Rejected because a proposal it depends on was rejected. */
    PROPOSAL_CASCADE_REJECTED,
    /** Applied directly by the AI because the area's permission is AUTO. */
    PROPOSAL_AUTO_APPLIED,
    AI_SETTINGS_CHANGED,
    AI_ANALYSIS_STARTED,
    AI_ANALYSIS_CANCELLED
}

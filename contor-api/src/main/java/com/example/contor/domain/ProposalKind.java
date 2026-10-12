package com.example.contor.domain;

/** What a proposal would do when applied. Each kind is applied by one ProposalApplier bean. */
public enum ProposalKind {
    CREATE_RECORD, UPDATE_RECORD, CREATE_RELATION, SET_LABEL, ARCHIVE_EMAIL, DRAFT_REPLY, CREATE_COLLECTION, ADD_FIELD, CREATE_RULE, CREATE_LABEL,
    /** A pattern across several emails with follow-up to-dos or appointments (InsightService). */
    INSIGHT
}

package com.example.contor.exception;

import com.example.contor.domain.ProposalKind;

/** No {@code ProposalApplier} exists for the kind yet. */
public class ProposalNotApplicableException extends ConflictException {
    public ProposalNotApplicableException(ProposalKind kind) {
        super("Proposals of kind " + kind + " cannot be applied yet");
    }
}

package com.example.contor.exception;

public class ProposalDependenciesPendingException extends ConflictException {
    public ProposalDependenciesPendingException(Long id) {
        super("Proposal " + id + " depends on proposals that have not been applied yet");
    }
}

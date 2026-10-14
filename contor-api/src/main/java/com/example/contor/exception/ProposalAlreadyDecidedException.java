package com.example.contor.exception;

public class ProposalAlreadyDecidedException extends ConflictException {
    public ProposalAlreadyDecidedException(Long id) {
        super("Proposal " + id + " has already been decided");
    }
}

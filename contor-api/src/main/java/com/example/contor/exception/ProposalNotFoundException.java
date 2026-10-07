package com.example.contor.exception;

public class ProposalNotFoundException extends ResourceNotFoundException {
    public ProposalNotFoundException(Long id) {
        super("Proposal not found with id: " + id);
    }
}

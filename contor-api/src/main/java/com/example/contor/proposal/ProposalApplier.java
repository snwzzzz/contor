package com.example.contor.proposal;

import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;

/**
 * Applies one {@link ProposalKind}: turns the proposal's {@code finalPayload} into real changes.
 * <p>
 * Plug-in point: to make a new kind applicable, add one {@code @Component} implementing this interface.
 * {@link ProposalApplierRegistry} picks it up automatically; kinds without an applier cannot be approved.
 * <p>
 * Runs inside the decision transaction: throwing (e.g. a validation error) rolls everything back and leaves the
 * proposal pending. Malformed payloads throw {@link IllegalArgumentException}.
 */
public interface ProposalApplier {

    ProposalKind kind();

    void apply(Proposal proposal);

    /** Whether only the OWNER may approve proposals of this kind (schema changes, settings). */
    default boolean requiresOwner() {
        return false;
    }
}

package com.example.contor.proposal;

import com.example.contor.domain.ProposalKind;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Collects every {@link ProposalApplier} bean, keyed by proposal kind. */
@Component
public class ProposalApplierRegistry {

    private final Map<ProposalKind, ProposalApplier> appliers = new EnumMap<>(ProposalKind.class);

    public ProposalApplierRegistry(List<ProposalApplier> appliers) {
        for (ProposalApplier applier : appliers) {
            ProposalApplier previous = this.appliers.putIfAbsent(applier.kind(), applier);
            if (previous != null) {
                throw new IllegalStateException("Two ProposalAppliers for " + applier.kind() + ": "
                        + previous.getClass().getSimpleName() + ", " + applier.getClass().getSimpleName());
            }
        }
    }

    public Optional<ProposalApplier> applier(ProposalKind kind) {
        return Optional.ofNullable(appliers.get(kind));
    }
}

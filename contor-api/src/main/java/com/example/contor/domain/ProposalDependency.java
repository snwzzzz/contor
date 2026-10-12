package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

/** "{@code proposalId} can only be decided once {@code dependsOnProposalId} is applied". Composite key, no auditing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "\"proposal_dependency\"")
public class ProposalDependency {

    @EmbeddedId
    private ProposalDependencyId id;

    @Embeddable
    public record ProposalDependencyId(@Column(name = "proposal_id") Long proposalId,
                                       @Column(name = "depends_on_proposal_id") Long dependsOnProposalId) {}
}

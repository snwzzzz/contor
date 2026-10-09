package com.example.contor.persistence;

import com.example.contor.domain.ProposalDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProposalDependencyRepository extends JpaRepository<ProposalDependency, ProposalDependency.ProposalDependencyId> {

    /** What the proposal depends on. */
    List<ProposalDependency> findById_ProposalId(Long proposalId);

    /** The proposals that depend on it. */
    List<ProposalDependency> findById_DependsOnProposalId(Long dependsOnProposalId);
}

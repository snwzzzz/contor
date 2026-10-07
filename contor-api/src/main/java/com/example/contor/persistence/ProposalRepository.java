package com.example.contor.persistence;

import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Proposal.ProposalId> {

    List<Proposal> findByStatusOrderByCreatedAtDesc(ProposalStatus status);

    long countByStatus(ProposalStatus status);
}

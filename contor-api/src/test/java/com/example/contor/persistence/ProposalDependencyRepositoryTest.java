package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalDependency;
import com.example.contor.domain.ProposalKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class ProposalDependencyRepositoryTest {

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProposalDependencyRepository proposalDependencyRepository;

    private Proposal saved() {
        return proposalRepository.saveAndFlush(Proposal.builder().kind(ProposalKind.CREATE_RECORD).payload(Map.of("collectionId", 1)).build());
    }

    private static ProposalDependency edge(Proposal proposal, Proposal dependsOn) {
        return new ProposalDependency(new ProposalDependency.ProposalDependencyId(proposal.getId().id(), dependsOn.getId().id()));
    }

    @Test
    void finds_the_dependencies_and_the_dependents_of_a_proposal() {
        var person = saved();
        var organization = saved();
        var relation = saved();
        proposalDependencyRepository.saveAllAndFlush(List.of(edge(relation, person), edge(relation, organization)));

        assertThat(proposalDependencyRepository.findById_ProposalId(relation.getId().id()))
                .extracting(dependency -> dependency.getId().dependsOnProposalId())
                .containsExactlyInAnyOrder(person.getId().id(), organization.getId().id());
        assertThat(proposalDependencyRepository.findById_DependsOnProposalId(person.getId().id()))
                .extracting(dependency -> dependency.getId().proposalId()).containsExactly(relation.getId().id());
        assertThat(proposalDependencyRepository.findById_ProposalId(person.getId().id())).isEmpty();
    }

    @Test
    void deleting_a_proposal_removes_its_edges() {
        var person = saved();
        var relation = saved();
        proposalDependencyRepository.saveAndFlush(edge(relation, person));

        proposalRepository.delete(person);
        proposalRepository.flush();
        proposalDependencyRepository.flush();

        assertThat(proposalDependencyRepository.count()).isZero();
    }

    @Test
    void a_proposal_cannot_depend_on_itself() {
        var proposal = saved();

        assertThatThrownBy(() -> proposalDependencyRepository.saveAndFlush(edge(proposal, proposal)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stores_the_result_and_the_auto_apply_flag() {
        var proposal = proposalRepository.saveAndFlush(Proposal.builder().kind(ProposalKind.CREATE_RELATION)
                .payload(Map.of()).result(Map.of("collectionId", 1, "recordId", 9)).autoApplyWhenReady(true).build());

        var reloaded = proposalRepository.findById(proposal.getId()).orElseThrow();

        assertThat(reloaded.getResult()).containsEntry("recordId", 9);
        assertThat(reloaded.isAutoApplyWhenReady()).isTrue();
        assertThat(saved().isAutoApplyWhenReady()).isFalse();
    }
}

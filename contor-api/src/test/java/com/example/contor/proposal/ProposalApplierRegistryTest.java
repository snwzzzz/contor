package com.example.contor.proposal;

import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProposalApplierRegistryTest {

    private record FakeApplier(ProposalKind kind) implements ProposalApplier {
        @Override
        public void apply(Proposal proposal) {
        }
    }

    @Test
    void can_resolve_applier_by_kind() {
        var create = new FakeApplier(ProposalKind.CREATE_RECORD);
        var registry = new ProposalApplierRegistry(List.of(create, new FakeApplier(ProposalKind.UPDATE_RECORD)));

        assertThat(registry.applier(ProposalKind.CREATE_RECORD)).containsSame(create);
    }

    @Test
    void kinds_without_applier_resolve_to_empty() {
        var registry = new ProposalApplierRegistry(List.of(new FakeApplier(ProposalKind.CREATE_RECORD)));

        assertThat(registry.applier(ProposalKind.CREATE_COLLECTION)).isEmpty();
    }

    @Test
    void rejects_two_appliers_for_one_kind() {
        assertThatThrownBy(() -> new ProposalApplierRegistry(List.of(
                new FakeApplier(ProposalKind.CREATE_RECORD), new FakeApplier(ProposalKind.CREATE_RECORD))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATE_RECORD");
    }
}

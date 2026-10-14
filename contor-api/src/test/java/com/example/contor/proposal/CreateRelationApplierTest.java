package com.example.contor.proposal;

import com.example.contor.api.model.NewRelationCommand;
import com.example.contor.api.model.RelationDto;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.service.RelationService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CreateRelationApplierTest {

    @Mock
    private RelationService relationService;

    @Mock
    private ProposalRepository proposalRepository;

    @InjectMocks
    private CreateRelationApplier applier;

    private static Proposal relation(Map<String, Object> payload, EmailMessage source) {
        return Proposal.builder().kind(ProposalKind.CREATE_RELATION).payload(payload).finalPayload(payload)
                .sourceEmail(source).confidence(new BigDecimal("0.8")).build();
    }

    private static Map<String, Object> record(long collectionId, long recordId) {
        return Map.of("collectionId", collectionId, "recordId", recordId);
    }

    private static Map<String, Object> payload(Object from, Object to) {
        return Map.of("relationTypeId", 3, "from", from, "to", to);
    }

    private void dependencyApplied(long proposalId, Map<String, Object> result) {
        when(proposalRepository.findById(new Proposal.ProposalId(proposalId)))
                .thenReturn(Optional.of(Proposal.builder().id(new Proposal.ProposalId(proposalId)).result(result).build()));
    }

    @Test
    void applies_create_relation_proposals_without_needing_the_owner() {
        assertThat(applier.kind()).isEqualTo(ProposalKind.CREATE_RELATION);
        assertThat(applier.requiresOwner()).isFalse();
    }

    @Test
    void creates_the_relation_as_ai_with_the_source_email_and_confidence() {
        var email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build();
        when(relationService.createAiRelation(any(), any(), any())).thenReturn(new RelationDto().id(40L));

        var result = applier.apply(relation(payload(record(1, 10), record(2, 20)), email));

        ArgumentCaptor<NewRelationCommand> command = ArgumentCaptor.forClass(NewRelationCommand.class);
        verify(relationService).createAiRelation(command.capture(), eq(email), eq(new BigDecimal("0.8")));
        assertThat(command.getValue().getRelationTypeId()).isEqualTo(3L);
        assertThat(command.getValue().getFromCollectionId()).isEqualTo(1L);
        assertThat(command.getValue().getFromRecordId()).isEqualTo(10L);
        assertThat(command.getValue().getToCollectionId()).isEqualTo(2L);
        assertThat(command.getValue().getToRecordId()).isEqualTo(20L);
        assertThat(result).containsEntry("relationId", 40L).containsEntry("created", true);
    }

    @Test
    void resolves_endpoints_that_name_a_proposal_from_the_result_of_that_proposal() {
        dependencyApplied(50, Map.of("collectionId", 1L, "recordId", 11L));
        dependencyApplied(51, Map.of("collectionId", 2L, "recordId", 22L));
        when(relationService.createAiRelation(any(), any(), any())).thenReturn(new RelationDto().id(41L));

        applier.apply(relation(payload(Map.of("proposalId", 50), Map.of("proposalId", 51)), null));

        ArgumentCaptor<NewRelationCommand> command = ArgumentCaptor.forClass(NewRelationCommand.class);
        verify(relationService).createAiRelation(command.capture(), eq(null), any());
        assertThat(command.getValue().getFromRecordId()).isEqualTo(11L);
        assertThat(command.getValue().getToRecordId()).isEqualTo(22L);
    }

    @Test
    void an_identical_existing_relation_is_a_no_op_that_returns_the_existing_one() {
        when(relationService.createAiRelation(any(), any(), any())).thenThrow(new DuplicateResourceException("already related"));
        when(relationService.listRelations(1L, 10L)).thenReturn(List.of(
                new RelationDto().id(7L).relationTypeId(4L).fromCollectionId(1L).fromRecordId(10L).toCollectionId(2L).toRecordId(20L),
                new RelationDto().id(8L).relationTypeId(3L).fromCollectionId(1L).fromRecordId(10L).toCollectionId(2L).toRecordId(20L)));

        var result = applier.apply(relation(payload(record(1, 10), record(2, 20)), null));

        assertThat(result).containsEntry("relationId", 8L).containsEntry("created", false);
    }

    @Test
    void a_dependency_that_produced_no_record_is_rejected() {
        dependencyApplied(50, Map.of());

        assertThatThrownBy(() -> applier.apply(relation(payload(Map.of("proposalId", 50), record(2, 20)), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("proposal 50");
        verify(relationService, never()).createAiRelation(any(), any(), any());
    }

    @Test
    void an_unknown_dependency_is_rejected() {
        when(proposalRepository.findById(new Proposal.ProposalId(99L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applier.apply(relation(payload(Map.of("proposalId", 99), record(2, 20)), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("proposal 99");
    }

    @Test
    void rejects_malformed_payloads() {
        Map<String, Object> noType = new HashMap<>(payload(record(1, 10), record(2, 20)));
        noType.remove("relationTypeId");
        assertThatThrownBy(() -> applier.apply(relation(noType, null))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payload.relationTypeId is required");
        assertThatThrownBy(() -> applier.apply(relation(payload("p1", record(2, 20)), null))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payload.from must be an object");
        assertThatThrownBy(() -> applier.apply(relation(payload(Map.of("collectionId", 1), record(2, 20)), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload.from needs a proposalId or a collectionId and recordId");
        assertThatThrownBy(() -> applier.apply(relation(payload(Map.of("ref", "p1"), record(2, 20)), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload.from needs a proposalId or a collectionId and recordId");
        verify(relationService, never()).createAiRelation(any(), any(), any());
    }
}

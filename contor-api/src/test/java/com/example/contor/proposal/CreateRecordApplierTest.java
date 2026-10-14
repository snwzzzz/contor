package com.example.contor.proposal;

import com.example.contor.api.model.RecordDto;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.service.RecordService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CreateRecordApplierTest {

    @Mock
    private RecordService recordService;

    @Mock
    private RecordSourceRepository recordSourceRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @InjectMocks
    private CreateRecordApplier applier;

    private static Proposal proposal(Map<String, Object> payload, EmailMessage source) {
        return Proposal.builder().kind(ProposalKind.CREATE_RECORD).payload(payload).finalPayload(payload).sourceEmail(source).build();
    }

    @Test
    void applies_create_record_proposals() {
        assertThat(applier.kind()).isEqualTo(ProposalKind.CREATE_RECORD);
        assertThat(applier.requiresOwner()).isFalse();
    }

    @Test
    void creates_the_record_as_ai_and_links_the_source_email() {
        var email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build();
        var collection = Collection.builder().id(new Collection.CollectionId(1L)).build();
        when(recordService.createRecord(eq(1L), any(), eq(ActorType.AI))).thenReturn(new RecordDto().id(5L).collectionId(1L));
        when(collectionRepository.getReferenceById(new Collection.CollectionId(1L))).thenReturn(collection);

        applier.apply(proposal(Map.of("collectionId", 1, "title", "Parcel", "values", Map.of("tracking", "T-1"), "extra", Map.of("note", "x")), email));

        verify(recordService).createRecord(eq(1L), argThat(command -> "Parcel".equals(command.getTitle())
                && "T-1".equals(command.getValues().get("tracking")) && "x".equals(command.getExtra().get("note"))), eq(ActorType.AI));
        verify(recordSourceRepository).saveAndFlush(argThat((RecordSource source) -> source.getCollection() == collection
                && source.getRecordId() == 5L && source.getEmail() == email && source.getRole() == RecordSourceRole.CREATED_FROM));
    }

    @Test
    void does_not_link_a_source_without_email() {
        when(recordService.createRecord(eq(1L), any(), eq(ActorType.AI))).thenReturn(new RecordDto().id(5L).collectionId(1L));

        applier.apply(proposal(Map.of("collectionId", 1, "values", Map.of()), null));

        verifyNoInteractions(recordSourceRepository);
    }

    @Test
    void rejects_malformed_payloads() {
        assertThatThrownBy(() -> applier.apply(proposal(Map.of("values", Map.of()), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload.collectionId is required");
        assertThatThrownBy(() -> applier.apply(proposal(Map.of("collectionId", "one"), null)))
                .hasMessage("payload.collectionId is required");
        var badValues = new HashMap<String, Object>();
        badValues.put("collectionId", 1);
        badValues.put("values", "tracking=T-1");
        assertThatThrownBy(() -> applier.apply(proposal(badValues, null))).hasMessage("payload.values must be an object");
        verifyNoInteractions(recordService);
    }

    @Test
    void returns_the_created_record_as_result() {
        when(recordService.createRecord(eq(1L), any(), eq(ActorType.AI))).thenReturn(new RecordDto().id(5L).collectionId(1L));

        var result = applier.apply(proposal(Map.of("collectionId", 1, "values", Map.of()), null));

        assertThat(result).containsEntry("collectionId", 1L).containsEntry("recordId", 5L);
    }
}

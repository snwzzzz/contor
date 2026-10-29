package com.example.contor.proposal;

import com.example.contor.api.model.RecordDto;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.ExternalApiItem;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UpdateRecordApplierTest {

    @Mock
    private RecordService recordService;

    @Mock
    private RecordSourceRepository recordSourceRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @InjectMocks
    private UpdateRecordApplier applier;

    private static Proposal proposal(Map<String, Object> payload, EmailMessage source) {
        return Proposal.builder().kind(ProposalKind.UPDATE_RECORD).payload(payload).finalPayload(payload).sourceEmail(source).build();
    }

    @Test
    void applies_update_record_proposals() {
        assertThat(applier.kind()).isEqualTo(ProposalKind.UPDATE_RECORD);
    }

    @Test
    void updates_the_record_and_links_the_source_email() {
        var email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build();
        var collection = Collection.builder().id(new Collection.CollectionId(1L)).build();
        when(recordService.updateRecord(eq(1L), eq(5L), argThat(command -> "SHIPPED".equals(command.getValues().get("state")))))
                .thenReturn(new RecordDto().id(5L).collectionId(1L));
        when(collectionRepository.getReferenceById(new Collection.CollectionId(1L))).thenReturn(collection);

        applier.apply(proposal(Map.of("collectionId", 1, "recordId", 5, "values", Map.of("state", "SHIPPED")), email));

        verify(recordSourceRepository).saveAndFlush(argThat((RecordSource source) -> source.getRecordId() == 5L
                && source.getRole() == RecordSourceRole.UPDATED_FROM));
    }

    @Test
    void requires_a_record_id() {
        assertThatThrownBy(() -> applier.apply(proposal(Map.of("collectionId", 1, "values", Map.of()), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload.recordId is required");
        verifyNoInteractions(recordService);
    }

    @Test
    void returns_the_updated_record_as_result() {
        when(recordService.updateRecord(eq(1L), eq(5L), any())).thenReturn(new RecordDto().id(5L).collectionId(1L));

        var result = applier.apply(proposal(Map.of("collectionId", 1, "recordId", 5, "values", Map.of()), null));

        assertThat(result).containsEntry("collectionId", 1L).containsEntry("recordId", 5L);
    }

    @Test
    void an_update_from_an_api_item_links_the_item_once() {
        var collection = Collection.builder().id(new Collection.CollectionId(1L)).build();
        var item = ExternalApiItem.builder().id(9L).itemKey("SO-1").build();
        when(recordService.updateRecord(eq(1L), eq(5L), any())).thenReturn(new RecordDto().id(5L).collectionId(1L));
        when(collectionRepository.getReferenceById(new Collection.CollectionId(1L))).thenReturn(collection);
        Map<String, Object> payload = Map.of("collectionId", 1, "recordId", 5, "values", Map.of("state", "SHIPPED"));
        var proposal = Proposal.builder().kind(ProposalKind.UPDATE_RECORD).payload(payload).finalPayload(payload).sourceApiItem(item).build();
        when(recordSourceRepository.existsByCollection_IdAndRecordIdAndApiItem_IdAndRole(new Collection.CollectionId(1L), 5L, 9L,
                RecordSourceRole.UPDATED_FROM)).thenReturn(false, true);

        applier.apply(proposal);
        applier.apply(proposal);

        verify(recordSourceRepository, times(1)).saveAndFlush(argThat((RecordSource source) -> source.getApiItem() == item
                && source.getRole() == RecordSourceRole.UPDATED_FROM));
    }
}

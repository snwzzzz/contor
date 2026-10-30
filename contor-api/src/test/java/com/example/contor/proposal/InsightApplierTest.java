package com.example.contor.proposal;

import com.example.contor.api.model.RecordDto;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.service.RecordService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InsightApplierTest {

    @Mock
    private RecordService recordService;

    @Mock
    private RecordSourceRepository recordSourceRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private ExternalApiItemRepository externalApiItemRepository;

    @InjectMocks
    private InsightApplier applier;

    private static Proposal insight(Map<String, Object> payload) {
        return Proposal.builder().kind(ProposalKind.INSIGHT).payload(payload).finalPayload(payload).build();
    }

    @Test
    void applies_insights_without_needing_the_owner() {
        assertThat(applier.kind()).isEqualTo(ProposalKind.INSIGHT);
        assertThat(applier.requiresOwner()).isFalse();
    }

    @Test
    void creates_the_kept_follow_ups_as_ai_records_linked_to_the_evidence() {
        var first = EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build();
        var second = EmailMessage.builder().id(new EmailMessage.EmailMessageId(9L)).build();
        var tasks = Collection.builder().id(new Collection.CollectionId(4L)).build();
        when(emailMessageRepository.findAllById(List.of(new EmailMessage.EmailMessageId(8L), new EmailMessage.EmailMessageId(9L))))
                .thenReturn(List.of(first, second));
        when(collectionRepository.getReferenceById(new Collection.CollectionId(4L))).thenReturn(tasks);
        when(recordService.createRecord(eq(4L), any(), eq(ActorType.AI))).thenReturn(new RecordDto().id(31L).collectionId(4L));

        Map<String, Object> result = applier.apply(insight(Map.of("insightKey", "k", "title", "No answer from Anna",
                "evidenceEmailIds", List.of(8, 9),
                "followUps", List.of(Map.of("collectionId", 4, "title", "Follow up with Anna", "values", Map.of("due_date", "2026-10-14"))))));

        verify(recordService).createRecord(eq(4L), argThat(command -> "Follow up with Anna".equals(command.getTitle())
                && "2026-10-14".equals(command.getValues().get("due_date"))), eq(ActorType.AI));
        verify(recordSourceRepository, times(2)).saveAndFlush(argThat((RecordSource source) -> source.getRecordId() == 31L
                && source.getCollection() == tasks && source.getRole() == RecordSourceRole.CREATED_FROM));
        assertThat(result).containsEntry("records", List.of(Map.of("collectionId", 4L, "recordId", 31L)));
    }

    @Test
    void an_insight_without_follow_ups_left_is_just_acknowledged() {
        Map<String, Object> result = applier.apply(insight(Map.of("insightKey", "k", "title", "t", "evidenceEmailIds", List.of(),
                "followUps", List.of())));

        verifyNoInteractions(recordService, recordSourceRepository);
        assertThat(result).containsEntry("records", List.of());
    }

    @Test
    void a_follow_up_without_a_collection_is_rejected() {
        assertThatThrownBy(() -> applier.apply(insight(Map.of("insightKey", "k", "title", "t",
                "followUps", List.of(Map.of("title", "no collection"))))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void follow_ups_must_be_a_list() {
        assertThatThrownBy(() -> applier.apply(insight(Map.of("insightKey", "k", "title", "t", "followUps", "task"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void links_the_follow_ups_to_the_app_items_of_the_evidence_too() {
        var item = ExternalApiItem.builder().id(9L).itemKey("J-7").build();
        when(externalApiItemRepository.findAllById(List.of(9L))).thenReturn(List.of(item));
        when(recordService.createRecord(eq(4L), any(), eq(ActorType.AI))).thenReturn(new RecordDto().id(5L).collectionId(4L));
        when(collectionRepository.getReferenceById(new Collection.CollectionId(4L))).thenReturn(Collection.builder().id(new Collection.CollectionId(4L)).build());

        applier.apply(insight(Map.of("evidenceEmailIds", List.of(), "evidenceApiItemIds", List.of(9),
                "followUps", List.of(Map.of("collectionId", 4, "title", "Check the posting", "values", Map.of())))));

        verify(recordSourceRepository).saveAndFlush(argThat((RecordSource source) -> source.getApiItem() == item && source.getEmail() == null
                && source.getRecordId() == 5L && source.getRole() == RecordSourceRole.CREATED_FROM));
    }
}

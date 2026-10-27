package com.example.contor.mapper;

import com.example.contor.api.model.ChatConversationDto;
import com.example.contor.api.model.ChatMentionType;
import com.example.contor.api.model.ChatMessageDto;
import com.example.contor.api.model.ChatMessageRole;
import com.example.contor.api.model.ChatMessageStatus;
import com.example.contor.api.model.CitationKind;
import com.example.contor.api.model.ClaimCheck;
import com.example.contor.api.model.ReportBlockType;
import com.example.contor.api.model.SourceState;
import com.example.contor.domain.ChatBlock;
import com.example.contor.domain.ChatCitation;
import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMention;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import com.example.contor.domain.ChatRole;
import com.example.contor.domain.Citation;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ReportBlockKind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ChatMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private static final ReportMapper.SourceLookup CURRENT = new ReportMapper.SourceLookup() {
        @Override
        public SourceState state(Citation citation) {
            return SourceState.CURRENT;
        }

        @Override
        public String recordTitle(Long collectionId, Long recordId) {
            return "Anna Kessler";
        }
    };

    private final Collection people = Collection.builder().id(new Collection.CollectionId(3L)).name("People").fields(new ArrayList<>()).build();

    @Test
    void maps_a_conversation_with_its_messages_in_order() {
        var conversation = conversation();
        message(conversation, 7L, 0, ChatRole.USER, "Who is Anna?", ChatMessageState.DONE);
        message(conversation, 8L, 1, ChatRole.ASSISTANT, null, ChatMessageState.PENDING);

        ChatConversationDto dto = ChatMapper.toDto(conversation, CURRENT);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getTitle()).isEqualTo("Who is Anna?");
        assertThat(dto.getCreatedAt()).isEqualTo(MapperSupport.toOffset(NOW));
        assertThat(dto.getMessages()).extracting(ChatMessageDto::getId).containsExactly(7L, 8L);
        assertThat(dto.getMessages()).extracting(ChatMessageDto::getRole).containsExactly(ChatMessageRole.USER, ChatMessageRole.ASSISTANT);
        assertThat(dto.getMessages()).extracting(ChatMessageDto::getStatus).containsExactly(ChatMessageStatus.DONE, ChatMessageStatus.PENDING);
        assertThat(dto.getMessages().getFirst().getText()).isEqualTo("Who is Anna?");
        assertThat(dto.getMessages().get(1).getBlocks()).isEmpty();
    }

    @Test
    void maps_an_answer_with_blocks_sources_and_mentions() {
        var conversation = conversation();
        var answer = message(conversation, 8L, 1, ChatRole.ASSISTANT, "Anna works at Kessler GmbH.", ChatMessageState.DONE);
        answer.setBlocks(List.of(
                new ChatBlock(ReportBlockKind.PARAGRAPH, null, List.of(new ChatBlock.Item("Anna works at Kessler GmbH.", List.of(1))), null),
                new ChatBlock(ReportBlockKind.TABLE, List.of("Order", "Total"), null,
                        List.of(List.of(new ChatBlock.Item("SO-1", List.of()), new ChatBlock.Item("110 EUR", List.of(2)))))));
        answer.setMentions(List.of(ChatMention.record(3L, 4711L, "Anna Kessler", "People"), ChatMention.email(9L, "Delay notice")));
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(2).type(CitationType.RECORD).collection(people).recordId(4711L).build());
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(1).type(CitationType.RECORD).collection(people).recordId(4711L).build());

        ChatMessageDto dto = ChatMapper.toDto(conversation, CURRENT).getMessages().getFirst();

        assertThat(dto.getBlocks()).extracting(block -> block.getType()).containsExactly(ReportBlockType.PARAGRAPH, ReportBlockType.TABLE);
        assertThat(dto.getBlocks()).extracting(block -> block.getId()).containsExactly(0L, 1L);
        var sentence = dto.getBlocks().getFirst().getItems().getFirst();
        assertThat(sentence.getText()).isEqualTo("Anna works at Kessler GmbH.");
        assertThat(sentence.getCheck()).isEqualTo(ClaimCheck.SUPPORTED);
        assertThat(sentence.getMarkers()).containsExactly(1);
        var table = dto.getBlocks().get(1);
        assertThat(table.getColumns()).containsExactly("Order", "Total");
        assertThat(table.getItems()).isEmpty();
        assertThat(table.getRows().getFirst()).extracting(cell -> cell.getText()).containsExactly("SO-1", "110 EUR");
        assertThat(table.getRows().getFirst().get(1).getMarkers()).containsExactly(2);
        assertThat(dto.getSources()).extracting(source -> source.getMarker()).containsExactly(1, 2);
        assertThat(dto.getSources().getFirst().getKind()).isEqualTo(CitationKind.RECORD);
        assertThat(dto.getSources().getFirst().getRecordTitle()).isEqualTo("Anna Kessler");
        var person = dto.getMentions().getFirst();
        assertThat(person.getType()).isEqualTo(ChatMentionType.RECORD);
        assertThat(person.getCollectionId()).isEqualTo(3L);
        assertThat(person.getRecordId()).isEqualTo(4711L);
        assertThat(person.getLabel()).isEqualTo("Anna Kessler");
        assertThat(person.getCollectionName()).isEqualTo("People");
        var email = dto.getMentions().get(1);
        assertThat(email.getType()).isEqualTo(ChatMentionType.EMAIL);
        assertThat(email.getEmailId()).isEqualTo(9L);
        assertThat(email.getRecordId()).isNull();
    }

    @Test
    void maps_a_failed_answer_with_its_error() {
        var conversation = conversation();
        var answer = message(conversation, 8L, 1, ChatRole.ASSISTANT, null, ChatMessageState.FAILED);
        answer.setError("The model did not answer.");

        ChatMessageDto dto = ChatMapper.toDto(conversation, CURRENT).getMessages().getFirst();

        assertThat(dto.getStatus()).isEqualTo(ChatMessageStatus.FAILED);
        assertThat(dto.getError()).isEqualTo("The model did not answer.");
        assertThat(dto.getText()).isNull();
    }

    @Test
    void maps_a_summary() {
        var conversation = conversation();
        conversation.setLastModified(NOW.plusSeconds(60));

        var dto = ChatMapper.toSummary(conversation, true);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getTitle()).isEqualTo("Who is Anna?");
        assertThat(dto.getAnswering()).isTrue();
        assertThat(dto.getLastModified()).isEqualTo(MapperSupport.toOffset(NOW.plusSeconds(60)));
    }

    @Test
    void null_in_null_out() {
        assertThat(ChatMapper.toDto(null, CURRENT)).isNull();
        assertThat(ChatMapper.toSummary(null, false)).isNull();
    }

    private static ChatConversation conversation() {
        var conversation = ChatConversation.builder().id(new ChatConversation.ChatConversationId(5L)).title("Who is Anna?").build();
        conversation.setCreatedAt(NOW);
        return conversation;
    }

    private static ChatMessage message(ChatConversation conversation, Long id, int position, ChatRole role, String content, ChatMessageState state) {
        var message = ChatMessage.builder().id(new ChatMessage.ChatMessageId(id)).conversation(conversation).position(position)
                .role(role).content(content).state(state).build();
        message.setCreatedAt(NOW);
        conversation.getMessages().add(message);
        return message;
    }
}

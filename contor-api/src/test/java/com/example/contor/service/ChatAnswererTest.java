package com.example.contor.service;

import com.example.contor.ai.ChatPlan;
import com.example.contor.ai.EmailParagraphs;
import com.example.contor.ai.EvidencePack;
import com.example.contor.ai.OllamaException;
import com.example.contor.ai.ReportDraft;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.ChatBlock;
import com.example.contor.domain.ChatCitation;
import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMention;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import com.example.contor.domain.ChatRole;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.persistence.ChatMessageRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatAnswererTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private EmailMessageRepository emailMessageRepository;
    @Mock
    private ChatRetrievalService chatRetrievalService;
    @Mock
    private ChatModelService chatModelService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ChatAnswerer chatAnswerer;
    private ChatConversation conversation;
    private ChatMessage answer;
    private final Collection people = Collection.builder().id(new Collection.CollectionId(3L)).name("People").fields(new ArrayList<>()).build();

    /** Anna (R1) with her company (F1), and her email (E1) with one paragraph (P1.1). */
    private final EvidencePack pack = EvidencePack.builder()
            .record(new EvidencePack.RecordInput(3L, "People", 4711L, "Anna Kessler", false, List.of(
                    new EvidencePack.FieldInput("company", "Company", FieldType.TEXT, "Kessler GmbH"))))
            .email(new EvidencePack.EmailInput(9L, LocalDate.of(2026, 9, 4), "Anna Kessler", "anna@kessler.example", false, "Delay notice",
                    EmailParagraphs.split("The delivery is late.", null)))
            .build("Who is Anna?", EvidencePack.DEFAULT_BUDGET);

    @BeforeEach
    void setUp() {
        chatAnswerer = new ChatAnswerer(chatMessageRepository, collectionRepository, emailMessageRepository, chatRetrievalService,
                chatModelService, transactionManager, Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC));
        conversation = ChatConversation.builder().id(new ChatConversation.ChatConversationId(5L))
                .owner(AppUser.builder().id(OWNER).build()).title("Who is Anna?").build();
        message(0, ChatRole.USER, "Who is Anna?", ChatMessageState.DONE);
        answer = message(1, ChatRole.ASSISTANT, null, ChatMessageState.PENDING);
        when(chatMessageRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRepository.getReferenceById(people.getId())).thenReturn(people);
        when(collectionRepository.findById(people.getId())).thenReturn(Optional.of(people));
        when(emailMessageRepository.getReferenceById(new EmailMessage.EmailMessageId(9L)))
                .thenReturn(EmailMessage.builder().id(new EmailMessage.EmailMessageId(9L)).build());
        when(chatRetrievalService.catalog()).thenReturn("- people: People");
        when(chatRetrievalService.collectionSlugs()).thenReturn(List.of("people"));
        when(chatModelService.plan(anyString(), anyList(), anyString(), anyList())).thenReturn(new ChatPlan(List.of("Anna"), List.of()));
        when(chatRetrievalService.findRecords(any(), anyList())).thenReturn(List.of(new RecordRef(3L, 4711L)));
        when(chatRetrievalService.collect(anyList(), eq(List.of("Anna")), anyString(), eq(OWNER))).thenReturn(pack);
        when(chatRetrievalService.mailAccess(OWNER)).thenReturn(true);
        when(chatModelService.model()).thenReturn("qwen3.5:9b");
    }

    @Test
    void answer_WritesTheAnswerWithItsSourcesAndMentions() {
        draft("Anna works at Kessler GmbH [F1].\\n\\n- She wrote that the delivery is late [P1.1].");

        chatAnswerer.answer(answer.getId().id());

        assertThat(answer.getState()).isEqualTo(ChatMessageState.DONE);
        assertThat(answer.getError()).isNull();
        assertThat(answer.getBlocks()).extracting(ChatBlock::kind).containsExactly(ReportBlockKind.PARAGRAPH, ReportBlockKind.BULLETS);
        assertThat(answer.getBlocks().getFirst().items().getFirst().text()).isEqualTo("Anna works at Kessler GmbH.");
        assertThat(answer.getBlocks().getFirst().items().getFirst().markers()).containsExactly(1);
        assertThat(answer.getBlocks().get(1).items().getFirst().markers()).containsExactly(2);
        assertThat(answer.getCitations()).extracting(ChatCitation::getMarker).containsExactly(1, 2);
        var field = answer.getCitations().getFirst();
        assertThat(field.getType()).isEqualTo(CitationType.FIELD);
        assertThat(field.getRecordId()).isEqualTo(4711L);
        assertThat(field.getFieldSlug()).isEqualTo("company");
        assertThat(field.getValueSnapshot()).containsEntry("value", "Kessler GmbH");
        var paragraph = answer.getCitations().get(1);
        assertThat(paragraph.getType()).isEqualTo(CitationType.EMAIL_PARAGRAPH);
        assertThat(paragraph.getEmail().getId().id()).isEqualTo(9L);
        assertThat(paragraph.getQuote()).isEqualTo("The delivery is late.");
        assertThat(answer.getMentions()).containsExactly(ChatMention.record(3L, 4711L, "Anna Kessler", "People"),
                ChatMention.email(9L, "Delay notice"));
        assertThat(answer.getContent()).contains("Anna works at Kessler GmbH.", "She wrote that the delivery is late.");
        assertThat(answer.getPromptVersion()).isEqualTo(ChatModelService.ANSWER_PROMPT_VERSION);
        assertThat(answer.getModel()).isEqualTo("qwen3.5:9b");
        assertThat(answer.getPlan()).containsKeys("searches", "records", "emails");
        assertThat(conversation.getFocus()).as("the next question may refer to her").containsExactly(new RecordRef(3L, 4711L));
        assertThat(conversation.getLastModified()).isNotNull();
    }

    @Test
    void answer_ShowsHeadingsAsBoldLinesAndKeepsTables() {
        draft("Anna is a customer.\\n\\n## Orders\\n\\n| Order | Total |\\n|---|---|\\n| SO-1 | 110 EUR [F1] |");

        chatAnswerer.answer(answer.getId().id());

        assertThat(answer.getBlocks()).extracting(ChatBlock::kind)
                .containsExactly(ReportBlockKind.PARAGRAPH, ReportBlockKind.PARAGRAPH, ReportBlockKind.TABLE);
        assertThat(answer.getBlocks().get(1).items().getFirst().text()).isEqualTo("**Orders**");
        var table = answer.getBlocks().get(2);
        assertThat(table.columns()).containsExactly("Order", "Total");
        assertThat(table.rows().getFirst().get(1).markers()).containsExactly(1);
    }

    @Test
    void answer_WithoutCitationsMentionsTheRecordsFound() {
        draft("Anna is someone you know.");

        chatAnswerer.answer(answer.getId().id());

        assertThat(answer.getCitations()).isEmpty();
        assertThat(answer.getMentions()).containsExactly(ChatMention.record(3L, 4711L, "Anna Kessler", "People"));
    }

    @Test
    void answer_SendsTheEarlierTurnsAndTheFocusAlong() {
        answer.setState(ChatMessageState.DONE);
        answer.setContent("Anna works at Kessler GmbH.");
        conversation.setFocus(new ArrayList<>(List.of(new RecordRef(3L, 4711L))));
        message(2, ChatRole.USER, "Where does she live?", ChatMessageState.DONE);
        var next = message(3, ChatRole.ASSISTANT, null, ChatMessageState.PENDING);
        when(chatMessageRepository.findById(next.getId())).thenReturn(Optional.of(next));
        draft("In Berlin [F1].");

        chatAnswerer.answer(next.getId().id());

        List<String> history = List.of("You: Who is Anna?", "Assistant: Anna works at Kessler GmbH.");
        verify(chatModelService).plan("Where does she live?", history, "- people: People", List.of("people"));
        verify(chatRetrievalService).findRecords(new ChatPlan(List.of("Anna"), List.of()), List.of(new RecordRef(3L, 4711L)));
        verify(chatModelService).answer(pack, "Where does she live?", history, true);
        assertThat(next.getState()).isEqualTo(ChatMessageState.DONE);
    }

    @Test
    void answer_ModelDownFailsWithAReason() {
        when(chatModelService.plan(anyString(), anyList(), anyString(), anyList())).thenThrow(new OllamaException("connection refused"));

        chatAnswerer.answer(answer.getId().id());

        assertThat(answer.getState()).isEqualTo(ChatMessageState.FAILED);
        assertThat(answer.getError()).isEqualTo("The AI model did not answer. Is Ollama running?");
        assertThat(answer.getBlocks()).isEmpty();
    }

    @Test
    void answer_UnreadableAnswerFailsWithItsReason() {
        when(chatModelService.answer(any(), anyString(), anyList(), anyBoolean())).thenThrow(new IllegalStateException("The model's answer could not be read"));

        chatAnswerer.answer(answer.getId().id());

        assertThat(answer.getState()).isEqualTo(ChatMessageState.FAILED);
        assertThat(answer.getError()).isEqualTo("The model's answer could not be read");
    }

    @Test
    void answer_DeletedMessageIgnored() {
        when(chatMessageRepository.findById(new ChatMessage.ChatMessageId(99L))).thenReturn(Optional.empty());

        chatAnswerer.answer(99L);

        verify(chatModelService, never()).plan(anyString(), anyList(), anyString(), anyList());
    }

    @Test
    void answer_FinishedAnswerIgnored() {
        answer.setState(ChatMessageState.FAILED);

        chatAnswerer.answer(answer.getId().id());

        assertThat(answer.getState()).isEqualTo(ChatMessageState.FAILED);
        verify(chatModelService, never()).plan(anyString(), anyList(), anyString(), anyList());
    }

    private void draft(String document) {
        when(chatModelService.answer(any(), anyString(), anyList(), anyBoolean()))
                .thenReturn(ReportDraft.parse(JSON.readTree("{\"document\": \"" + document + "\"}")));
    }

    private ChatMessage message(int position, ChatRole role, String content, ChatMessageState state) {
        var message = ChatMessage.builder().id(new ChatMessage.ChatMessageId(10L + position)).conversation(conversation)
                .position(position).role(role).content(content).state(state).build();
        conversation.getMessages().add(message);
        return message;
    }
}

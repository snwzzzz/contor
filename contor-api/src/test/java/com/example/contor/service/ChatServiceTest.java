package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.api.model.ChatConversationDto;
import com.example.contor.api.model.ChatMessageStatus;
import com.example.contor.api.model.NewChatMessageCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordRefDto;
import com.example.contor.api.model.UpdateChatConversationCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import com.example.contor.domain.ChatRole;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.ChatBusyException;
import com.example.contor.exception.ChatConversationNotFoundException;
import com.example.contor.exception.ChatMessageNotFoundException;
import com.example.contor.exception.ChatRetryException;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.ChatConversationRepository;
import com.example.contor.persistence.ChatMessageRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);

    @Mock
    private ChatConversationRepository chatConversationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RelationRepository relationRepository;
    @Mock
    private RecordService recordService;
    @Mock
    private ChatAnswerer chatAnswerer;
    @Mock
    private AiModelService aiModelService;
    @Mock
    private AuditService auditService;

    private final List<Runnable> queued = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        Executor executor = queued::add;
        chatService = new ChatService(chatConversationRepository, chatMessageRepository, appUserRepository, relationRepository, recordService,
                chatAnswerer, aiModelService, auditService, new AiExecutors(executor, mock(Executor.class)));
        when(appUserRepository.getReferenceById(OWNER)).thenReturn(AppUser.builder().id(OWNER).build());
        when(aiModelService.isReady()).thenReturn(true);
        when(chatConversationRepository.saveAndFlush(any(ChatConversation.class))).thenAnswer(inv -> {
            ChatConversation conversation = inv.getArgument(0);
            if (conversation.getId() == null) {
                conversation.setId(new ChatConversation.ChatConversationId(5L));
                conversation.setCreatedAt(Instant.parse("2026-10-26T10:00:00Z"));
            }
            for (ChatMessage message : conversation.getMessages()) {
                if (message.getId() == null) {
                    message.setId(new ChatMessage.ChatMessageId(ids.incrementAndGet()));
                    message.setCreatedAt(Instant.parse("2026-10-26T10:00:00Z"));
                }
            }
            return conversation;
        });
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage message = inv.getArgument(0);
            if (message.getId() == null) {
                message.setId(new ChatMessage.ChatMessageId(ids.incrementAndGet()));
                message.setCreatedAt(Instant.parse("2026-10-26T10:00:00Z"));
            }
            return message;
        });
        when(recordService.getRecord(3L, 4711L)).thenReturn(new RecordDto().id(4711L).title("Anna Kessler").values(Map.of()));
        var user = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createConversation_StoresTheQuestionAndAPendingAnswerAndQueuesIt() {
        ChatConversationDto dto = chatService.createConversation(new NewChatMessageCommand("  Who is Anna Kessler?  "));

        ArgumentCaptor<ChatConversation> saved = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository).saveAndFlush(saved.capture());
        var conversation = saved.getValue();
        assertThat(conversation.getOwner().getId()).isEqualTo(OWNER);
        assertThat(conversation.getTitle()).isEqualTo("Who is Anna Kessler?");
        assertThat(conversation.getMessages()).extracting(ChatMessage::getRole).containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
        assertThat(conversation.getMessages()).extracting(ChatMessage::getPosition).containsExactly(0, 1);
        assertThat(conversation.getMessages()).extracting(ChatMessage::getState).containsExactly(ChatMessageState.DONE, ChatMessageState.PENDING);
        assertThat(conversation.getMessages().getFirst().getContent()).isEqualTo("Who is Anna Kessler?");
        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getMessages()).extracting(message -> message.getStatus()).containsExactly(ChatMessageStatus.DONE, ChatMessageStatus.PENDING);
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(chatAnswerer).answer(conversation.getMessages().get(1).getId().id());
    }

    @Test
    void createConversation_LongQuestionGivesAShortTitle() {
        String question = "Tell me " + "everything about this ".repeat(10) + "customer";

        chatService.createConversation(new NewChatMessageCommand(question));

        ArgumentCaptor<ChatConversation> saved = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTitle()).hasSizeLessThanOrEqualTo(ChatService.MAX_TITLE).endsWith("…");
        assertThat(saved.getValue().getMessages().getFirst().getContent()).isEqualTo(question);
    }

    @Test
    void createConversation_PinnedRecordsBecomeTheFocus() {
        chatService.createConversation(new NewChatMessageCommand("What is open with her?").recordRefs(List.of(new RecordRefDto(3L, 4711L))));

        ArgumentCaptor<ChatConversation> saved = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFocus()).containsExactly(new RecordRef(3L, 4711L));
    }

    @Test
    void createConversation_UnknownPinnedRecordRejected() {
        when(recordService.getRecord(3L, 9L)).thenThrow(new RecordNotFoundException(3L, 9L));

        assertThatThrownBy(() -> chatService.createConversation(new NewChatMessageCommand("Who?").recordRefs(List.of(new RecordRefDto(3L, 9L)))))
                .isInstanceOf(RecordNotFoundException.class);
        verify(chatConversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createConversation_EmptyQuestionRejected() {
        assertThatThrownBy(() -> chatService.createConversation(new NewChatMessageCommand("   ")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(chatConversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createConversation_TooLongQuestionRejected() {
        assertThatThrownBy(() -> chatService.createConversation(new NewChatMessageCommand("x".repeat(ChatService.MAX_QUESTION + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createConversation_AiNotReadyRejected() {
        when(aiModelService.isReady()).thenReturn(false);

        assertThatThrownBy(() -> chatService.createConversation(new NewChatMessageCommand("Who is Anna?")))
                .isInstanceOf(AiUnavailableException.class);
        verify(chatConversationRepository, never()).saveAndFlush(any());
        assertThat(queued).isEmpty();
    }

    @Test
    void askQuestion_AddsTheNextTurnAndQueuesIt() {
        var conversation = owned(done());

        ChatConversationDto dto = chatService.askQuestion(5L, new NewChatMessageCommand("And where does she work?"));

        assertThat(conversation.getMessages()).extracting(ChatMessage::getPosition).containsExactly(0, 1, 2, 3);
        assertThat(conversation.getMessages().get(2).getContent()).isEqualTo("And where does she work?");
        assertThat(conversation.getMessages().get(3).getState()).isEqualTo(ChatMessageState.PENDING);
        assertThat(conversation.getLastModified()).isNotNull();
        verify(chatMessageRepository).saveAndFlush(conversation.getMessages().get(2));
        verify(chatMessageRepository).saveAndFlush(conversation.getMessages().get(3));
        assertThat(dto.getMessages()).hasSize(4);
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(chatAnswerer).answer(conversation.getMessages().get(3).getId().id());
    }

    @Test
    void askQuestion_KeepsTheFocusWithoutPinnedRecords() {
        var conversation = owned(done());
        conversation.setFocus(new ArrayList<>(List.of(new RecordRef(3L, 4711L))));

        chatService.askQuestion(5L, new NewChatMessageCommand("And where does she work?"));

        assertThat(conversation.getFocus()).containsExactly(new RecordRef(3L, 4711L));
    }

    @Test
    void askQuestion_WhileAnsweringRejected() {
        var conversation = owned(done());
        conversation.getMessages().get(1).setState(ChatMessageState.WRITING);

        assertThatThrownBy(() -> chatService.askQuestion(5L, new NewChatMessageCommand("And?")))
                .isInstanceOf(ChatBusyException.class);
        verify(chatConversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void askQuestion_FullConversationRejected() {
        var conversation = owned(done());
        for (int position = 2; position < ChatService.MAX_MESSAGES; position++) {
            conversation.getMessages().add(message(conversation, position, ChatRole.USER, ChatMessageState.DONE));
        }

        assertThatThrownBy(() -> chatService.askQuestion(5L, new NewChatMessageCommand("And?")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void askQuestion_OtherUsersConversationNotFound() {
        when(chatConversationRepository.findByIdAndOwner_Id(new ChatConversation.ChatConversationId(5L), OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.askQuestion(5L, new NewChatMessageCommand("And?")))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }

    @Test
    void findAll_ListsTheUsersConversationsWithTheirAnsweringState() {
        var idle = done();
        var answering = done();
        answering.setId(new ChatConversation.ChatConversationId(6L));
        answering.getMessages().get(1).setState(ChatMessageState.PENDING);
        when(chatConversationRepository.findByOwner_IdOrderByLastModifiedDesc(OWNER)).thenReturn(List.of(answering, idle));

        var summaries = chatService.findAll();

        assertThat(summaries).extracting(summary -> summary.getId()).containsExactly(6L, 5L);
        assertThat(summaries).extracting(summary -> summary.getAnswering()).containsExactly(true, false);
    }

    @Test
    void findById_ReturnsTheConversation() {
        owned(done());

        assertThat(chatService.findById(5L).getMessages()).hasSize(2);
    }

    @Test
    void findById_NotFound() {
        assertThatThrownBy(() -> chatService.findById(5L)).isInstanceOf(ChatConversationNotFoundException.class);
    }

    @Test
    void updateConversation_RenamesIt() {
        var conversation = owned(done());

        chatService.updateConversation(5L, new UpdateChatConversationCommand().title("  Anna  "));

        assertThat(conversation.getTitle()).isEqualTo("Anna");
        verify(chatConversationRepository).saveAndFlush(conversation);
    }

    @Test
    void updateConversation_NullTitleKeepsIt() {
        var conversation = owned(done());

        chatService.updateConversation(5L, new UpdateChatConversationCommand());

        assertThat(conversation.getTitle()).isEqualTo("Who is Anna?");
    }

    @Test
    void updateConversation_BlankTitleRejected() {
        owned(done());

        assertThatThrownBy(() -> chatService.updateConversation(5L, new UpdateChatConversationCommand().title(" ")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(chatConversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteConversation_DeletesAndAudits() {
        var conversation = owned(done());

        chatService.deleteConversation(5L);

        verify(chatConversationRepository).delete(conversation);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.CHAT_CONVERSATION_DELETED);
        assertThat(event.getValue().entityId()).isEqualTo(5L);
    }

    @Test
    void deleteConversation_NotFound() {
        assertThatThrownBy(() -> chatService.deleteConversation(5L)).isInstanceOf(ChatConversationNotFoundException.class);
        verify(chatConversationRepository, never()).delete(any());
    }

    @Test
    void retryAnswer_QueuesAFailedLastAnswerAgain() {
        var conversation = owned(done());
        var answer = conversation.getMessages().get(1);
        answer.setState(ChatMessageState.FAILED);
        answer.setError("The model did not answer.");

        chatService.retryAnswer(5L, answer.getId().id());

        assertThat(answer.getState()).isEqualTo(ChatMessageState.PENDING);
        assertThat(answer.getError()).isNull();
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(chatAnswerer).answer(answer.getId().id());
    }

    @Test
    void retryAnswer_DoneAnswerRejected() {
        var conversation = owned(done());

        assertThatThrownBy(() -> chatService.retryAnswer(5L, conversation.getMessages().get(1).getId().id()))
                .isInstanceOf(ChatRetryException.class);
        assertThat(queued).isEmpty();
    }

    @Test
    void retryAnswer_UnknownMessageNotFound() {
        owned(done());

        assertThatThrownBy(() -> chatService.retryAnswer(5L, 999L)).isInstanceOf(ChatMessageNotFoundException.class);
    }

    @Test
    void failInterruptedAnswers_MarksAnswersStillBeingWrittenAsFailed() {
        var conversation = done();
        var answer = conversation.getMessages().get(1);
        answer.setState(ChatMessageState.WRITING);
        when(chatMessageRepository.findByStateIn(ChatMessageState.ACTIVE)).thenReturn(List.of(answer));

        chatService.failInterruptedAnswers();

        assertThat(answer.getState()).isEqualTo(ChatMessageState.FAILED);
        assertThat(answer.getError()).isNotBlank();
        verify(chatMessageRepository).saveAndFlush(answer);
        assertThat(queued).isEmpty();
    }

    /** A conversation of the owner with one finished turn. */
    private ChatConversation done() {
        var conversation = ChatConversation.builder().id(new ChatConversation.ChatConversationId(5L))
                .owner(AppUser.builder().id(OWNER).build()).title("Who is Anna?").build();
        conversation.setCreatedAt(Instant.parse("2026-10-26T09:00:00Z"));
        conversation.getMessages().add(message(conversation, 0, ChatRole.USER, ChatMessageState.DONE));
        conversation.getMessages().add(message(conversation, 1, ChatRole.ASSISTANT, ChatMessageState.DONE));
        return conversation;
    }

    private ChatConversation owned(ChatConversation conversation) {
        when(chatConversationRepository.findByIdAndOwner_Id(conversation.getId(), OWNER)).thenReturn(Optional.of(conversation));
        return conversation;
    }

    private ChatMessage message(ChatConversation conversation, int position, ChatRole role, ChatMessageState state) {
        var message = ChatMessage.builder().id(new ChatMessage.ChatMessageId(ids.incrementAndGet())).conversation(conversation)
                .position(position).role(role).content(role == ChatRole.USER ? "Who is Anna?" : "Anna.").state(state).build();
        message.setCreatedAt(Instant.parse("2026-10-26T09:00:00Z"));
        return message;
    }
}

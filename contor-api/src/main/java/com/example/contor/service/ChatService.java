package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.api.model.ChatConversationDto;
import com.example.contor.api.model.ChatConversationSummaryDto;
import com.example.contor.api.model.NewChatMessageCommand;
import com.example.contor.api.model.RecordRefDto;
import com.example.contor.api.model.UpdateChatConversationCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import com.example.contor.domain.ChatRole;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.ChatBusyException;
import com.example.contor.exception.ChatConversationNotFoundException;
import com.example.contor.exception.ChatMessageNotFoundException;
import com.example.contor.exception.ChatRetryException;
import com.example.contor.mapper.ChatMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.ChatConversationRepository;
import com.example.contor.persistence.ChatMessageRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The AI chat (H6): a user's private conversations. A question is stored with a PENDING answer that {@link ChatAnswerer}
 * writes on the AI thread after commit; one answer per conversation at a time. Pinned records ({@code recordRefs}) are
 * checked and become the conversation's focus. Answers still being written when the backend stops are marked FAILED
 * on the next start (the user may retry them).
 */
@RequiredArgsConstructor
@Service
public class ChatService {

    public static final int MAX_QUESTION = 2000;
    public static final int MAX_TITLE = 120;
    /** Questions and answers together; a conversation holds half as many turns. */
    public static final int MAX_MESSAGES = 200;
    static final String INTERRUPTED = "The answer was interrupted by a restart. Try again.";

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AppUserRepository appUserRepository;
    private final RelationRepository relationRepository;
    private final RecordService recordService;
    private final ChatAnswerer chatAnswerer;
    private final AiModelService aiModelService;
    private final AuditService auditService;
    private final AiExecutors aiExecutors;

    @Transactional
    public List<ChatConversationSummaryDto> findAll() {
        return chatConversationRepository.findByOwner_IdOrderByLastModifiedDesc(currentUser()).stream()
                .map(conversation -> ChatMapper.toSummary(conversation, answering(conversation)))
                .toList();
    }

    @Transactional
    public ChatConversationDto findById(Long conversationId) {
        return toDto(load(conversationId));
    }

    @Transactional
    public ChatConversationDto createConversation(NewChatMessageCommand command) {
        String question = question(command.getText());
        List<RecordRef> pinned = pinned(command.getRecordRefs());
        requireAi();
        ChatConversation conversation = ChatConversation.builder()
                .owner(appUserRepository.getReferenceById(currentUser()))
                .title(title(question))
                .focus(new ArrayList<>(pinned))
                .build();
        ChatMessage answer = addTurn(conversation, question);
        chatConversationRepository.saveAndFlush(conversation);
        queue(answer.getId().id());
        return toDto(conversation);
    }

    @Transactional
    public ChatConversationDto askQuestion(Long conversationId, NewChatMessageCommand command) {
        ChatConversation conversation = load(conversationId);
        String question = question(command.getText());
        if (answering(conversation)) {
            throw new ChatBusyException(conversationId);
        }
        if (conversation.getMessages().size() + 2 > MAX_MESSAGES) {
            throw new IllegalArgumentException("a conversation holds at most " + MAX_MESSAGES / 2 + " questions: start a new one");
        }
        List<RecordRef> pinned = pinned(command.getRecordRefs());
        requireAi();
        if (!pinned.isEmpty()) {
            conversation.setFocus(new ArrayList<>(pinned));
        }
        ChatMessage answer = addTurn(conversation, question);
        // Persisted one by one: saving the loaded conversation would merge the new messages into copies without ids.
        conversation.getMessages().stream().filter(message -> message.getId() == null).forEach(chatMessageRepository::saveAndFlush);
        conversation.setLastModified(Instant.now());
        chatConversationRepository.saveAndFlush(conversation);
        queue(answer.getId().id());
        return toDto(conversation);
    }

    @Transactional
    public ChatConversationDto retryAnswer(Long conversationId, Long messageId) {
        ChatConversation conversation = load(conversationId);
        ChatMessage answer = conversation.getMessages().stream()
                .filter(message -> message.getId().id().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new ChatMessageNotFoundException(messageId));
        if (answer != conversation.getMessages().getLast() || answer.getRole() != ChatRole.ASSISTANT
                || answer.getState() != ChatMessageState.FAILED) {
            throw new ChatRetryException();
        }
        requireAi();
        answer.setState(ChatMessageState.PENDING);
        answer.setError(null);
        conversation.setLastModified(Instant.now());
        chatConversationRepository.saveAndFlush(conversation);
        queue(messageId);
        return toDto(conversation);
    }

    @Transactional
    public ChatConversationDto updateConversation(Long conversationId, UpdateChatConversationCommand command) {
        ChatConversation conversation = load(conversationId);
        if (command.getTitle() != null) {
            String title = command.getTitle().strip();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("title: must not be empty");
            }
            if (title.length() > MAX_TITLE) {
                throw new IllegalArgumentException("title: at most " + MAX_TITLE + " characters");
            }
            conversation.setTitle(title);
        }
        chatConversationRepository.saveAndFlush(conversation);
        return toDto(conversation);
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        ChatConversation conversation = load(conversationId);
        chatConversationRepository.delete(conversation);
        auditService.record(AuditEvent.currentActor(AuditAction.CHAT_CONVERSATION_DELETED).entity("chat_conversation", conversationId));
    }

    /** No answer is queued after a restart: those still being written are failed, the user may retry them. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedAnswers() {
        for (ChatMessage answer : chatMessageRepository.findByStateIn(ChatMessageState.ACTIVE)) {
            answer.setState(ChatMessageState.FAILED);
            answer.setError(INTERRUPTED);
            chatMessageRepository.saveAndFlush(answer);
        }
    }

    /** Adds the question and its PENDING answer after the last message; returns the answer. */
    private static ChatMessage addTurn(ChatConversation conversation, String question) {
        int next = conversation.getMessages().isEmpty() ? 0 : conversation.getMessages().getLast().getPosition() + 1;
        conversation.getMessages().add(ChatMessage.builder().conversation(conversation).position(next)
                .role(ChatRole.USER).content(question).state(ChatMessageState.DONE).build());
        ChatMessage answer = ChatMessage.builder().conversation(conversation).position(next + 1)
                .role(ChatRole.ASSISTANT).state(ChatMessageState.PENDING).build();
        conversation.getMessages().add(answer);
        return answer;
    }

    private static boolean answering(ChatConversation conversation) {
        return conversation.getMessages().stream().anyMatch(message -> message.getState().isActive());
    }

    private ChatConversation load(Long conversationId) {
        return chatConversationRepository.findByIdAndOwner_Id(new ChatConversation.ChatConversationId(conversationId), currentUser())
                .orElseThrow(() -> new ChatConversationNotFoundException(conversationId));
    }

    private static String question(String text) {
        String question = text == null ? "" : text.strip();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("text: must not be empty");
        }
        if (question.length() > MAX_QUESTION) {
            throw new IllegalArgumentException("text: at most " + MAX_QUESTION + " characters");
        }
        return question;
    }

    /** The question on one line, cut at a word to {@link #MAX_TITLE} characters. */
    private static String title(String question) {
        String line = question.replaceAll("\\s+", " ");
        if (line.length() <= MAX_TITLE) {
            return line;
        }
        String cut = line.substring(0, MAX_TITLE - 1);
        int space = cut.lastIndexOf(' ');
        return (space > MAX_TITLE / 2 ? cut.substring(0, space) : cut).strip() + "…";
    }

    /** The pinned records, each checked to exist (404 otherwise), without duplicates. */
    private List<RecordRef> pinned(List<RecordRefDto> refs) {
        LinkedHashSet<RecordRef> pinned = new LinkedHashSet<>();
        for (RecordRefDto ref : Optional.ofNullable(refs).orElse(List.of())) {
            recordService.getRecord(ref.getCollectionId(), ref.getRecordId());
            pinned.add(new RecordRef(ref.getCollectionId(), ref.getRecordId()));
        }
        return List.copyOf(pinned);
    }

    private void requireAi() {
        if (!aiModelService.isReady()) {
            throw new AiUnavailableException("The AI model is not ready: start Ollama and download the models first");
        }
    }

    /** Conversations are private: the session user, never request input. */
    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId().orElseThrow(() -> new IllegalStateException("the chat needs a logged-in user")));
    }

    private ChatConversationDto toDto(ChatConversation conversation) {
        return ChatMapper.toDto(conversation, new CitationSourceLookup(recordService, relationRepository, currentUser().id()));
    }

    /** The answerer must see the committed answer, so it is queued after commit (directly without a transaction). */
    private void queue(Long messageId) {
        Runnable submit = () -> aiExecutors.analysis().execute(() -> chatAnswerer.answer(messageId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }
}

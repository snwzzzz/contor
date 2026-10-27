package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.ChatMessageDto;
import com.example.contor.api.model.ChatMessageStatus;
import com.example.contor.api.model.NewChatMessageCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.exception.ChatConversationNotFoundException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.service.AiModelService;
import com.example.contor.service.ChatAnswerer;
import com.example.contor.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The chat's turns against real JPA and Postgres: ids of new answers on a loaded conversation, the stub answer, and
 * privacy between users. Rolled back per test, so nothing is queued on the AI thread (that happens after commit).
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatAnswerer chatAnswerer;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AiModelService aiModelService;

    private AppUser owner;

    @BeforeEach
    void setUp() {
        when(aiModelService.isReady()).thenReturn(true);
        owner = appUserRepository.saveAndFlush(user("chat-owner"));
        login(owner);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void a_conversation_takes_turns_and_the_answers_are_written() {
        var created = chatService.createConversation(new NewChatMessageCommand("Who is Anna Kessler?"));
        Long conversationId = created.getId();
        chatAnswerer.answer(created.getMessages().get(1).getId());

        var asked = chatService.askQuestion(conversationId, new NewChatMessageCommand("And where does she work?"));
        Long nextAnswer = asked.getMessages().get(3).getId();
        assertThat(nextAnswer).as("the new answer has its id").isNotNull();
        chatAnswerer.answer(nextAnswer);

        var conversation = chatService.findById(conversationId);
        assertThat(conversation.getMessages()).extracting(ChatMessageDto::getText)
                .containsExactly("Who is Anna Kessler?", conversation.getMessages().get(1).getText(), "And where does she work?",
                        conversation.getMessages().get(3).getText());
        assertThat(conversation.getMessages()).extracting(ChatMessageDto::getStatus).containsOnly(ChatMessageStatus.DONE);
        assertThat(conversation.getMessages().get(3).getBlocks()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from chat_message where conversation_id = ?", Long.class, conversationId))
                .isEqualTo(4L);
        assertThat(chatService.findAll()).extracting(summary -> summary.getId()).containsExactly(conversationId);
    }

    @Test
    void another_user_cannot_see_the_conversation() {
        Long conversationId = chatService.createConversation(new NewChatMessageCommand("Who is Anna Kessler?")).getId();

        login(appUserRepository.saveAndFlush(user("chat-other")));

        assertThat(chatService.findAll()).isEmpty();
        assertThatThrownBy(() -> chatService.findById(conversationId)).isInstanceOf(ChatConversationNotFoundException.class);
        assertThatThrownBy(() -> chatService.deleteConversation(conversationId)).isInstanceOf(ChatConversationNotFoundException.class);
    }

    private static AppUser user(String username) {
        return AppUser.builder()
                .username(new Username(username))
                .email(new Email(username + "@example.com"))
                .passwordHash("$argon2id$test")
                .role(UserRole.OWNER)
                .build();
    }

    private static void login(AppUser user) {
        var principal = new AuthenticatedUser(user.getId().id(), user.getUsername().value(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}

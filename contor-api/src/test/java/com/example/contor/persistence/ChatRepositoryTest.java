package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
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
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.domain.ReportScope.RecordRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class ChatRepositoryTest {

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Collection people;
    private AppUser owner;
    private EmailMessage email;

    @BeforeEach
    void setUp() {
        people = collectionRepository.save(Fixtures.collection("people"));
        owner = appUserRepository.save(Fixtures.user("owner"));
        email = emailMessageRepository.save(Fixtures.email(mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com")), "AAMk-1"));
    }

    @Test
    void stores_a_conversation_with_its_messages_in_order() {
        var conversation = conversation(owner, "Who is Anna Kessler?");
        conversation.setFocus(List.of(new RecordRef(people.getId().id(), 4711L)));
        message(conversation, 0, ChatRole.USER, "Who is Anna Kessler?", ChatMessageState.DONE);
        var answer = message(conversation, 1, ChatRole.ASSISTANT, "Anna Kessler works at Kessler GmbH.", ChatMessageState.DONE);
        answer.setBlocks(List.of(new ChatBlock(ReportBlockKind.PARAGRAPH, null,
                List.of(new ChatBlock.Item("Anna Kessler works at Kessler GmbH.", List.of(1))), null)));
        answer.setMentions(List.of(ChatMention.record(people.getId().id(), 4711L, "Anna Kessler", "People"), ChatMention.email(email.getId().id(), "Delay notice")));
        answer.setPlan(Map.of("records", List.of(Map.of("q", "Anna Kessler"))));
        answer.setPromptVersion("chat-answer-v1");
        answer.setModel("qwen3.5:9b");
        answer.setPlanMs(1200);
        answer.setAnswerMs(8400);
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(1).type(CitationType.FIELD)
                .collection(people).recordId(4711L).fieldSlug("company").valueSnapshot(Map.of("value", "Kessler GmbH")).build());
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(2).type(CitationType.EMAIL_PARAGRAPH)
                .email(email).paragraphIndex(1).paragraphHash("ab12").segmenterVersion(1).quote("held at customs").build());

        chatConversationRepository.saveAndFlush(conversation);
        entityManager.clear();

        var reloaded = chatConversationRepository.findById(conversation.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(reloaded.getFocus()).containsExactly(new RecordRef(people.getId().id(), 4711L));
        assertThat(reloaded.getMessages()).extracting(ChatMessage::getRole).containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
        var reloadedAnswer = reloaded.getMessages().get(1);
        assertThat(reloadedAnswer.getBlocks().getFirst().items().getFirst().markers()).containsExactly(1);
        assertThat(reloadedAnswer.getMentions()).extracting(ChatMention::label).containsExactly("Anna Kessler", "Delay notice");
        assertThat(reloadedAnswer.getPlan()).containsKey("records");
        assertThat(reloadedAnswer.getPlanMs()).isEqualTo(1200);
        assertThat(reloadedAnswer.getCitations()).extracting(ChatCitation::getMarker).containsExactly(1, 2);
        assertThat(reloadedAnswer.getCitations().getFirst().getValueSnapshot()).containsEntry("value", "Kessler GmbH");
        assertThat(reloadedAnswer.getCitations().get(1).getEmail().getId()).isEqualTo(email.getId());
    }

    @Test
    void a_new_conversation_has_no_focus_and_a_new_answer_is_pending() {
        var conversation = conversation(owner, "Hi");
        var answer = ChatMessage.builder().conversation(conversation).position(0).role(ChatRole.ASSISTANT).build();
        conversation.getMessages().add(answer);

        chatConversationRepository.saveAndFlush(conversation);
        entityManager.clear();

        var reloaded = chatConversationRepository.findById(conversation.getId()).orElseThrow();
        assertThat(reloaded.getFocus()).isEmpty();
        assertThat(reloaded.getMessages().getFirst().getState()).isEqualTo(ChatMessageState.PENDING);
        assertThat(reloaded.getMessages().getFirst().getBlocks()).isEmpty();
        assertThat(reloaded.getMessages().getFirst().getMentions()).isEmpty();
    }

    @Test
    void lists_the_conversations_of_a_user_most_recent_first() {
        var other = appUserRepository.save(Fixtures.user("other"));
        var older = chatConversationRepository.saveAndFlush(conversation(owner, "First"));
        var newer = conversation(owner, "Second");
        chatConversationRepository.saveAndFlush(newer);
        jdbcTemplate.update("update chat_conversation set last_modified = last_modified + interval '60 seconds' where id = ?", newer.getId().id());
        chatConversationRepository.saveAndFlush(conversation(other, "Not mine"));
        entityManager.clear();

        assertThat(chatConversationRepository.findByOwner_IdOrderByLastModifiedDesc(owner.getId())).extracting(ChatConversation::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void finds_a_conversation_only_through_its_owner() {
        var other = appUserRepository.save(Fixtures.user("other"));
        var conversation = chatConversationRepository.saveAndFlush(conversation(owner, "Mine"));

        assertThat(chatConversationRepository.findByIdAndOwner_Id(conversation.getId(), owner.getId())).isPresent();
        assertThat(chatConversationRepository.findByIdAndOwner_Id(conversation.getId(), other.getId())).isEmpty();
    }

    @Test
    void finds_the_answers_still_being_written() {
        var conversation = conversation(owner, "Who is Anna?");
        message(conversation, 0, ChatRole.USER, "Who is Anna?", ChatMessageState.DONE);
        var writing = message(conversation, 1, ChatRole.ASSISTANT, null, ChatMessageState.WRITING);
        message(conversation, 2, ChatRole.ASSISTANT, "Failed.", ChatMessageState.FAILED);
        chatConversationRepository.saveAndFlush(conversation);

        assertThat(chatMessageRepository.findByStateIn(ChatMessageState.ACTIVE)).extracting(ChatMessage::getId)
                .containsExactly(writing.getId());
        assertThat(chatMessageRepository.existsByConversation_IdAndStateIn(conversation.getId(), ChatMessageState.ACTIVE)).isTrue();
    }

    @Test
    void lists_the_messages_of_a_conversation_in_order() {
        var conversation = conversation(owner, "Who is Anna?");
        message(conversation, 1, ChatRole.ASSISTANT, "Anna.", ChatMessageState.DONE);
        message(conversation, 0, ChatRole.USER, "Who is Anna?", ChatMessageState.DONE);
        chatConversationRepository.saveAndFlush(conversation);
        entityManager.clear();

        assertThat(chatMessageRepository.findByConversation_IdOrderByPositionAsc(conversation.getId())).extracting(ChatMessage::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void deleting_a_conversation_removes_its_messages_and_citations() {
        var conversation = conversation(owner, "Who is Anna?");
        var answer = message(conversation, 0, ChatRole.ASSISTANT, "Anna.", ChatMessageState.DONE);
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(1).type(CitationType.RECORD).collection(people).recordId(4711L).build());
        chatConversationRepository.saveAndFlush(conversation);
        entityManager.clear();

        chatConversationRepository.delete(chatConversationRepository.findById(conversation.getId()).orElseThrow());
        chatConversationRepository.flush();

        assertThat(count("chat_conversation")).isZero();
        assertThat(count("chat_message")).isZero();
        assertThat(count("chat_citation")).isZero();
    }

    @Test
    void deleting_a_user_removes_their_conversations() {
        var user = appUserRepository.save(Fixtures.user("leaving"));
        var conversation = conversation(user, "Who is Anna?");
        message(conversation, 0, ChatRole.USER, "Who is Anna?", ChatMessageState.DONE);
        chatConversationRepository.saveAndFlush(conversation);

        jdbcTemplate.update("delete from app_user where id = ?", user.getId().id());

        assertThat(count("chat_conversation")).isZero();
        assertThat(count("chat_message")).isZero();
    }

    @Test
    void deleting_an_email_keeps_the_quote_of_its_citation() {
        var conversation = conversation(owner, "Why late?");
        var answer = message(conversation, 0, ChatRole.ASSISTANT, "Customs.", ChatMessageState.DONE);
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(1).type(CitationType.EMAIL_PARAGRAPH)
                .email(email).paragraphIndex(1).paragraphHash("ab12").segmenterVersion(1).quote("held at customs").build());
        chatConversationRepository.saveAndFlush(conversation);

        jdbcTemplate.update("delete from email_message where id = ?", email.getId().id());

        assertThat(jdbcTemplate.queryForObject("select quote from chat_citation where email_id is null", String.class)).isEqualTo("held at customs");
    }

    @Test
    void two_messages_cannot_share_a_position() {
        var conversation = conversation(owner, "Who is Anna?");
        message(conversation, 0, ChatRole.USER, "Who is Anna?", ChatMessageState.DONE);
        message(conversation, 0, ChatRole.ASSISTANT, "Anna.", ChatMessageState.DONE);

        assertThatThrownBy(() -> chatConversationRepository.saveAndFlush(conversation))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_chat_message_conversation_position");
    }

    @Test
    void a_message_has_a_known_role() {
        var conversation = chatConversationRepository.saveAndFlush(conversation(owner, "Hi"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into chat_message (id, conversation_id, position, role, state, created_at) values (nextval('chat_message_seq'), ?, 0, 'SYSTEM', 'DONE', now())",
                conversation.getId().id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chat_message_role");
    }

    @Test
    void a_message_has_a_known_state() {
        var conversation = chatConversationRepository.saveAndFlush(conversation(owner, "Hi"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into chat_message (id, conversation_id, position, role, state, created_at) values (nextval('chat_message_seq'), ?, 0, 'USER', 'THINKING', now())",
                conversation.getId().id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chat_message_state");
    }

    @Test
    void a_paragraph_citation_needs_its_paragraph() {
        var conversation = conversation(owner, "Why late?");
        var answer = message(conversation, 0, ChatRole.ASSISTANT, "Customs.", ChatMessageState.DONE);
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(1).type(CitationType.EMAIL_PARAGRAPH).email(email).build());

        assertThatThrownBy(() -> chatConversationRepository.saveAndFlush(conversation))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chat_citation_target");
    }

    @Test
    void a_field_citation_needs_its_field() {
        var conversation = conversation(owner, "Who is Anna?");
        var answer = message(conversation, 0, ChatRole.ASSISTANT, "Anna.", ChatMessageState.DONE);
        answer.getCitations().add(ChatCitation.builder().message(answer).marker(1).type(CitationType.FIELD).collection(people).recordId(4711L).build());

        assertThatThrownBy(() -> chatConversationRepository.saveAndFlush(conversation))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chat_citation_target");
    }

    private static ChatConversation conversation(AppUser user, String title) {
        return ChatConversation.builder().owner(user).title(title).build();
    }

    private static ChatMessage message(ChatConversation conversation, int position, ChatRole role, String content, ChatMessageState state) {
        var message = ChatMessage.builder().conversation(conversation).position(position).role(role).content(content).state(state).build();
        conversation.getMessages().add(message);
        return message;
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from \"" + table + "\"", Long.class);
    }
}

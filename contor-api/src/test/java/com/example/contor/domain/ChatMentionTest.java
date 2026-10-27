package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ChatMentionTest {

    @Test
    public void can_mention_a_record() {
        var mention = ChatMention.record(3L, 4711L, "Anna Kessler", "People");

        assertThat(mention.kind()).isEqualTo(ChatMentionKind.RECORD);
        assertThat(mention.collectionId()).isEqualTo(3L);
        assertThat(mention.recordId()).isEqualTo(4711L);
        assertThat(mention.emailId()).isNull();
        assertThat(mention.label()).isEqualTo("Anna Kessler");
        assertThat(mention.collectionName()).isEqualTo("People");
    }

    @Test
    public void can_mention_an_email() {
        var mention = ChatMention.email(9L, "Delay notice");

        assertThat(mention.kind()).isEqualTo(ChatMentionKind.EMAIL);
        assertThat(mention.emailId()).isEqualTo(9L);
        assertThat(mention.collectionId()).isNull();
    }

    @Test
    public void the_label_is_trimmed() {
        assertThat(ChatMention.email(9L, "  Delay notice ").label()).isEqualTo("Delay notice");
    }

    @Test
    public void a_record_mention_needs_its_record() {
        assertThatThrownBy(() -> ChatMention.record(3L, null, "Anna", "People")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatMention.record(null, 4711L, "Anna", "People")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatMention.record(0L, 4711L, "Anna", "People")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void an_email_mention_needs_its_email() {
        assertThatThrownBy(() -> ChatMention.email(null, "Delay notice")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void an_email_mention_has_no_record() {
        assertThatThrownBy(() -> new ChatMention(ChatMentionKind.EMAIL, 3L, 4711L, 9L, "Delay notice", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void a_mention_needs_a_kind_and_a_label() {
        assertThatThrownBy(() -> new ChatMention(null, null, null, 9L, "Delay notice", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatMention.email(9L, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatMention.email(9L, null)).isInstanceOf(IllegalArgumentException.class);
    }
}

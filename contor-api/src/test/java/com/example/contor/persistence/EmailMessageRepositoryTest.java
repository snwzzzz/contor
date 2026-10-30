package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.ai.DigestEmail;
import com.example.contor.ai.DiscoveryEmail;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.EmailHeader;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProcessingState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class EmailMessageRepositoryTest {

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private EmailEmbeddingRepository emailEmbeddingRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AiSettingsRepository aiSettingsRepository;

    @Test
    void can_save_and_find_by_provider_message_id_with_json_columns() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var email = Fixtures.email(account, "AAMk-1");
        email.setHeaders(List.of(new EmailHeader("Received", "a"), new EmailHeader("Received", "b")));
        emailMessageRepository.saveAndFlush(email);

        var found = emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "AAMk-1");

        assertThat(found).isPresent();
        assertThat(found.get().getProcessingState()).isEqualTo(ProcessingState.NEW);
        assertThat(found.get().getProcessingVersion()).isZero();
        assertThat(found.get().isRead()).isFalse();
        assertThat(found.get().getToRecipients()).containsExactly(new EmailRecipient("Me", "me@example.com"));
        assertThat(found.get().getHeaders()).hasSize(2);
    }

    @Test
    void counts_matching_senders_of_one_owner_most_frequent_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        sender(mine, "1", "anna@acme.com", "Anna Acme");
        sender(mine, "2", "anna@acme.com", "Anna Acme");
        sender(mine, "3", "bob@acme.com", "Bob");
        sender(mine, "4", "carl@other.example", "Carl");
        sender(foreign, "5", "zoe@acme.com", "Zoe");
        var sent = sender(mine, "6", "me@acme.com", "Me");
        sent.setFolder("sent");
        emailMessageRepository.saveAndFlush(sent);

        var senders = emailMessageRepository.findSenders(owner.getId(), "%acme%", PageRequest.of(0, 10));

        assertThat(senders).containsExactly(new SenderCount("Anna Acme", "anna@acme.com", 2L), new SenderCount("Bob", "bob@acme.com", 1L));
    }

    @Test
    void suggestions_leave_out_hidden_mailboxes() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var shown = mailAccountRepository.save(Fixtures.mailAccount(owner, "shown@example.com"));
        var hiddenBox = Fixtures.mailAccount(owner, "hidden@example.com");
        hiddenBox.setHidden(true);
        var hidden = mailAccountRepository.save(hiddenBox);
        sender(shown, "1", "anna@acme.com", "Anna");
        sender(hidden, "2", "bert@acme.com", "Bert");
        sentTo(shown, "3", new EmailRecipient("Carl", "carl@acme.com"));
        sentTo(hidden, "4", new EmailRecipient("Dora", "dora@acme.com"));

        assertThat(emailMessageRepository.findSenders(owner.getId(), "%acme%", PageRequest.of(0, 10)))
                .extracting(SenderCount::address).containsExactly("anna@acme.com");
        assertThat(emailMessageRepository.findRecipients(owner.getId(), "%acme%", 10))
                .extracting(SenderCount::address).containsExactly("carl@acme.com");
    }

    @Test
    void counts_matching_recipients_of_one_owners_sent_mail_most_frequent_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        sentTo(mine, "1", new EmailRecipient("Anna Acme", "Anna@acme.com"), new EmailRecipient(null, "bob@acme.com"));
        sentTo(mine, "2", new EmailRecipient("Anna Acme", "anna@acme.com"));
        sentTo(mine, "3", new EmailRecipient("Carl", "carl@other.example"));
        sentTo(foreign, "4", new EmailRecipient("Zoe", "zoe@acme.com"));
        var received = Fixtures.email(mine, "5");
        received.setToRecipients(List.of(new EmailRecipient("Dora", "dora@acme.com")));
        emailMessageRepository.saveAndFlush(received);

        var recipients = emailMessageRepository.findRecipients(owner.getId(), "%acme%", 10);

        assertThat(recipients).containsExactly(new SenderCount("Anna Acme", "anna@acme.com", 2L), new SenderCount(null, "bob@acme.com", 1L));
        assertThat(emailMessageRepository.findRecipients(owner.getId(), "%\\%%", 10)).isEmpty();
    }

    @Test
    void finds_summaries_of_one_owners_mail_as_a_page_newest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        message(mine, "1", "conv-a", "inbox", "2026-01-01T10:00:00Z");
        var newest = message(mine, "2", "conv-b", "inbox", "2026-01-03T10:00:00Z");
        message(mine, "3", "conv-a", "inbox", "2026-01-02T10:00:00Z");
        message(foreign, "4", "conv-c", "inbox", "2026-01-04T10:00:00Z");
        newest.setBodyText("long body");
        newest.setSnippet("snippet");
        newest.setRead(true);
        newest.setProcessingState(ProcessingState.DONE);
        emailMessageRepository.saveAndFlush(newest);

        var page = emailMessageRepository.findSummaries(
                EmailSpecifications.matching(new EmailSpecifications.Criteria(owner.getId(), null, null, null, null)),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "receivedAt")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(EmailSummary::conversationId).containsExactly("conv-b", "conv-a");
        assertThat(page.getContent().getFirst()).isEqualTo(new EmailSummary(newest.getId().id(), mine.getId().id(), "conv-b",
                "inbox", "shop@example.com", "Shop", List.of(new EmailRecipient("Me", "me@example.com")), "Your order has shipped",
                "snippet", Instant.parse("2026-01-03T10:00:00Z"), true, false, ProcessingState.DONE));
    }

    @Test
    void summaries_honour_the_search_filters() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var match = sender(mine, "1", "anna@acme.com", "Anna");
        sender(mine, "2", "bob@other.example", "Bob");

        var page = emailMessageRepository.findSummaries(EmailSpecifications.matching(
                        new EmailSpecifications.Criteria(owner.getId(), null, null, null, "acme")),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "receivedAt")));

        assertThat(page.getContent()).extracting(EmailSummary::id).containsExactly(match.getId().id());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void finds_earlier_inbox_mail_from_a_sender_of_one_owner_newest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var older = from(mine, "1", "Anna@Acme.com", "2026-01-01T10:00:00Z");
        var newer = from(mine, "2", "anna@acme.com", "2026-01-02T10:00:00Z");
        from(mine, "3", "anna@acme.com", "2026-01-05T10:00:00Z");
        from(mine, "4", "bob@acme.com", "2026-01-02T10:00:00Z");
        from(foreign, "5", "anna@acme.com", "2026-01-02T10:00:00Z");
        var sent = from(mine, "6", "anna@acme.com", "2026-01-02T11:00:00Z");
        sent.setFolder("sent");
        emailMessageRepository.saveAndFlush(sent);

        var history = emailMessageRepository.findFromSenderBefore(owner.getId(), "ANNA@acme.com", Instant.parse("2026-01-04T00:00:00Z"),
                PageRequest.of(0, 5));

        assertThat(history).extracting(EmailMessage::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void knows_whether_an_owner_ever_wrote_to_an_address() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        sentTo(mine, "1", new EmailRecipient("Anna", "Anna@Acme.com"));
        sentTo(foreign, "2", new EmailRecipient("Bob", "bob@acme.com"));
        var received = Fixtures.email(mine, "3");
        received.setToRecipients(List.of(new EmailRecipient("Carl", "carl@acme.com")));
        emailMessageRepository.saveAndFlush(received);

        assertThat(emailMessageRepository.existsSentTo(owner.getId(), "anna@ACME.com")).isTrue();
        assertThat(emailMessageRepository.existsSentTo(owner.getId(), "bob@acme.com")).isFalse();
        assertThat(emailMessageRepository.existsSentTo(owner.getId(), "carl@acme.com")).isFalse();
    }

    @Test
    void finds_sent_mail_nobody_answered_in_its_conversation() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var unanswered = message(mine, "1", "conv-a", "sent", "2026-10-01T10:00:00Z");
        message(mine, "2", "conv-b", "sent", "2026-10-01T10:00:00Z");
        message(mine, "3", "conv-b", "inbox", "2026-10-02T10:00:00Z");
        message(mine, "4", "conv-c", "inbox", "2026-09-30T10:00:00Z");
        var answeredBefore = message(mine, "5", "conv-c", "sent", "2026-10-01T11:00:00Z");
        message(mine, "6", "conv-d", "sent", "2026-10-11T10:00:00Z");
        message(foreign, "7", "conv-e", "sent", "2026-10-01T10:00:00Z");

        var found = emailMessageRepository.findUnansweredSent(owner.getId(), Instant.parse("2026-09-20T00:00:00Z"),
                Instant.parse("2026-10-08T00:00:00Z"), 10);

        assertThat(found).containsExactlyInAnyOrder(unanswered.getId().id(), answeredBefore.getId().id());
    }

    @Test
    void lists_the_digest_of_one_owners_mail_since_a_date_newest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        message(mine, "1", "conv-a", "inbox", "2026-09-01T10:00:00Z");
        var older = message(mine, "2", "conv-b", "inbox", "2026-10-02T10:00:00Z");
        var newer = message(mine, "3", "conv-c", "sent", "2026-10-03T10:00:00Z");
        message(foreign, "4", "conv-d", "inbox", "2026-10-04T10:00:00Z");

        var digest = emailMessageRepository.findDigest(owner.getId(), Instant.parse("2026-10-01T00:00:00Z"), PageRequest.of(0, 10));

        assertThat(digest).extracting(DigestEmail::id).containsExactly(newer.getId().id(), older.getId().id());
        assertThat(digest.getFirst().folder()).isEqualTo("sent");
        assertThat(digest.getFirst().subject()).isEqualTo("Your order has shipped");
    }

    private EmailMessage from(MailAccount account, String id, String address, String receivedAt) {
        var email = Fixtures.email(account, id);
        email.setFromAddress(address);
        email.setReceivedAt(Instant.parse(receivedAt));
        return emailMessageRepository.saveAndFlush(email);
    }

    private void sentTo(MailAccount account, String id, EmailRecipient... recipients) {
        var email = Fixtures.email(account, id);
        email.setFolder("sent");
        email.setToRecipients(List.of(recipients));
        emailMessageRepository.saveAndFlush(email);
    }

    private EmailMessage sender(MailAccount account, String id, String address, String name) {
        var email = Fixtures.email(account, id);
        email.setFromAddress(address);
        email.setFromName(name);
        return emailMessageRepository.saveAndFlush(email);
    }

    private EmailMessage message(MailAccount account, String id, String conversation, String folder, String receivedAt) {
        var email = Fixtures.email(account, id);
        email.setConversationId(conversation);
        email.setFolder(folder);
        email.setReceivedAt(Instant.parse(receivedAt));
        return emailMessageRepository.saveAndFlush(email);
    }

    @Test
    void finds_a_thread_of_one_mailbox_oldest_first_sent_mail_included() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var other = mailAccountRepository.save(Fixtures.mailAccount(owner, "other@example.com"));
        message(mine, "3", "conv-1", "inbox", "2026-01-03T10:00:00Z");
        message(mine, "1", "conv-1", "inbox", "2026-01-01T10:00:00Z");
        message(mine, "2", "conv-1", "sent", "2026-01-02T10:00:00Z");
        message(mine, "4", "conv-2", "inbox", "2026-01-02T10:00:00Z");
        message(other, "5", "conv-1", "inbox", "2026-01-02T12:00:00Z");

        var thread = emailMessageRepository.findThread(mine.getId(), "conv-1");

        assertThat(thread).extracting(EmailMessage::getProviderMessageId).containsExactly("1", "2", "3");
    }

    @Test
    void counts_thread_messages_and_the_last_reply_per_conversation_of_one_owner() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var stranger = appUserRepository.save(Fixtures.user("stranger"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(stranger, "them@example.com"));
        message(mine, "1", "conv-1", "inbox", "2026-01-01T10:00:00Z");
        message(mine, "2", "conv-1", "sent", "2026-01-02T10:00:00Z");
        message(mine, "3", "conv-1", "sent", "2026-01-04T10:00:00Z");
        message(mine, "4", "conv-2", "inbox", "2026-01-02T10:00:00Z");
        message(foreign, "5", "conv-1", "inbox", "2026-01-02T12:00:00Z");

        var stats = emailMessageRepository.threadStats(owner.getId(), List.of("conv-1", "conv-2", "conv-3"));

        assertThat(stats).containsExactlyInAnyOrder(
                new ThreadStat(mine.getId().id(), "conv-1", 3L, Instant.parse("2026-01-04T10:00:00Z")),
                new ThreadStat(mine.getId().id(), "conv-2", 1L, null));
    }

    @Test
    void provider_message_id_is_unique_per_account() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var first = mailAccountRepository.save(Fixtures.mailAccount(owner, "a@example.com"));
        var second = mailAccountRepository.save(Fixtures.mailAccount(owner, "b@example.com"));
        emailMessageRepository.saveAndFlush(Fixtures.email(first, "AAMk-1"));
        emailMessageRepository.saveAndFlush(Fixtures.email(second, "AAMk-1"));

        assertThatThrownBy(() -> emailMessageRepository.saveAndFlush(Fixtures.email(first, "AAMk-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finds_by_processing_state() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var queued = Fixtures.email(account, "AAMk-1");
        queued.setProcessingState(ProcessingState.QUEUED);
        emailMessageRepository.save(queued);
        emailMessageRepository.save(Fixtures.email(account, "AAMk-2"));

        assertThat(emailMessageRepository.findByProcessingState(ProcessingState.QUEUED))
                .extracting(e -> e.getProviderMessageId())
                .containsExactly("AAMk-1");
    }

    private EmailMessage email(MailAccount account, String id, String receivedAt, ProcessingState state) {
        EmailMessage email = Fixtures.email(account, id);
        email.setReceivedAt(Instant.parse(receivedAt));
        email.setProcessingState(state);
        email.setSubject("Subject " + id);
        email.setSnippet("Snippet " + id);
        return emailMessageRepository.save(email);
    }

    @Test
    void finds_ids_to_process_of_one_owner_oldest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var newer = email(mine, "A", "2026-03-01T00:00:00Z", ProcessingState.NEW);
        var older = email(mine, "B", "2026-02-01T00:00:00Z", ProcessingState.NEW);
        email(mine, "C", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        email(mine, "D", "2026-02-15T00:00:00Z", ProcessingState.DONE);
        email(theirs, "E", "2026-02-15T00:00:00Z", ProcessingState.NEW);
        emailMessageRepository.flush();

        List<Long> ids = emailMessageRepository.findIdsForProcessing(owner.getId(), ProcessingState.NEW,
                Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 10));

        assertThat(ids).containsExactly(older.getId().id(), newer.getId().id());
        assertThat(emailMessageRepository.findIdsForProcessing(owner.getId(), ProcessingState.NEW,
                Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 1))).hasSize(1);
    }

    /** Three mailboxes of one owner: one the AI reads, one it may not read, one hidden in the app. */
    private record Mailboxes(AppUser owner, MailAccount read, MailAccount off, MailAccount hidden) {}

    private Mailboxes mailboxes() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var read = mailAccountRepository.save(Fixtures.mailAccount(owner, "read@example.com"));
        var off = Fixtures.mailAccount(owner, "off@example.com");
        off.setAiEnabled(false);
        var hidden = Fixtures.mailAccount(owner, "hidden@example.com");
        hidden.setHidden(true);
        var boxes = new Mailboxes(owner, read, mailAccountRepository.save(off), mailAccountRepository.save(hidden));
        for (var box : List.of(boxes.read(), boxes.off(), boxes.hidden())) {
            var inbox = email(box, box.getEmailAddress().value(), "2026-02-01T00:00:00Z", ProcessingState.NEW);
            inbox.setSubject(box.getEmailAddress().value());
            // Sent mail is never processed (the sync marks it SKIPPED), but the digest and the unanswered check read it.
            message(box, "sent-" + box.getEmailAddress().value(), "conv-" + box.getEmailAddress().value(), "sent", "2026-10-01T10:00:00Z")
                    .setProcessingState(ProcessingState.SKIPPED);
        }
        emailMessageRepository.flush();
        return boxes;
    }

    private List<String> aiReadable(AppUser owner) {
        var since = Instant.parse("2026-01-01T00:00:00Z");
        var processing = emailMessageRepository.findIdsForProcessing(owner.getId(), ProcessingState.NEW, since, PageRequest.of(0, 10));
        var discovery = emailMessageRepository.findDiscoveryEmails(owner.getId(), since, PageRequest.of(0, 10));
        var digest = emailMessageRepository.findDigest(owner.getId(), since, PageRequest.of(0, 10));
        var unanswered = emailMessageRepository.findUnansweredSent(owner.getId(), Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-10-08T00:00:00Z"), 10);
        // Every AI query agrees: compare them as the mailboxes their mail comes from.
        var fromProcessing = emailMessageRepository.findAllById(processing.stream().map(EmailMessage.EmailMessageId::new).toList())
                .stream().map(email -> email.getAccount().getEmailAddress().value()).sorted().toList();
        assertThat(discovery.stream().map(DiscoveryEmail::subject).sorted().toList()).isEqualTo(fromProcessing);
        assertThat(digest).hasSize(fromProcessing.size() * 2);
        assertThat(unanswered).hasSize(fromProcessing.size());
        return fromProcessing;
    }

    @Test
    void the_ai_never_reads_a_mailbox_it_is_turned_off_for_and_reads_hidden_ones_by_default() {
        var boxes = mailboxes();

        assertThat(aiReadable(boxes.owner())).containsExactly("hidden@example.com", "read@example.com");
    }

    @Test
    void the_ai_skips_hidden_mailboxes_when_the_owner_turned_reading_them_off() {
        var boxes = mailboxes();
        var settings = AiSettings.defaults(boxes.owner());
        settings.setReadHiddenMailboxes(false);
        aiSettingsRepository.saveAndFlush(settings);

        assertThat(aiReadable(boxes.owner())).containsExactly("read@example.com");
    }

    @Test
    void discovery_samples_skip_sent_mail() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        email(mine, "A", "2026-02-01T00:00:00Z", ProcessingState.NEW);
        var sent = email(mine, "B", "2026-03-01T00:00:00Z", ProcessingState.SKIPPED);
        sent.setFolder("sent");
        emailMessageRepository.flush();

        assertThat(emailMessageRepository.findDiscoveryEmails(owner.getId(), Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 10)))
                .extracting(DiscoveryEmail::subject).containsExactly("Subject A");
    }

    @Test
    void lists_discovery_samples_of_one_owner_newest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        email(mine, "A", "2026-02-01T00:00:00Z", ProcessingState.NEW);
        email(mine, "B", "2026-03-01T00:00:00Z", ProcessingState.DONE);
        email(mine, "C", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        email(theirs, "D", "2026-02-15T00:00:00Z", ProcessingState.NEW);
        emailMessageRepository.flush();

        List<DiscoveryEmail> samples = emailMessageRepository.findDiscoveryEmails(owner.getId(),
                Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 10));

        assertThat(samples).extracting(DiscoveryEmail::subject).containsExactly("Subject B", "Subject A");
        assertThat(samples.getFirst().fromAddress()).isEqualTo("shop@example.com");
        assertThat(samples.getFirst().snippet()).isEqualTo("Snippet B");
    }

    @Test
    void marks_old_unprocessed_emails_of_one_owner() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var old = email(mine, "A", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        var recent = email(mine, "B", "2026-03-01T00:00:00Z", ProcessingState.NEW);
        var foreign = email(theirs, "C", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        emailMessageRepository.flush();

        int updated = emailMessageRepository.updateProcessingStateBefore(owner.getId(), ProcessingState.NEW, ProcessingState.SKIPPED,
                Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(updated).isEqualTo(1);
        assertThat(emailMessageRepository.findById(old.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.SKIPPED);
        assertThat(emailMessageRepository.findById(recent.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.NEW);
        assertThat(emailMessageRepository.findById(foreign.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.NEW);
    }

    @Test
    void resets_interrupted_emails() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var interrupted = email(account, "A", "2026-01-01T00:00:00Z", ProcessingState.PROCESSING);
        email(account, "B", "2026-01-01T00:00:00Z", ProcessingState.DONE);
        emailMessageRepository.flush();

        assertThat(emailMessageRepository.updateProcessingState(ProcessingState.PROCESSING, ProcessingState.NEW)).isEqualTo(1);
        assertThat(emailMessageRepository.findById(interrupted.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.NEW);
    }

    @Test
    void finds_the_inbox_mail_without_a_vector_for_the_model_newest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var old = email(mine, "old", "2026-01-01T00:00:00Z", ProcessingState.DONE);
        var recent = email(mine, "recent", "2026-03-01T00:00:00Z", ProcessingState.DONE);
        var embedded = email(mine, "embedded", "2026-02-01T00:00:00Z", ProcessingState.DONE);
        var embeddedWithOldModel = email(mine, "oldmodel", "2026-02-15T00:00:00Z", ProcessingState.DONE);
        var sent = email(mine, "sent", "2026-04-01T00:00:00Z", ProcessingState.SKIPPED);
        sent.setFolder("sent");
        email(theirs, "foreign", "2026-05-01T00:00:00Z", ProcessingState.NEW);
        var vector = new float[com.example.contor.domain.EmailEmbedding.DIMENSIONS];
        emailEmbeddingRepository.save(com.example.contor.domain.EmailEmbedding.builder()
                .id(new com.example.contor.domain.EmailEmbedding.EmailEmbeddingId(embedded.getId().id(), "nomic")).embedding(vector).build());
        emailEmbeddingRepository.save(com.example.contor.domain.EmailEmbedding.builder()
                .id(new com.example.contor.domain.EmailEmbedding.EmailEmbeddingId(embeddedWithOldModel.getId().id(), "old-model")).embedding(vector).build());
        emailMessageRepository.flush();

        assertThat(emailMessageRepository.findIdsWithoutEmbedding(owner.getId(), "nomic", PageRequest.of(0, 10)))
                .containsExactly(recent.getId().id(), embeddedWithOldModel.getId().id(), old.getId().id());
        assertThat(emailMessageRepository.findIdsWithoutEmbedding(owner.getId(), "nomic", PageRequest.of(0, 1))).containsExactly(recent.getId().id());
        assertThat(emailMessageRepository.countIndexable(owner.getId())).isEqualTo(4);
    }
}


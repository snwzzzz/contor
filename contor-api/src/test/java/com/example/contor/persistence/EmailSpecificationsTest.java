package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.HexColor;
import com.example.contor.domain.Label;
import com.example.contor.domain.LabelSource;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.SearchField;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

import java.time.Instant;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class EmailSpecificationsTest {

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private EmailLabelRepository emailLabelRepository;

    private AppUser owner;
    private MailAccount work;
    private MailAccount home;
    private Label orders;

    @BeforeEach
    void emails() {
        owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        work = mailAccountRepository.save(Fixtures.mailAccount(owner, "work@example.com"));
        home = mailAccountRepository.save(Fixtures.mailAccount(owner, "home@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "other@example.com"));
        orders = labelRepository.save(Label.builder().slug(new Slug("orders")).name("Orders").color(new HexColor("#00ff00")).createdBy(CreatedBy.USER).build());

        var shipped = save(work, "1", "Your ORDER has shipped", "shop@example.com", "Shop", ProcessingState.DONE, "2026-01-03T00:00:00Z");
        save(work, "2", "Meeting tomorrow", "boss@example.com", "Boss", ProcessingState.NEW, "2026-01-02T00:00:00Z");
        save(home, "3", "Invoice", "billing@shop.example", "Shop Billing", ProcessingState.NEW, "2026-01-01T00:00:00Z");
        save(foreign, "4", "Order for someone else", "shop@example.com", "Shop", ProcessingState.NEW, "2026-01-04T00:00:00Z");
        emailLabelRepository.save(EmailLabel.builder().id(new EmailLabel.EmailLabelId(shipped.getId().id(), orders.getId().id()))
                .source(LabelSource.AI).build());
    }

    private EmailMessage save(MailAccount account, String id, String subject, String from, String fromName, ProcessingState state, String receivedAt) {
        var email = Fixtures.email(account, id);
        email.setSubject(subject);
        email.setFromAddress(from);
        email.setFromName(fromName);
        email.setProcessingState(state);
        email.setReceivedAt(Instant.parse(receivedAt));
        return emailMessageRepository.save(email);
    }

    private List<String> subjects(EmailSpecifications.Criteria criteria) {
        return emailMessageRepository.findAll(EmailSpecifications.matching(criteria), Sort.by(Sort.Direction.DESC, "receivedAt"))
                .stream().map(EmailMessage::getSubject).toList();
    }

    @Test
    void only_returns_emails_of_the_owner() {
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, null)))
                .containsExactly("Your ORDER has shipped", "Meeting tomorrow", "Invoice");
    }

    @Test
    void filters_by_account_label_and_state() {
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), home.getId(), null, null, null))).containsExactly("Invoice");
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, orders.getId(), null, null))).containsExactly("Your ORDER has shipped");
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, ProcessingState.NEW, null)))
                .containsExactly("Meeting tomorrow", "Invoice");
    }

    @Test
    void searches_subject_and_sender_case_insensitively() {
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, "order"))).containsExactly("Your ORDER has shipped");
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, "SHOP"))).containsExactly("Your ORDER has shipped", "Invoice");
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, "boss@"))).containsExactly("Meeting tomorrow");
    }

    @Test
    void treats_like_wildcards_in_the_search_literally() {
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, "%"))).isEmpty();
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, "_"))).isEmpty();
    }

    private List<String> search(String text, SearchField field) {
        return subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, text, field));
    }

    @Test
    void searches_only_the_chosen_field() {
        var invoice = emailMessageRepository.findAll().stream()
                .filter(email -> email.getSubject().equals("Invoice")).findFirst().orElseThrow();
        invoice.setBodyText("Please pay for ORDER 42.");
        invoice.setToRecipients(List.of(new EmailRecipient("Anna Berger", "anna@berger.example")));
        emailMessageRepository.save(invoice);

        assertThat(search("order", SearchField.SUBJECT)).containsExactly("Your ORDER has shipped");
        assertThat(search("order", SearchField.CONTENT)).containsExactly("Invoice");
        assertThat(search("order", SearchField.ALL)).containsExactly("Your ORDER has shipped", "Invoice");
        assertThat(search("berger", SearchField.PEOPLE)).containsExactly("Invoice");
        assertThat(search("boss", SearchField.PEOPLE)).containsExactly("Meeting tomorrow");
        assertThat(search("@shop.example", SearchField.PEOPLE)).containsExactly("Invoice");
        assertThat(search("berger", SearchField.SUBJECT)).isEmpty();
    }

    private List<String> answered(Boolean answered) {
        return subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, null, SearchField.ALL, answered));
    }

    @Test
    void filters_by_whether_a_newer_sent_message_exists_in_the_conversation() {
        // "Meeting tomorrow" (work, 2 Jan) gets a reply on 3 Jan; "Your ORDER has shipped" (work, 3 Jan 00:00) has a conversation
        // whose only sent message is older; "Invoice" has no conversation at all.
        var meeting = emailMessageRepository.findAll().stream().filter(e -> e.getSubject().equals("Meeting tomorrow")).findFirst().orElseThrow();
        var shipped = emailMessageRepository.findAll().stream().filter(e -> e.getSubject().equals("Your ORDER has shipped")).findFirst().orElseThrow();
        meeting.setConversationId("conv-meeting");
        shipped.setConversationId("conv-shipped");
        emailMessageRepository.save(meeting);
        emailMessageRepository.save(shipped);
        var reply = save(work, "5", "Re: Meeting tomorrow", "work@example.com", "Me", ProcessingState.SKIPPED, "2026-01-03T09:00:00Z");
        reply.setFolder("sent");
        reply.setConversationId("conv-meeting");
        var oldNote = save(work, "6", "Note", "work@example.com", "Me", ProcessingState.SKIPPED, "2026-01-01T09:00:00Z");
        oldNote.setFolder("sent");
        oldNote.setConversationId("conv-shipped");
        emailMessageRepository.saveAll(List.of(reply, oldNote));

        assertThat(answered(true)).containsExactly("Meeting tomorrow");
        assertThat(answered(false)).containsExactly("Your ORDER has shipped", "Invoice");
        assertThat(answered(null)).containsExactly("Your ORDER has shipped", "Meeting tomorrow", "Invoice");
    }

    private List<String> read(Boolean read) {
        return subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, null, SearchField.ALL, null, false, read));
    }

    @Test
    void filters_by_read_state() {
        var meeting = emailMessageRepository.findAll().stream().filter(e -> e.getSubject().equals("Meeting tomorrow")).findFirst().orElseThrow();
        meeting.setRead(true);
        emailMessageRepository.save(meeting);

        assertThat(read(false)).containsExactly("Your ORDER has shipped", "Invoice");
        assertThat(read(true)).containsExactly("Meeting tomorrow");
        assertThat(read(null)).containsExactly("Your ORDER has shipped", "Meeting tomorrow", "Invoice");
    }

    @Test
    void sent_folder_lists_only_the_owners_sent_mail() {
        var sent = save(work, "7", "Re: Invoice", "work@example.com", "Me", ProcessingState.SKIPPED, "2026-01-05T09:00:00Z");
        sent.setFolder("sent");
        emailMessageRepository.save(sent);
        var foreignSent = save(mailAccountRepository.findAll().stream().filter(a -> !a.getOwner().getId().equals(owner.getId())).findFirst().orElseThrow(),
                "8", "Not mine", "other@example.com", "Other", ProcessingState.SKIPPED, "2026-01-06T09:00:00Z");
        foreignSent.setFolder("sent");
        emailMessageRepository.save(foreignSent);

        var inbox = new EmailSpecifications.Criteria(owner.getId(), null, null, null, null, SearchField.ALL, null, false);
        var sentCriteria = new EmailSpecifications.Criteria(owner.getId(), null, null, null, null, SearchField.ALL, null, true);

        assertThat(subjects(sentCriteria)).containsExactly("Re: Invoice");
        assertThat(subjects(inbox)).doesNotContain("Re: Invoice");
    }

    @Test
    void sent_folder_ignores_the_answered_filter() {
        var sent = save(work, "7", "Re: Invoice", "work@example.com", "Me", ProcessingState.SKIPPED, "2026-01-05T09:00:00Z");
        sent.setFolder("sent");
        emailMessageRepository.save(sent);

        var criteria = new EmailSpecifications.Criteria(owner.getId(), null, null, null, null, SearchField.ALL, true, true);

        assertThat(subjects(criteria)).containsExactly("Re: Invoice");
    }

    private List<String> involving(List<Long> emailIds, List<String> addresses, List<String> domains) {
        return emailMessageRepository.findAll(
                        EmailSpecifications.matching(new EmailSpecifications.Criteria(owner.getId(), null, null, null, null))
                                .and(EmailSpecifications.involving(emailIds, addresses, domains)),
                        Sort.by(Sort.Direction.DESC, "receivedAt"))
                .stream().map(EmailMessage::getSubject).toList();
    }

    @Test
    void finds_the_emails_a_record_comes_up_in() {
        Long invoice = emailMessageRepository.findAll().stream()
                .filter(email -> email.getSubject().equals("Invoice")).findFirst().orElseThrow().getId().id();

        assertThat(involving(List.of(invoice), List.of(), List.of())).containsExactly("Invoice");
        assertThat(involving(List.of(), List.of("BOSS@example.com"), List.of())).containsExactly("Meeting tomorrow");
        assertThat(involving(List.of(), List.of(), List.of("Shop.Example"))).containsExactly("Invoice");
        assertThat(involving(List.of(), List.of(), List.of("example.com"))).containsExactly("Your ORDER has shipped", "Meeting tomorrow");
        assertThat(involving(List.of(), List.of(), List.of("hop.example"))).isEmpty();
        assertThat(involving(List.of(invoice), List.of("boss@example.com"), List.of())).containsExactly("Meeting tomorrow", "Invoice");
        assertThat(involving(List.of(), List.of(), List.of())).isEmpty();
    }

    @Test
    void sent_mail_never_shows_up_in_searches() {
        var reply = save(work, "5", "Re: Meeting tomorrow", "work@example.com", "Me", ProcessingState.SKIPPED, "2026-01-05T00:00:00Z");
        reply.setFolder("sent");
        emailMessageRepository.save(reply);

        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, null)))
                .doesNotContain("Re: Meeting tomorrow").hasSize(3);
        assertThat(subjects(new EmailSpecifications.Criteria(owner.getId(), null, null, null, "meeting")))
                .containsExactly("Meeting tomorrow");
    }
}

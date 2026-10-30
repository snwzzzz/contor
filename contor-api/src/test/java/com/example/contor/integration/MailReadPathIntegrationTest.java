package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.EmailPageDto;
import com.example.contor.api.model.EmailThreadDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.service.MailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline of the inbox read path (AI v2 plan, step 1): how many SQL statements an inbox page and a thread need, and
 * how long they take on a realistic mailbox (bodies of tens of kilobytes). The statement counts guard against N+1
 * queries; the timings are only logged ("Read path baseline …") to compare optimizations against.
 */
@Slf4j
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.example.contor.integration.SqlCapture"})
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailReadPathIntegrationTest {

    private static final int EMAILS = 300;
    private static final int CONVERSATIONS = 60;
    private static final int PAGE_SIZE = 50;
    private static final int TIMED_RUNS = 5;

    @Autowired
    private MailService mailService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private Long threadEmailId;

    @BeforeEach
    void seedMailbox() {
        AppUser owner = appUserRepository.save(AppUser.builder().username(new Username("reader")).email(new Email("reader@example.com"))
                .passwordHash("x").role(UserRole.OWNER).build());
        var principal = AuthenticatedUser.of(owner);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MailAccount account = mailAccountRepository.save(MailAccount.builder().owner(owner).provider(MailProvider.MICROSOFT_GRAPH)
                .emailAddress(new Email("me@example.com")).displayName("Inbox").build());

        String text = "Hello, this is a realistic paragraph of an email body. ".repeat(350);
        String html = "<div><p>" + text + "</p><table><tr><td>layout</td></tr></table></div>";
        Instant start = Instant.parse("2026-01-01T08:00:00Z");
        List<EmailMessage> emails = new ArrayList<>();
        for (int i = 0; i < EMAILS; i++) {
            boolean sent = i % 10 == 9;
            emails.add(EmailMessage.builder().account(account).providerMessageId("msg-" + i)
                    .conversationId("conv-" + (i % CONVERSATIONS)).folder(sent ? EmailMessage.FOLDER_SENT : "inbox")
                    .fromName(sent ? "Me" : "Sender " + i).fromAddress(sent ? "me@example.com" : "sender" + i + "@example.org")
                    .toRecipients(List.of(new EmailRecipient("Me", "me@example.com")))
                    .subject("Subject " + (i % CONVERSATIONS)).snippet("Hello, this is a realistic paragraph")
                    .bodyText(text).bodyHtml(html).receivedAt(start.plus(Duration.ofHours(i))).build());
        }
        emailMessageRepository.saveAll(emails);
        threadEmailId = emails.getLast().getId().id();
        entityManager.flush();
        entityManager.clear();

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        SqlCapture.clear();
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void an_inbox_page_needs_a_fixed_number_of_statements() {
        EmailPageDto page = mailService.listEmails(null, null, null, null, null, null, null, null, 0, PAGE_SIZE);

        assertThat(page.getItems()).hasSize(PAGE_SIZE);
        assertThat(SqlCapture.statements()).as("an inbox page must not read mail bodies")
                .noneMatch(sql -> sql.contains("body_text") || sql.contains("body_html"));
        long statements = statistics.getPrepareStatementCount();
        assertThat(statements).isLessThanOrEqualTo(6);
        logTimings("inbox page", statements, () -> mailService.listEmails(null, null, null, null, null, null, null, null, 0, PAGE_SIZE));
    }

    @Test
    void a_thread_needs_a_fixed_number_of_statements() {
        EmailThreadDto thread = mailService.getEmailThread(threadEmailId);

        assertThat(thread.getMessages()).hasSize(EMAILS / CONVERSATIONS);
        long statements = statistics.getPrepareStatementCount();
        assertThat(statements).isLessThanOrEqualTo(6);
        logTimings("thread", statements, () -> mailService.getEmailThread(threadEmailId));
    }

    /** Median of a few warm runs, each with an empty persistence context like a real request. */
    private void logTimings(String what, long statements, Supplier<?> read) {
        List<Long> micros = new ArrayList<>();
        for (int run = 0; run < TIMED_RUNS; run++) {
            entityManager.clear();
            long start = System.nanoTime();
            read.get();
            micros.add((System.nanoTime() - start) / 1000);
        }
        long median = micros.stream().sorted().toList().get(TIMED_RUNS / 2);
        log.info("Read path baseline {}: {} statements, median {} ms over {} runs ({} emails, page {})", what, statements,
                median / 1000.0, TIMED_RUNS, EMAILS, PAGE_SIZE);
    }
}

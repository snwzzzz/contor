package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.service.CollectionSchemaService;
import com.example.contor.service.DemoMailboxService;
import com.example.contor.service.RelationTypeService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo import against the real database and the committed business mailbox, the way the dev start runs it (no logged-in user). */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DemoMailboxIntegrationTest {

    private static final Path MAILBOX = Path.of("src/test/resources/ai-eval/business/mailbox");

    @Autowired private DemoMailboxService demoMailboxService;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private MailAccountRepository mailAccountRepository;
    @Autowired private EmailMessageRepository emailMessageRepository;
    @Autowired private CollectionSchemaService collectionSchemaService;
    @Autowired private RelationTypeService relationTypeService;

    @Test
    void the_business_mailbox_is_imported_once_into_two_demo_accounts() {
        AppUser owner = appUserRepository.save(AppUser.builder().username(new Username("demo")).email(new Email("demo@example.com"))
                .passwordHash("x").role(UserRole.OWNER).build());

        int imported = demoMailboxService.importMailbox(owner, MAILBOX, true);

        assertThat(imported).isEqualTo(305);
        assertThat(mailAccountRepository.findByOwner_Id(owner.getId())).hasSize(2)
                .allSatisfy(account -> assertThat(account.getProvider()).isEqualTo(MailProvider.DEMO));
        assertThat(emailMessageRepository.count()).isGreaterThanOrEqualTo(305);
        assertThat(collectionSchemaService.findAll()).extracting(CollectionDto::getSlug).contains("sales_orders", "shipments", "complaints");
        assertThat(relationTypeService.findAll()).extracting(type -> type.getSlug()).contains("ships", "delivers", "drives_for");
        assertThat(demoMailboxService.importMailbox(owner, MAILBOX, true)).as("second start").isZero();
    }
}

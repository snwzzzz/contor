package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.HexColor;
import com.example.contor.domain.Label;
import com.example.contor.domain.LabelSource;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class LabelRepositoryTest {

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private EmailLabelRepository emailLabelRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private Label label(String slug) {
        return Label.builder().slug(new Slug(slug)).name(slug).color(new HexColor("#ff0000")).createdBy(CreatedBy.USER).build();
    }

    @Test
    void can_save_and_find_by_slug() {
        labelRepository.save(label("invoices"));

        var found = labelRepository.findBySlug(new Slug("invoices"));

        assertThat(found).isPresent();
        assertThat(found.get().getColor()).isEqualTo(new HexColor("#ff0000"));
        assertThat(labelRepository.existsBySlug(new Slug("receipts"))).isFalse();
    }

    @Test
    void can_attach_labels_to_an_email() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var email = emailMessageRepository.save(Fixtures.email(account, "AAMk-1"));
        var label = labelRepository.save(label("invoices"));
        emailLabelRepository.saveAndFlush(EmailLabel.builder()
                .id(new EmailLabel.EmailLabelId(email.getId().id(), label.getId().id()))
                .source(LabelSource.AI)
                .confidence(new BigDecimal("0.8500"))
                .build());

        var labels = emailLabelRepository.findById_EmailId(email.getId().id());

        assertThat(labels).hasSize(1);
        assertThat(labels.get(0).getSource()).isEqualTo(LabelSource.AI);
        assertThat(labels.get(0).getConfidence()).isEqualByComparingTo("0.85");
        assertThat(labels.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void lists_labels_by_name() {
        labelRepository.save(label("zeta"));
        labelRepository.save(label("alpha"));

        assertThat(labelRepository.findAllByOrderByNameAsc()).extracting(Label::getName).containsExactly("alpha", "zeta");
    }

    @Test
    void finds_labels_of_several_emails_at_once() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var first = emailMessageRepository.save(Fixtures.email(account, "AAMk-1"));
        var second = emailMessageRepository.save(Fixtures.email(account, "AAMk-2"));
        var third = emailMessageRepository.save(Fixtures.email(account, "AAMk-3"));
        var label = labelRepository.save(label("orders"));
        for (var email : List.of(first, second, third)) {
            emailLabelRepository.save(EmailLabel.builder().id(new EmailLabel.EmailLabelId(email.getId().id(), label.getId().id()))
                    .source(LabelSource.MANUAL).build());
        }

        assertThat(emailLabelRepository.findById_EmailIdIn(List.of(first.getId().id(), third.getId().id()))).hasSize(2);
    }
}

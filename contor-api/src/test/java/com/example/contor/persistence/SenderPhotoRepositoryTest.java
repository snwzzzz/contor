package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.ContactPhoto;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.SenderDomain;
import com.example.contor.domain.SenderLogo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class SenderPhotoRepositoryTest {

    @Autowired
    private SenderLogoRepository senderLogoRepository;

    @Autowired
    private ContactPhotoRepository contactPhotoRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void finds_cached_logos_by_domain_including_misses() {
        senderLogoRepository.saveAndFlush(logo("siemens.com", new byte[] {1, 2}));
        senderLogoRepository.saveAndFlush(logo("no-logo.example", null));

        var found = senderLogoRepository.findByDomainIn(List.of(new SenderDomain("siemens.com"), new SenderDomain("no-logo.example"),
                new SenderDomain("unknown.example")));

        assertThat(found).extracting(l -> l.getDomain().value()).containsExactlyInAnyOrder("siemens.com", "no-logo.example");
        assertThat(senderLogoRepository.findByDomain(new SenderDomain("siemens.com")).orElseThrow().getImage()).containsExactly(1, 2);
    }

    @Test
    void a_domain_is_cached_once() {
        senderLogoRepository.saveAndFlush(logo("siemens.com", null));

        assertThatThrownBy(() -> senderLogoRepository.saveAndFlush(logo("siemens.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_write_lock_can_be_taken_repeatedly_within_a_transaction() {
        assertThat(senderLogoRepository.lockWrites()).isEqualTo(1);
        assertThat(senderLogoRepository.lockWrites()).isEqualTo(1);
    }

    @Test
    void contact_photos_are_scoped_to_the_mailbox_owner_and_replaced_per_account() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var account = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var foreign = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        contactPhotoRepository.saveAllAndFlush(List.of(contact(account, "bob@example.com"), contact(foreign, "eve@example.com")));

        assertThat(contactPhotoRepository.findByAccount_Owner_Id(owner.getId()))
                .extracting(ContactPhoto::getEmailAddress).containsExactly("bob@example.com");

        contactPhotoRepository.deleteByAccount_Id(account.getId());
        contactPhotoRepository.flush();

        assertThat(contactPhotoRepository.findByAccount_Owner_Id(owner.getId())).isEmpty();
        assertThat(contactPhotoRepository.findByAccount_Owner_Id(other.getId())).hasSize(1);
    }

    private static SenderLogo logo(String domain, byte[] image) {
        return SenderLogo.builder().domain(new SenderDomain(domain)).image(image)
                .contentType(image == null ? null : "image/png").fetchedAt(Instant.now()).build();
    }

    private static ContactPhoto contact(MailAccount account, String address) {
        return ContactPhoto.builder().account(account).emailAddress(address).image(new byte[] {1}).contentType("image/jpeg").build();
    }
}

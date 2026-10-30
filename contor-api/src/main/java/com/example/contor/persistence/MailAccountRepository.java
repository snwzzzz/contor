package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailAccountStatus;
import com.example.contor.domain.MailProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MailAccountRepository extends JpaRepository<MailAccount, MailAccount.MailAccountId> {

    List<MailAccount> findByOwner_Id(AppUser.AppUserId ownerId);

    List<MailAccount> findByStatus(MailAccountStatus status);

    boolean existsByOwner_IdAndProvider(AppUser.AppUserId ownerId, MailProvider provider);

    /** The mailbox a sign-in belongs to (unique per owner, provider and address). */
    Optional<MailAccount> findByOwner_IdAndProviderAndEmailAddress(AppUser.AppUserId ownerId, MailProvider provider, Email emailAddress);
}

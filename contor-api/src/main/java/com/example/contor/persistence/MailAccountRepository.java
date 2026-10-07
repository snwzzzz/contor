package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.MailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MailAccountRepository extends JpaRepository<MailAccount, MailAccount.MailAccountId> {

    List<MailAccount> findByOwner_Id(AppUser.AppUserId ownerId);

    List<MailAccount> findByStatus(com.example.contor.domain.MailAccountStatus status);

    java.util.Optional<MailAccount> findByOwner_IdAndEmailAddress(AppUser.AppUserId ownerId, com.example.contor.domain.Email emailAddress);
}

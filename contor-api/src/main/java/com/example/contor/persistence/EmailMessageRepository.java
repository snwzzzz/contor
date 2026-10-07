package com.example.contor.persistence;

import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ProcessingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, EmailMessage.EmailMessageId>,
        JpaSpecificationExecutor<EmailMessage> {

    Optional<EmailMessage> findByAccount_IdAndProviderMessageId(MailAccount.MailAccountId accountId, String providerMessageId);

    List<EmailMessage> findByProcessingState(ProcessingState processingState);
}

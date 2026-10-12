package com.example.contor.service;

import com.example.contor.ai.ImportanceSignals;
import com.example.contor.ai.ReplyCandidateFilter;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.persistence.EmailMessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What is known about the sender of an email, for the importance of the proposals made from it: one of the user's
 * People records, someone the user wrote to, or a bulk sender. Cheap queries, no model call; the reply triage adds its
 * urgency itself.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ImportanceSignalService {

    private static final String PEOPLE = "people";

    private final EmailMessageRepository emailMessageRepository;
    private final CollectionSchemaService collectionSchemaService;
    private final RecordService recordService;

    /** The signals of an email's sender; {@link ImportanceSignals#NONE} for unknown emails or senders. */
    @Transactional
    public ImportanceSignals signalsFor(Long emailId) {
        EmailMessage email = emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId)).orElse(null);
        if (email == null || email.getFromAddress() == null || email.getFromAddress().isBlank()) {
            return ImportanceSignals.NONE;
        }
        String address = email.getFromAddress().strip();
        AppUser.AppUserId owner = email.getAccount().getOwner().getId();
        return new ImportanceSignals(null, isContact(address), emailMessageRepository.existsSentTo(owner, address),
                ReplyCandidateFilter.isAutomatedSender(address));
    }

    /** A People record with this address; false without a People collection or when its schema has no email filter. */
    private boolean isContact(String address) {
        Long people = collectionSchemaService.findAll().stream().filter(collection -> PEOPLE.equals(collection.getSlug()))
                .map(CollectionDto::getId).findFirst().orElse(null);
        if (people == null) {
            return false;
        }
        try {
            return recordService.listRecords(people, List.of("email:eq:" + address), null, null, 0, 1).getTotalElements() > 0;
        } catch (RuntimeException e) {
            log.debug("No contact lookup for the importance: {}", e.getMessage());
            return false;
        }
    }
}

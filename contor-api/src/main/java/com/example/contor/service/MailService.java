package com.example.contor.service;

import com.example.contor.api.model.EmailDto;
import com.example.contor.api.model.EmailLabelDto;
import com.example.contor.api.model.EmailPageDto;
import com.example.contor.api.model.EmailProcessingState;
import com.example.contor.api.model.MailAccountDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Label;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ProcessingState;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.mapper.MailMapper;
import com.example.contor.persistence.EmailLabelRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.EmailSpecifications;
import com.example.contor.persistence.LabelRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only access to the current user's mailboxes and emails (sync comes in a later phase). */
@RequiredArgsConstructor
@Service
public class MailService {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final MailAccountRepository mailAccountRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final EmailLabelRepository emailLabelRepository;
    private final LabelRepository labelRepository;

    @Transactional
    public List<MailAccountDto> listMailAccounts() {
        return mailAccountRepository.findByOwner_Id(currentUser()).stream()
                .map(MailMapper::toDto)
                .toList();
    }

    @Transactional
    public EmailPageDto listEmails(Long accountId, Long labelId, EmailProcessingState processingState, String q,
                                   Integer page, Integer size) {
        var criteria = new EmailSpecifications.Criteria(
                currentUser(),
                accountId != null ? new MailAccount.MailAccountId(accountId) : null,
                labelId != null ? new Label.LabelId(labelId) : null,
                processingState != null ? ProcessingState.valueOf(processingState.name()) : null,
                q);
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<EmailMessage> result = emailMessageRepository.findAll(EmailSpecifications.matching(criteria), pageable);

        Map<Long, List<EmailLabelDto>> labels = labelsOf(result.getContent().stream().map(email -> email.getId().id()).toList());
        return new EmailPageDto(
                new ArrayList<>(result.getContent().stream()
                        .map(email -> MailMapper.toSummaryDto(email, labels.getOrDefault(email.getId().id(), List.of())))
                        .toList()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public EmailDto getEmail(Long id) {
        EmailMessage email = emailMessageRepository.findById(new EmailMessage.EmailMessageId(id))
                .filter(candidate -> candidate.getAccount().getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new EmailNotFoundException(id));
        return MailMapper.toDto(email, labelsOf(List.of(id)).getOrDefault(id, List.of()));
    }

    /** Labels of several emails with two queries (no N+1). */
    private Map<Long, List<EmailLabelDto>> labelsOf(List<Long> emailIds) {
        if (emailIds.isEmpty()) {
            return Map.of();
        }
        List<EmailLabel> emailLabels = emailLabelRepository.findById_EmailIdIn(emailIds);
        if (emailLabels.isEmpty()) {
            return Map.of();
        }
        List<Label.LabelId> labelIds = emailLabels.stream().map(el -> new Label.LabelId(el.getId().labelId())).distinct().toList();
        Map<Long, Label> labelsById = labelRepository.findAllById(labelIds).stream()
                .collect(Collectors.toMap(label -> label.getId().id(), Function.identity()));
        Map<Long, List<EmailLabelDto>> byEmail = new HashMap<>();
        for (EmailLabel emailLabel : emailLabels) {
            Label label = labelsById.get(emailLabel.getId().labelId());
            if (label != null) {
                byEmail.computeIfAbsent(emailLabel.getId().emailId(), key -> new ArrayList<>()).add(MailMapper.toDto(emailLabel, label));
            }
        }
        return byEmail;
    }

    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
    }
}

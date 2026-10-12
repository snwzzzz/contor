package com.example.contor.service;

import com.example.contor.api.model.EvalCaseDto;
import com.example.contor.api.model.EvalCaseExportDto;
import com.example.contor.api.model.EvalEmailDto;
import com.example.contor.api.model.EvalExpectedDto;
import com.example.contor.api.model.EvalForbiddenDto;
import com.example.contor.api.model.EvalRecordDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the owner's emails and what happened to their record proposals into extraction eval cases, so real mail can be
 * labelled by correcting the findings in the UI. Applied CREATE_RECORD proposals (final payload, i.e. with the user's
 * corrections) are the expected records, rejected ones become {@code mustNotProduce}. The result is returned, never
 * stored: the owner saves it into the git-ignored {@code ai-eval/private/} folder.
 */
@RequiredArgsConstructor
@Service
public class AiEvalExportService {

    private static final Set<ProposalStatus> APPLIED = Set.of(ProposalStatus.APPROVED, ProposalStatus.EDITED_AND_APPROVED, ProposalStatus.AUTO_APPLIED);

    private final EmailMessageRepository emailMessageRepository;
    private final ProposalRepository proposalRepository;
    private final CollectionRepository collectionRepository;

    @Transactional
    public EvalCaseExportDto exportCases(List<Long> emailIds) {
        AppUser.AppUserId owner = new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
        List<EmailMessage.EmailMessageId> ids = emailIds.stream().distinct().map(EmailMessage.EmailMessageId::new).toList();
        Map<EmailMessage.EmailMessageId, EmailMessage> owned = new LinkedHashMap<>();
        emailMessageRepository.findAllById(ids).stream()
                .filter(email -> email.getAccount().getOwner().getId().equals(owner))
                .forEach(email -> owned.put(email.getId(), email));
        if (owned.isEmpty()) {
            return new EvalCaseExportDto(List.of());
        }

        Map<Long, String> slugs = new HashMap<>();
        for (Collection collection : collectionRepository.findAll()) {
            slugs.put(collection.getId().id(), collection.getSlug().value());
        }
        Map<EmailMessage.EmailMessageId, List<Proposal>> proposals = new HashMap<>();
        proposalRepository.findBySourceEmail_IdIn(List.copyOf(owned.keySet()))
                .forEach(proposal -> proposals.computeIfAbsent(proposal.getSourceEmail().getId(), key -> new ArrayList<>()).add(proposal));

        List<EvalCaseDto> cases = ids.stream().filter(owned::containsKey)
                .map(id -> toCase(owned.get(id), proposals.getOrDefault(id, List.of()), slugs)).toList();
        return new EvalCaseExportDto(cases);
    }

    private static EvalCaseDto toCase(EmailMessage email, List<Proposal> proposals, Map<Long, String> slugs) {
        Map<String, List<EvalRecordDto>> expected = new LinkedHashMap<>();
        List<EvalForbiddenDto> forbidden = new ArrayList<>();
        for (Proposal proposal : proposals) {
            if (proposal.getKind() != ProposalKind.CREATE_RECORD) {
                continue;
            }
            Map<String, Object> payload = Optional.ofNullable(proposal.getFinalPayload()).orElse(proposal.getPayload());
            String slug = payload.get("collectionId") instanceof Number id ? slugs.get(id.longValue()) : null;
            String title = payload.get("title") instanceof String text ? text : null;
            if (slug == null || title == null) {
                continue;
            }
            if (APPLIED.contains(proposal.getStatus())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> values = payload.get("values") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
                expected.computeIfAbsent(slug, key -> new ArrayList<>()).add(new EvalRecordDto(title, new LinkedHashMap<>(values)));
            } else if (proposal.getStatus() == ProposalStatus.REJECTED) {
                forbidden.add(new EvalForbiddenDto(slug).title(title));
            }
        }
        return new EvalCaseDto("email-" + email.getId().id(), toEmail(email), new EvalExpectedDto(expected), forbidden);
    }

    private static EvalEmailDto toEmail(EmailMessage email) {
        List<String> to = Optional.ofNullable(email.getToRecipients()).orElse(List.of()).stream().map(EmailRecipient::address).toList();
        return new EvalEmailDto(email.getAccount().getEmailAddress().value(), email.getFromAddress(), to,
                Optional.ofNullable(email.getSubject()).orElse(""), email.getReceivedAt().atOffset(ZoneOffset.UTC), AiAnalysisWorker.plainText(email))
                .fromName(email.getFromName());
    }
}

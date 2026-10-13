package com.example.contor.ai;

import com.example.contor.api.model.CollectionDto;

import java.util.List;

/** Turns an email into proposal drafts for the collections the AI may write to. */
public interface ExtractionService {

    /** @param ownerProfile who the user is (role, company, what it buys and sells), from the AI settings; {@code null} = unknown */
    List<ProposalDraft> analyze(EmailContent email, List<CollectionDto> targets, String ownerProfile);

    default List<ProposalDraft> analyze(EmailContent email, List<CollectionDto> targets) {
        return analyze(email, targets, null);
    }
}

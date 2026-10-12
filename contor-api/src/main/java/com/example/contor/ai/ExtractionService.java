package com.example.contor.ai;

import com.example.contor.api.model.CollectionDto;

import java.util.List;

/** Turns an email into proposal drafts for the collections the AI may write to. */
public interface ExtractionService {

    List<ProposalDraft> analyze(EmailContent email, List<CollectionDto> targets);
}

package com.example.contor.ai;

import com.example.contor.domain.EmailMessage;

import java.util.List;

/** Turns an email into proposal drafts (next phase). */
public interface ExtractionService {

    List<ProposalDraft> analyze(EmailMessage email);
}

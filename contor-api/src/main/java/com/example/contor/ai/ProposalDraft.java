package com.example.contor.ai;

import com.example.contor.domain.ProposalKind;

import java.math.BigDecimal;
import java.util.Map;

/** A proposal suggested by the AI before it is stored; payload formats are documented with the spec's ProposalAction. */
public record ProposalDraft(ProposalKind kind, Map<String, Object> payload, BigDecimal confidence, String reasoning) {
}

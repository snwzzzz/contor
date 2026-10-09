"use client";

import { Check, X } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";
import { useGetProposal } from "@/lib/api/generated/endpoints/proposals/proposals";
import { useGetRecord } from "@/lib/api/generated/endpoints/records/records";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import type { RelationTypeDto } from "@/lib/api/generated/model/relationTypeDto";
import { proposalTitle, type ProposalPayload } from "@/lib/proposal-format";

/** One end of a proposed relation: an existing record, or the record another proposal creates. */
export interface RelationEndpoint {
  collectionId?: number;
  recordId?: number;
  proposalId?: number;
}

export interface RelationPayload {
  relationTypeId?: number;
  from?: RelationEndpoint;
  to?: RelationEndpoint;
}

/** Which end the line is shown under: that record's card names the other end only. */
export type RelationAnchor = "from" | "to" | null;

/** Title of an end: from a pending record proposal of this list, else the stored record or the applied proposal. */
function useEndpointTitle(endpoint: RelationEndpoint | undefined, pending: Map<number, ProposalDto>): string {
  const proposed = endpoint?.proposalId != null ? pending.get(endpoint.proposalId) : undefined;
  const existing = endpoint?.collectionId != null && endpoint.recordId != null;
  const applied = endpoint?.proposalId != null && !proposed;
  const record = useGetRecord(endpoint?.collectionId ?? 0, endpoint?.recordId ?? 0, { query: { enabled: existing } });
  const appliedProposal = useGetProposal(endpoint?.proposalId ?? 0, { query: { enabled: applied } });

  if (proposed) return proposalTitle(proposed.payload as ProposalPayload);
  if (existing) return record.data?.title ?? `Record #${endpoint.recordId}`;
  if (applied && appliedProposal.data) return proposalTitle(appliedProposal.data.payload as ProposalPayload);
  return "…";
}

interface RelationLineProps {
  proposal: ProposalDto;
  types: Map<number, RelationTypeDto>;
  /** Pending record proposals of the list by id: a relation waits for them. */
  pending: Map<number, ProposalDto>;
  anchor: RelationAnchor;
  busy: boolean;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
}

/** A relation the assistant proposes: "works at Acme" under the person, or "Anna works at Acme" on its own. */
export function RelationLine({ proposal, types, pending, anchor, busy, onDecide }: RelationLineProps) {
  const payload = proposal.payload as RelationPayload;
  const type = payload.relationTypeId != null ? types.get(payload.relationTypeId) : undefined;
  const fromTitle = useEndpointTitle(payload.from, pending);
  const toTitle = useEndpointTitle(payload.to, pending);

  const forward = type?.name ?? "relates to";
  const text =
    anchor === "from" ? `${forward} ${toTitle}`
    : anchor === "to" ? `${type?.inverseName ?? forward} ${fromTitle}`
    : `${fromTitle} ${forward} ${toTitle}`;
  // A relation can only be kept once the records it connects exist.
  const waiting = [payload.from, payload.to].some((end) => end?.proposalId != null && pending.has(end.proposalId));

  return (
    <li className="flex items-center justify-between gap-2 text-sm">
      <span className="min-w-0 truncate" title={proposal.reasoning ?? undefined}>{text}</span>
      <span className="flex shrink-0 gap-0.5">
        <AppButton size="sm" variant="ghost" disabled={busy} aria-label={`Dismiss ${text}`} onClick={() => onDecide("REJECT")}>
          <X aria-hidden="true" data-icon="inline-start" />
        </AppButton>
        <AppButton
          size="sm"
          variant="ghost"
          disabled={waiting}
          loading={busy}
          aria-label={`Keep ${text}`}
          title={waiting ? "Keep the records first" : undefined}
          onClick={() => onDecide("APPROVE")}
        >
          {busy ? null : <Check aria-hidden="true" data-icon="inline-start" />}
        </AppButton>
      </span>
    </li>
  );
}

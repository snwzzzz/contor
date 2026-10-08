"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";

const PENDING = { status: "PENDING", size: 50 } as const;

interface Payload {
  collectionId?: number;
  recordId?: number;
  title?: string;
  name?: string;
  values?: Record<string, unknown>;
  fields?: { name?: string }[];
}

function proposalTitle(payload: Payload): string {
  return payload.title ?? payload.name ?? (payload.recordId != null ? `Record #${payload.recordId}` : "Proposal");
}

function formatValue(value: unknown): string {
  if (value && typeof value === "object" && "amount" in value) {
    const money = value as { amount: unknown; currency?: unknown };
    return `${String(money.amount)} ${String(money.currency ?? "")}`.trim();
  }
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}

/** Pending AI proposals: what each would change and why, with approve/reject. */
export function ProposalList() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useListProposals(PENDING);
  const { data: collections } = useListCollections();
  // A decision can create records, collections or both: refresh everything that may show them.
  const decide = useDecideProposal({ mutation: { onSuccess: () => queryClient.invalidateQueries() } });

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading proposals…</p>;
  }
  if (error) {
    return <ErrorMessage message={error.message} />;
  }
  if (!data || data.items.length === 0) {
    return (
      <EmptyState
        icon={Sparkles}
        title="Nothing to review"
        description="When the assistant finds something in your mail and you chose “Ask me”, it waits here for your decision."
      />
    );
  }

  const names = new Map(collections?.map((collection) => [collection.id, collection.name]));
  return (
    <div className="grid gap-3">
      <p className="text-sm text-muted-foreground">{data.totalElements} waiting for your decision</p>
      {data.items.map((proposal) => (
        <ProposalCard
          key={proposal.id}
          proposal={proposal}
          collectionName={names.get((proposal.payload as Payload).collectionId ?? -1)}
          busy={decide.isPending && decide.variables?.proposalId === proposal.id}
          onDecide={(decision) => decide.mutate({ proposalId: proposal.id, data: { decision } })}
        />
      ))}
    </div>
  );
}

interface ProposalCardProps {
  proposal: ProposalDto;
  collectionName: string | undefined;
  busy: boolean;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
}

function ProposalCard({ proposal, collectionName, busy, onDecide }: ProposalCardProps) {
  const payload = proposal.payload as Payload;
  const title = proposalTitle(payload);
  const target = collectionName ?? "a collection";
  const kind =
    proposal.kind === "CREATE_RECORD"
      ? `New record in ${target}`
      : proposal.kind === "UPDATE_RECORD"
        ? `Update in ${target}`
        : proposal.kind === "CREATE_COLLECTION"
          ? "New category"
          : proposal.kind;
  const values = Object.entries(payload.values ?? {});
  const fields = (payload.fields ?? []).map((field) => field.name).filter(Boolean).join(", ");

  return (
    <article aria-label={title} className="grid gap-3 rounded-lg border p-4">
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <div className="grid gap-0.5">
          <span className="text-xs text-muted-foreground">{kind}</span>
          <h2 className="font-medium">{title}</h2>
        </div>
        {proposal.confidence != null ? (
          <span className="text-xs text-muted-foreground">{Math.round(proposal.confidence * 100)}% sure</span>
        ) : null}
      </header>
      {values.length > 0 ? (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          {values.map(([key, value]) => (
            <div key={key} className="contents">
              <dt className="text-muted-foreground">{key}</dt>
              <dd className="break-words">{formatValue(value)}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      {fields ? <p className="text-sm text-muted-foreground">Fields: {fields}</p> : null}
      {proposal.reasoning ? <p className="text-sm italic">{proposal.reasoning}</p> : null}
      <div className="flex gap-2">
        <AppButton size="sm" loading={busy} aria-label={`Approve ${title}`} onClick={() => onDecide("APPROVE")}>
          Approve
        </AppButton>
        <AppButton size="sm" variant="ghost" disabled={busy} aria-label={`Reject ${title}`} onClick={() => onDecide("REJECT")}>
          Reject
        </AppButton>
      </div>
    </article>
  );
}

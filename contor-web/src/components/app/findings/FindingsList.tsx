"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check, ScanSearch, X } from "lucide-react";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { collectionTint } from "@/lib/collection-tint";
import { formatValue, humanize, proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";

const PENDING_FINDINGS = { status: "PENDING", group: "FINDINGS", size: 60 } as const;
const MAX_VALUES = 4;

function collectionOf(proposal: ProposalDto): number {
  return (proposal.payload as ProposalPayload).collectionId ?? -1;
}

/** What the assistant extracted from mail (people, organizations, job applications, …): keep or dismiss each. */
export function FindingsList() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useListProposals(PENDING_FINDINGS);
  const { data: collections } = useListCollections();
  const [filter, setFilter] = useState<number | null>(null);
  // Keeping a finding creates or changes a record: refresh everything that may show it.
  const decide = useDecideProposal({ mutation: { onSuccess: () => queryClient.invalidateQueries() } });

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading findings…</p>;
  }
  if (error) {
    return <ErrorMessage message={error.message} />;
  }
  if (!data || data.items.length === 0) {
    return (
      <EmptyState
        icon={ScanSearch}
        title="No new findings"
        description="People, organizations and other records the assistant finds in your mail wait here until you keep them."
      />
    );
  }

  const names = new Map(collections?.map((collection) => [collection.id, collection.name]));
  const counts = new Map<number, number>();
  for (const proposal of data.items) counts.set(collectionOf(proposal), (counts.get(collectionOf(proposal)) ?? 0) + 1);
  const visible = filter === null ? data.items : data.items.filter((proposal) => collectionOf(proposal) === filter);

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap gap-1.5" aria-label="Filter by collection" role="group">
        <FilterChip label="All" count={data.items.length} active={filter === null} onClick={() => setFilter(null)} />
        {[...counts].map(([collectionId, count]) => (
          <FilterChip
            key={collectionId}
            label={names.get(collectionId) ?? "Other"}
            count={count}
            tint={collectionTint(collectionId)}
            active={filter === collectionId}
            onClick={() => setFilter(collectionId)}
          />
        ))}
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {visible.map((proposal) => (
          <FindingCard
            key={proposal.id}
            proposal={proposal}
            collectionName={names.get(collectionOf(proposal))}
            busy={decide.isPending && decide.variables?.proposalId === proposal.id}
            onDecide={(decision) => decide.mutate({ proposalId: proposal.id, data: { decision } })}
          />
        ))}
      </div>
    </div>
  );
}

interface FilterChipProps {
  label: string;
  count: number;
  tint?: string;
  active: boolean;
  onClick: () => void;
}

function FilterChip({ label, count, tint, active, onClick }: FilterChipProps) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs transition-colors hover:bg-accent",
        active && "border-foreground bg-foreground text-background hover:bg-foreground/90",
      )}
    >
      {tint ? <span className={cn("size-2 rounded-full", tint)} aria-hidden="true" /> : null}
      {label}{" "}
      <span className="tabular-nums opacity-60">{count}</span>
    </button>
  );
}

interface FindingCardProps {
  proposal: ProposalDto;
  collectionName: string | undefined;
  busy: boolean;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
}

function FindingCard({ proposal, collectionName, busy, onDecide }: FindingCardProps) {
  const payload = proposal.payload as ProposalPayload;
  const title = proposalTitle(payload);
  const collectionId = collectionOf(proposal);
  const values = Object.entries(payload.values ?? {})
    .filter(([, value]) => value !== null && value !== "")
    .slice(0, MAX_VALUES);

  return (
    <article
      aria-label={title}
      data-collection={collectionId}
      className={cn("flex flex-col overflow-hidden rounded-lg border bg-card", busy && "opacity-60")}
    >
      <div className={cn("flex items-center justify-between gap-2 px-4 py-1.5 text-xs font-medium", collectionTint(collectionId))}>
        <span className="truncate">{collectionName ?? "Collection"}</span>
        <span className="opacity-80">{proposal.kind === "UPDATE_RECORD" ? "Update" : "New"}</span>
      </div>
      <div className="grid flex-1 content-start gap-2 p-4">
        <h2 className="truncate font-medium">{title}</h2>
        {values.length > 0 ? (
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-sm">
            {values.map(([key, value]) => (
              <div key={key} className="contents">
                <dt className="text-muted-foreground">{humanize(key)}</dt>
                <dd className="truncate">{formatValue(value)}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        {proposal.reasoning ? <p className="line-clamp-2 text-xs text-muted-foreground">{proposal.reasoning}</p> : null}
      </div>
      <div className="flex justify-end gap-1 border-t px-2 py-1.5">
        <AppButton size="sm" variant="ghost" disabled={busy} aria-label={`Dismiss ${title}`} onClick={() => onDecide("REJECT")}>
          <X aria-hidden="true" data-icon="inline-start" />
          Dismiss
        </AppButton>
        <AppButton size="sm" variant="ghost" loading={busy} aria-label={`Keep ${title}`} onClick={() => onDecide("APPROVE")}>
          {busy ? null : <Check aria-hidden="true" data-icon="inline-start" />}
          Keep
        </AppButton>
      </div>
    </article>
  );
}

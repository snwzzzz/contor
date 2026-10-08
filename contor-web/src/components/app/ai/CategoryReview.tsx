"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AppButton } from "@/components/app/AppButton";
import { getListProposalsQueryKey, useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";

const PENDING = { status: "PENDING", size: 200 } as const;

interface CategoryPayload {
  name?: string;
  description?: string;
  fields?: { name?: string }[];
}

/** Pending CREATE_COLLECTION proposals from the discovery: create or skip each one. */
export function CategoryReview() {
  const queryClient = useQueryClient();
  const { data } = useListProposals(PENDING);
  const decide = useDecideProposal({
    mutation: { onSuccess: () => queryClient.invalidateQueries({ queryKey: getListProposalsQueryKey() }) },
  });
  const categories = data?.items.filter((proposal) => proposal.kind === "CREATE_COLLECTION") ?? [];

  if (categories.length === 0) {
    return <p className="text-sm text-muted-foreground">All categories are decided. Continue to analyze your emails.</p>;
  }
  return (
    <ul className="grid gap-3">
      {categories.map((proposal) => (
        <CategoryItem
          key={proposal.id}
          proposal={proposal}
          busy={decide.isPending && decide.variables?.proposalId === proposal.id}
          onDecide={(decision) => decide.mutate({ proposalId: proposal.id, data: { decision } })}
        />
      ))}
    </ul>
  );
}

function CategoryItem({ proposal, busy, onDecide }: { proposal: ProposalDto; busy: boolean; onDecide: (decision: "APPROVE" | "REJECT") => void }) {
  const payload = proposal.payload as CategoryPayload;
  const name = payload.name ?? "Category";
  const fields = (payload.fields ?? []).map((field) => field.name).filter(Boolean).join(", ");
  return (
    <li className="grid gap-2 rounded-lg border p-4">
      <div className="grid gap-0.5">
        <span className="font-medium">{name}</span>
        {payload.description ? <span className="text-sm text-muted-foreground">{payload.description}</span> : null}
      </div>
      {fields ? <span className="text-xs text-muted-foreground">{fields}</span> : null}
      {proposal.reasoning ? <span className="text-sm italic">{proposal.reasoning}</span> : null}
      <div className="flex gap-2">
        <AppButton size="sm" loading={busy} aria-label={`Create ${name}`} onClick={() => onDecide("APPROVE")}>
          Create
        </AppButton>
        <AppButton size="sm" variant="ghost" disabled={busy} aria-label={`Skip ${name}`} onClick={() => onDecide("REJECT")}>
          Skip
        </AppButton>
      </div>
    </li>
  );
}

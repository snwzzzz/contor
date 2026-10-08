"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Archive, CalendarPlus, FolderPlus, Link2, ListPlus, PencilLine, Reply, Sparkles, Tag, type LucideIcon } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { tintClass, type Tint } from "@/lib/collection-tint";
import { formatValue, proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";

const PENDING_ACTIONS = { status: "PENDING", group: "ACTIONS", size: 50 } as const;
const PREVIEW_VALUES = 3;

/** What the action does, in words, with its icon. Record actions depend on the target collection. */
function describe(proposal: ProposalDto, slug: string | undefined): { label: string; icon: LucideIcon; tint?: Tint } {
  switch (proposal.kind) {
    case "CREATE_RECORD":
      return slug === "events" ? { label: "Add to calendar", icon: CalendarPlus, tint: 1 }
        : slug === "tasks" ? { label: "New to-do", icon: ListPlus, tint: 3 }
          : { label: "New record", icon: ListPlus };
    case "UPDATE_RECORD":
      return slug === "events" ? { label: "Change calendar entry", icon: PencilLine, tint: 1 }
        : slug === "tasks" ? { label: "Update to-do", icon: PencilLine, tint: 3 }
          : { label: "Update record", icon: PencilLine };
    case "CREATE_COLLECTION":
      return { label: "New category", icon: FolderPlus, tint: 6 };
    case "DRAFT_REPLY":
      return { label: "Draft reply", icon: Reply, tint: 5 };
    case "SET_LABEL":
    case "CREATE_LABEL":
      return { label: proposal.kind === "SET_LABEL" ? "Label email" : "New label", icon: Tag, tint: 2 };
    case "ARCHIVE_EMAIL":
      return { label: "Archive email", icon: Archive };
    case "CREATE_RELATION":
      return { label: "Link records", icon: Link2 };
    default:
      return { label: "Suggestion", icon: Sparkles };
  }
}

/** Things the assistant proposes to do (calendar entries, to-dos, categories, later replies): approve or reject. */
export function ProposalList() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useListProposals(PENDING_ACTIONS);
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
        description="When the assistant wants to add something to your calendar or to-dos and you chose “Ask me”, it waits here."
      />
    );
  }

  const slugs = new Map(collections?.map((collection) => [collection.id, collection.slug]));
  return (
    <div className="grid max-w-3xl gap-3">
      <p className="text-sm text-muted-foreground">{data.totalElements} waiting for your decision</p>
      {data.items.map((proposal) => (
        <ActionCard
          key={proposal.id}
          proposal={proposal}
          slug={slugs.get((proposal.payload as ProposalPayload).collectionId ?? -1)}
          busy={decide.isPending && decide.variables?.proposalId === proposal.id}
          onDecide={(decision) => decide.mutate({ proposalId: proposal.id, data: { decision } })}
        />
      ))}
    </div>
  );
}

interface ActionCardProps {
  proposal: ProposalDto;
  slug: string | undefined;
  busy: boolean;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
}

function ActionCard({ proposal, slug, busy, onDecide }: ActionCardProps) {
  const payload = proposal.payload as ProposalPayload;
  const title = proposalTitle(payload);
  const { label, icon: Icon, tint } = describe(proposal, slug);
  const preview = [
    ...Object.values(payload.values ?? {}).filter((value) => value !== null && value !== "").map(formatValue),
    ...(payload.fields ?? []).map((field) => field.name).filter((name): name is string => Boolean(name)),
  ].slice(0, PREVIEW_VALUES);

  return (
    <article aria-label={title} className="grid gap-2 rounded-lg border bg-card p-4">
      <div className="flex items-start gap-3">
        <span className={cn("flex size-8 shrink-0 items-center justify-center rounded-md", tint ? tintClass(tint) : "bg-muted")} aria-hidden="true">
          <Icon className="size-4" />
        </span>
        <div className="grid min-w-0 flex-1 gap-0.5">
          <span className="text-xs text-muted-foreground">{label}</span>
          <h2 className="truncate font-medium">{title}</h2>
          {preview.length > 0 ? <p className="truncate text-sm text-muted-foreground">{preview.join(" · ")}</p> : null}
        </div>
        {proposal.confidence != null ? (
          <span className="shrink-0 text-xs tabular-nums text-muted-foreground">{Math.round(proposal.confidence * 100)}%</span>
        ) : null}
      </div>
      {proposal.reasoning ? <p className="line-clamp-2 pl-11 text-sm text-muted-foreground">{proposal.reasoning}</p> : null}
      <div className="flex justify-end gap-2">
        <AppButton size="sm" variant="ghost" disabled={busy} aria-label={`Reject ${title}`} onClick={() => onDecide("REJECT")}>
          Reject
        </AppButton>
        <AppButton size="sm" loading={busy} aria-label={`Approve ${title}`} onClick={() => onDecide("APPROVE")}>
          Approve
        </AppButton>
      </div>
    </article>
  );
}

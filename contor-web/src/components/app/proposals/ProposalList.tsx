"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Archive, CalendarPlus, FolderPlus, Link2, ListPlus, PencilLine, Reply, Sparkles, Tag, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FilterChip } from "@/components/app/FilterChip";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { tintClass, type Tint } from "@/lib/collection-tint";
import { formatValue, humanize, proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";

const PENDING_ACTIONS = { status: "PENDING", group: "ACTIONS", size: 50 } as const;
const PREVIEW_VALUES = 4;

/** Filter groups, in the order of the chips. */
const GROUPS = ["Calendar", "To-dos", "Categories", "Replies", "Labels", "Email", "Records", "Other"] as const;
type Group = (typeof GROUPS)[number];

interface Description {
  label: string;
  icon: LucideIcon;
  group: Group;
  tint?: Tint;
}

/** What the action does, in words, with its icon. Record actions depend on the target collection. */
function describe(proposal: ProposalDto, slug: string | undefined): Description {
  switch (proposal.kind) {
    case "CREATE_RECORD":
      return slug === "events" ? { label: "Add to calendar", icon: CalendarPlus, group: "Calendar", tint: 1 }
        : slug === "tasks" ? { label: "New to-do", icon: ListPlus, group: "To-dos", tint: 3 }
          : { label: "New record", icon: ListPlus, group: "Records" };
    case "UPDATE_RECORD":
      return slug === "events" ? { label: "Change calendar entry", icon: PencilLine, group: "Calendar", tint: 1 }
        : slug === "tasks" ? { label: "Update to-do", icon: PencilLine, group: "To-dos", tint: 3 }
          : { label: "Update record", icon: PencilLine, group: "Records" };
    case "CREATE_COLLECTION":
      return { label: "New category", icon: FolderPlus, group: "Categories", tint: 6 };
    case "DRAFT_REPLY":
      return { label: "Draft reply", icon: Reply, group: "Replies", tint: 5 };
    case "SET_LABEL":
    case "CREATE_LABEL":
      return { label: proposal.kind === "SET_LABEL" ? "Label email" : "New label", icon: Tag, group: "Labels", tint: 2 };
    case "ARCHIVE_EMAIL":
      return { label: "Archive email", icon: Archive, group: "Email" };
    case "CREATE_RELATION":
      return { label: "Link records", icon: Link2, group: "Records" };
    default:
      return { label: "Suggestion", icon: Sparkles, group: "Other" };
  }
}

/** Things the assistant proposes to do (calendar entries, to-dos, categories, later replies): approve or reject. */
export function ProposalList() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useListProposals(PENDING_ACTIONS);
  const { data: collections } = useListCollections();
  const [filter, setFilter] = useState<Group | null>(null);
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
  const described = data.items.map((proposal) => ({
    proposal,
    description: describe(proposal, slugs.get((proposal.payload as ProposalPayload).collectionId ?? -1)),
  }));
  const counts = new Map<Group, number>();
  for (const { description } of described) counts.set(description.group, (counts.get(description.group) ?? 0) + 1);
  const visible = filter === null ? described : described.filter(({ description }) => description.group === filter);

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">{data.totalElements} waiting for your decision</p>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by kind">
          <FilterChip label="All" count={described.length} active={filter === null} onClick={() => setFilter(null)} />
          {GROUPS.filter((group) => counts.has(group)).map((group) => (
            <FilterChip key={group} label={group} count={counts.get(group) ?? 0} active={filter === group} onClick={() => setFilter(group)} />
          ))}
        </div>
      </div>
      {/* As many columns as fit; each card only as tall as its content (no blank space under short cards). */}
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,22rem),1fr))] items-start gap-4">
        {visible.map(({ proposal, description }) => (
          <ActionCard
            key={proposal.id}
            proposal={proposal}
            description={description}
            busy={decide.isPending && decide.variables?.proposalId === proposal.id}
            onDecide={(decision) => decide.mutate({ proposalId: proposal.id, data: { decision } })}
          />
        ))}
      </div>
    </div>
  );
}

/** What the proposal would store: its values ("Location: Acme HQ"), or the fields of a new category. */
function previewOf(payload: ProposalPayload): [string, string][] {
  const values = Object.entries(payload.values ?? {})
    .filter(([, value]) => value !== null && value !== "")
    .map(([key, value]): [string, string] => [humanize(key), formatValue(value)]);
  const fields = (payload.fields ?? []).map((field) => field.name).filter((name): name is string => Boolean(name));
  return [...values, ...(fields.length > 0 ? [["Fields", fields.join(", ")] as [string, string]] : [])].slice(0, PREVIEW_VALUES);
}

interface ActionCardProps {
  proposal: ProposalDto;
  description: Description;
  busy: boolean;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
}

function ActionCard({ proposal, description, busy, onDecide }: ActionCardProps) {
  const payload = proposal.payload as ProposalPayload;
  const title = proposalTitle(payload);
  const { label, icon: Icon, tint } = description;
  const preview = previewOf(payload);
  const confidence = proposal.confidence != null ? Math.round(proposal.confidence * 100) : null;

  return (
    <article
      aria-label={title}
      className={cn("flex flex-col overflow-hidden rounded-xl border bg-card transition-shadow hover:shadow-sm", busy && "opacity-60")}
    >
      <div className="pb-4">
        <div className="flex items-start gap-3 p-4 pb-3">
          <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-lg", tint ? tintClass(tint) : "bg-muted")} aria-hidden="true">
            <Icon className="size-4" />
          </span>
          <div className="grid min-w-0 flex-1 gap-0.5">
            <span className="text-xs font-medium text-muted-foreground">{label}</span>
            <h2 className="line-clamp-2 leading-snug font-medium">{title}</h2>
          </div>
          {confidence !== null ? (
            <span className="flex shrink-0 flex-col items-end gap-1" title="How sure the assistant is">
              <span className="text-xs tabular-nums text-muted-foreground">{confidence}%</span>
              <span className="h-1 w-10 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                <span className="block h-full rounded-full bg-primary" style={{ width: `${confidence}%` }} />
              </span>
            </span>
          ) : null}
        </div>

        {preview.length > 0 ? (
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 px-4 text-sm">
            {preview.map(([key, value]) => (
              <div key={key} className="contents">
                <dt className="text-muted-foreground">{key}</dt>
                <dd className="truncate">{value}</dd>
              </div>
            ))}
          </dl>
        ) : null}

        {proposal.reasoning ? (
          <p className="mx-4 mt-3 line-clamp-3 rounded-lg bg-muted/50 px-3 py-2 text-xs leading-relaxed text-muted-foreground">{proposal.reasoning}</p>
        ) : null}
      </div>

      <div className="flex justify-end gap-2 border-t px-3 py-2">
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

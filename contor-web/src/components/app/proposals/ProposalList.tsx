"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { KindLabel } from "@/components/app/KindLabel";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FilterChip } from "@/components/app/FilterChip";
import { ImportanceBadge } from "@/components/app/proposals/ImportanceBadge";
import { InsightCard } from "@/components/app/proposals/InsightCard";
import { ProposalDetail } from "@/components/app/proposals/ProposalDetail";
import { ReplySuggestionCard } from "@/components/app/replies/ReplySuggestionCard";
import { getInsightRun, requestInsights } from "@/lib/api/generated/endpoints/ai/ai";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import type { ProposalPriority } from "@/lib/api/generated/model/proposalPriority";
import { CARD_HOVER, STRETCHED_BUTTON } from "@/lib/card-hover";
import { describeProposal, GROUPS, type Description, type Group } from "@/lib/proposal-describe";
import { formatValue, humanize, proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";

const PENDING_ACTIONS = { status: "PENDING", group: "ACTIONS", size: 50 } as const;
const PREVIEW_VALUES = 4;
/** Importance filters, as `?importance=` in the URL. */
const IMPORTANCE_FILTERS: { param: string; value: ProposalPriority; label: string }[] = [
  { param: "high", value: "HIGH", label: "High and up" },
  { param: "urgent", value: "URGENT", label: "Urgent" },
];

interface View {
  minImportance: ProposalPriority | null;
  byImportance: boolean;
}

function viewFrom(params: URLSearchParams): View {
  return {
    minImportance: IMPORTANCE_FILTERS.find((filter) => filter.param === params.get("importance"))?.value ?? null,
    byImportance: params.get("sort") === "importance",
  };
}

/**
 * Things the assistant proposes to do (calendar entries, to-dos, categories, reply drafts): approve or reject. Can be
 * narrowed to the important ones and sorted by importance; both are kept in the URL (`?importance=high&sort=importance`).
 */
export function ProposalList() {
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  // The URL is read once; afterwards the state leads and the URL follows (so a reload keeps the view).
  const [view, setView] = useState<View>(() => viewFrom(searchParams));
  const { data, isLoading, error } = useListProposals({
    ...PENDING_ACTIONS,
    ...(view.minImportance ? { minImportance: view.minImportance } : {}),
    ...(view.byImportance ? { sort: "IMPORTANCE" as const } : {}),
  });
  const { data: collections } = useListCollections();
  const [filter, setFilter] = useState<Group | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  // A decision can create records, collections or both: refresh everything that may show them.
  const decide = useDecideProposal({ mutation: { onSuccess: () => queryClient.invalidateQueries() } });
  const insights = useInsightSearch();

  function changeView(next: View) {
    setView(next);
    const params = new URLSearchParams(searchParams);
    const importance = IMPORTANCE_FILTERS.find((filter) => filter.value === next.minImportance)?.param;
    if (importance) params.set("importance", importance);
    else params.delete("importance");
    if (next.byImportance) params.set("sort", "importance");
    else params.delete("sort");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading proposals…</p>;
  }
  if (error) {
    return <ErrorMessage message={error.message} />;
  }
  if ((!data || data.items.length === 0) && view.minImportance) {
    return (
      <EmptyState
        icon={Sparkles}
        title="Nothing this important is waiting"
        description="Less important proposals are hidden by the filter."
        action={
          <AppButton variant="outline" onClick={() => changeView({ ...view, minImportance: null })}>
            Show all proposals
          </AppButton>
        }
      />
    );
  }
  if (!data || data.items.length === 0) {
    return (
      <EmptyState
        icon={Sparkles}
        title="Nothing to review"
        description="When the assistant wants to add something to your calendar or to-dos and you chose “Ask me”, it waits here."
        action={<LookForInsights search={insights} />}
      />
    );
  }

  const slugs = new Map(collections?.map((collection) => [collection.id, collection.slug]));
  const described = data.items.map((proposal) => ({
    proposal,
    description: describeProposal(proposal, slugs.get((proposal.payload as ProposalPayload).collectionId ?? -1)),
  }));
  const counts = new Map<Group, number>();
  for (const { description } of described) counts.set(description.group, (counts.get(description.group) ?? 0) + 1);
  const selected = described.find(({ proposal }) => proposal.id === selectedId);
  const visible = filter === null ? described : described.filter(({ description }) => description.group === filter);

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <p className="text-sm text-muted-foreground">{data.totalElements} waiting for your decision</p>
          <LookForInsights search={insights} />
        </div>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by kind">
          <FilterChip label="All" count={described.length} active={filter === null} onClick={() => setFilter(null)} />
          {GROUPS.filter((group) => counts.has(group)).map((group) => (
            <FilterChip key={group} label={group} count={counts.get(group) ?? 0} active={filter === group} onClick={() => setFilter(group)} />
          ))}
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by importance">
          {IMPORTANCE_FILTERS.map((option) => (
            <FilterChip
              key={option.value}
              label={option.label}
              active={view.minImportance === option.value}
              onClick={() => changeView({ ...view, minImportance: view.minImportance === option.value ? null : option.value })}
            />
          ))}
        </div>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Order">
          <FilterChip label="Newest first" active={!view.byImportance} onClick={() => changeView({ ...view, byImportance: false })} />
          <FilterChip label="Most important first" active={view.byImportance} onClick={() => changeView({ ...view, byImportance: true })} />
        </div>
      </div>
      {/* As many columns as fit; the cards of a row stretch to the same height (long text is clamped). */}
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,22rem),1fr))] gap-4">
        {visible.map(({ proposal, description }) =>
          proposal.kind === "DRAFT_REPLY" ? (
            <ReplySuggestionCard key={proposal.id} proposal={proposal} onOpen={() => setSelectedId(proposal.id)} />
          ) : proposal.kind === "INSIGHT" ? (
            <InsightCard key={proposal.id} proposal={proposal} />
          ) : (
            <ActionCard
              key={proposal.id}
              proposal={proposal}
              description={description}
              onOpen={() => setSelectedId(proposal.id)}
              busy={decide.isPending && decide.variables?.proposalId === proposal.id}
              onDecide={(decision) => decide.mutate({ proposalId: proposal.id, data: { decision } })}
            />
          ),
        )}
      </div>
      {selected ? (
        <ProposalDetail
          proposal={selected.proposal}
          description={selected.description}
          collection={collections?.find((collection) => collection.id === (selected.proposal.payload as ProposalPayload).collectionId)}
          busy={decide.isPending && decide.variables?.proposalId === selected.proposal.id}
          onClose={() => setSelectedId(null)}
          onDecide={(decision, payload) =>
            decide.mutate(
              { proposalId: selected.proposal.id, data: payload ? { decision, payload } : { decision } },
              { onSuccess: () => setSelectedId(null) },
            )
          }
        />
      ) : null}
    </div>
  );
}

interface InsightSearch {
  looking: boolean;
  start: () => void;
}

const INSIGHT_POLL_MS = 3000;

/** Starts an insight pass and resolves once the backend says it is done (the pass runs on the AI thread). */
async function searchInsights(): Promise<void> {
  let run = await requestInsights();
  while (run.running) {
    await new Promise((resolve) => setTimeout(resolve, INSIGHT_POLL_MS));
    run = await getInsightRun();
  }
}

/** "Look for insights": one mutation that starts the pass, waits for it and then refreshes the proposals. */
function useInsightSearch(): InsightSearch {
  const queryClient = useQueryClient();
  const search = useMutation({ mutationFn: searchInsights, onSuccess: () => queryClient.invalidateQueries() });
  return { looking: search.isPending, start: () => search.mutate() };
}

function LookForInsights({ search }: { search: InsightSearch }) {
  return (
    <AppButton size="sm" variant="outline" disabled={search.looking} onClick={search.start}>
      {search.looking ? "Looking for insights…" : "Look for insights"}
    </AppButton>
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
  onOpen: () => void;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
}

function ActionCard({ proposal, description, busy, onOpen, onDecide }: ActionCardProps) {
  const payload = proposal.payload as ProposalPayload;
  const title = proposalTitle(payload);
  const { label, icon: Icon, tint } = description;
  const preview = previewOf(payload);
  const confidence = proposal.confidence != null ? Math.round(proposal.confidence * 100) : null;

  return (
    <article
      aria-label={title}
      className={cn("relative flex h-full flex-col overflow-hidden rounded-xl border bg-card", CARD_HOVER, busy && "opacity-60")}
    >
      <div className="flex-1 pb-4">
        <div className="flex items-start gap-3 p-4 pb-3">
          <div className="grid min-w-0 flex-1 gap-1">
            <KindLabel icon={Icon} tint={tint}>
              {label}
              <ImportanceBadge importance={proposal.importance} reason={proposal.importanceReason} />
            </KindLabel>
            <h2 className="line-clamp-2 leading-snug font-medium">
              {/* Stretched over the whole card; the decision buttons sit above it. */}
              <button type="button" aria-label={`Show details for ${title}`} onClick={onOpen} className={STRETCHED_BUTTON}>
                {title}
              </button>
            </h2>
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
          <p className="mx-4 mt-3 line-clamp-3 text-xs leading-relaxed text-muted-foreground">{proposal.reasoning}</p>
        ) : null}
      </div>

      <div className="relative z-10 mt-auto flex justify-end gap-2 border-t px-3 py-2">
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

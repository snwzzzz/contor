"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check, CheckCheck, ScanSearch, X } from "lucide-react";
import { Children, useState, type ReactNode } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FilterChip } from "@/components/app/FilterChip";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useListRelationTypes } from "@/lib/api/generated/endpoints/relations/relations";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import type { RelationTypeDto } from "@/lib/api/generated/model/relationTypeDto";
import { collectionTint } from "@/lib/collection-tint";
import { formatValue, humanize, proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";
import { RelationLine, type RelationAnchor, type RelationPayload } from "./RelationLine";

const PENDING_FINDINGS = { status: "PENDING", group: "FINDINGS", size: 60 } as const;
const MAX_VALUES = 4;

function collectionOf(proposal: ProposalDto): number {
  return (proposal.payload as ProposalPayload).collectionId ?? -1;
}

const isRelation = (proposal: ProposalDto) => proposal.kind === "CREATE_RELATION";

/** Filter: everything, the record cards of one collection, or only the connections between existing records. */
type Filter = number | "connections" | null;

/** The pending record proposal a relation is shown under: the one it starts from, else the one it ends at. */
function anchorOf(relation: ProposalDto, records: Map<number, ProposalDto>): { record: ProposalDto; anchor: "from" | "to" } | null {
  const { from, to } = relation.payload as RelationPayload;
  const start = from?.proposalId != null ? records.get(from.proposalId) : undefined;
  if (start) return { record: start, anchor: "from" };
  const end = to?.proposalId != null ? records.get(to.proposalId) : undefined;
  return end ? { record: end, anchor: "to" } : null;
}

/** What the assistant extracted from mail (people, organizations, job applications, …): keep or dismiss each. */
export function FindingsList() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useListProposals(PENDING_FINDINGS);
  const { data: collections } = useListCollections();
  const { data: relationTypes } = useListRelationTypes();
  const [filter, setFilter] = useState<Filter>(null);
  const [bulkType, setBulkType] = useState<number | null>(null);
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
  const slugs = new Map(collections?.map((collection) => [collection.id, collection.slug]));
  const types = new Map((relationTypes ?? []).map((type) => [type.id, type]));
  const records = data.items.filter((proposal) => !isRelation(proposal));
  const pending = new Map(records.map((proposal) => [proposal.id, proposal]));
  const counts = new Map<number, number>();
  for (const proposal of records) counts.set(collectionOf(proposal), (counts.get(collectionOf(proposal)) ?? 0) + 1);
  const visible = filter === null ? records : filter === "connections" ? [] : records.filter((proposal) => collectionOf(proposal) === filter);

  // Relations hang under a record card; those between records that already exist stand on their own.
  const nested = new Map<number, { relation: ProposalDto; anchor: RelationAnchor }[]>();
  const standalone: ProposalDto[] = [];
  for (const relation of data.items.filter(isRelation)) {
    const placed = anchorOf(relation, pending);
    if (!placed) standalone.push(relation);
    else nested.set(placed.record.id, [...(nested.get(placed.record.id) ?? []), { relation, anchor: placed.anchor }]);
  }
  // Standalone connections grouped by relation type ("attends", "works at"), largest group first.
  const groups = [...Map.groupBy(standalone, (relation) => (relation.payload as RelationPayload).relationTypeId ?? -1)]
    .toSorted(([, a], [, b]) => b.length - a.length);
  const busyId = decide.isPending ? decide.variables?.proposalId : undefined;

  /** One decision after the other, so each refresh sees the previous one. */
  async function decideAll(typeId: number, relations: ProposalDto[], decision: "APPROVE" | "REJECT") {
    setBulkType(typeId);
    try {
      for (const relation of relations) await decide.mutateAsync({ proposalId: relation.id, data: { decision } });
    } catch {
      // The failed decision was reported by the global mutation error toast; the rest stays pending.
    } finally {
      setBulkType(null);
    }
  }
  const lineProps = (relation: ProposalDto, anchor: RelationAnchor) => ({
    proposal: relation,
    types,
    pending,
    anchor,
    busy: busyId === relation.id,
    onDecide: (decision: "APPROVE" | "REJECT") => decide.mutate({ proposalId: relation.id, data: { decision } }),
  });

  const showConnections = (filter === null || filter === "connections") && standalone.length > 0;

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap gap-1.5" aria-label="Filter by collection" role="group">
        <FilterChip label="All" count={records.length + standalone.length} active={filter === null} onClick={() => setFilter(null)} />
        {[...counts].map(([collectionId, count]) => (
          <FilterChip
            key={collectionId}
            label={names.get(collectionId) ?? "Other"}
            count={count}
            tint={collectionTint(collectionId, slugs.get(collectionId))}
            active={filter === collectionId}
            onClick={() => setFilter(collectionId)}
          />
        ))}
        {standalone.length > 0 ? (
          <FilterChip label="Connections" count={standalone.length} active={filter === "connections"} onClick={() => setFilter("connections")} />
        ) : null}
      </div>
      {visible.length > 0 ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {visible.map((proposal) => (
            <FindingCard
              key={proposal.id}
              proposal={proposal}
              collectionName={names.get(collectionOf(proposal))}
              collectionSlug={slugs.get(collectionOf(proposal))}
              busy={busyId === proposal.id}
              onDecide={(decision) => decide.mutate({ proposalId: proposal.id, data: { decision } })}
            >
              {(nested.get(proposal.id) ?? []).map(({ relation, anchor }) => (
                <RelationLine key={relation.id} {...lineProps(relation, anchor)} />
              ))}
            </FindingCard>
          ))}
        </div>
      ) : null}
      {showConnections ? (
        <section aria-label="New connections" className="grid gap-3">
          <div className="grid gap-0.5">
            <h2 className="text-sm font-medium">New connections</h2>
            <p className="text-xs text-muted-foreground">Links between records you already have. Keep the ones that are right.</p>
          </div>
          <div className="grid items-start gap-3 xl:grid-cols-2">
            {groups.map(([typeId, relations]) => (
              <ConnectionGroup
                key={typeId}
                type={types.get(typeId)}
                relations={relations}
                bulkBusy={bulkType === typeId}
                disabled={bulkType !== null}
                onDecideAll={(decision) => decideAll(typeId, relations, decision)}
              >
                {relations.map((relation) => (
                  <RelationLine key={relation.id} {...lineProps(relation, null)} />
                ))}
              </ConnectionGroup>
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}

interface ConnectionGroupProps {
  type: RelationTypeDto | undefined;
  relations: ProposalDto[];
  bulkBusy: boolean;
  disabled: boolean;
  onDecideAll: (decision: "APPROVE" | "REJECT") => void;
  /** The relation rows (`<li>`). */
  children: ReactNode;
}

/** Proposed connections of one kind ("attends"), with keep/dismiss for the whole group. */
function ConnectionGroup({ type, relations, bulkBusy, disabled, onDecideAll, children }: ConnectionGroupProps) {
  const name = type?.name ?? "relates to";
  return (
    <div className="overflow-hidden rounded-xl border bg-card">
      <div className="flex items-center justify-between gap-2 border-b bg-muted/30 px-4 py-2">
        <span className="flex items-center gap-2 text-sm font-medium">
          {name}
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-normal tabular-nums text-muted-foreground">{relations.length}</span>
        </span>
        {relations.length > 1 ? (
          <span className="flex gap-1">
            <AppButton size="sm" variant="ghost" disabled={disabled} aria-label={`Dismiss all ${relations.length} ${name}`} onClick={() => onDecideAll("REJECT")}>
              Dismiss all
            </AppButton>
            <AppButton
              size="sm"
              variant="outline"
              disabled={disabled && !bulkBusy}
              loading={bulkBusy}
              aria-label={`Keep all ${relations.length} ${name}`}
              onClick={() => onDecideAll("APPROVE")}
            >
              {bulkBusy ? null : <CheckCheck aria-hidden="true" data-icon="inline-start" />}
              Keep all
            </AppButton>
          </span>
        ) : null}
      </div>
      <ul aria-label={`New connections: ${name}`} className="divide-y">{children}</ul>
    </div>
  );
}

interface FindingCardProps {
  proposal: ProposalDto;
  collectionName: string | undefined;
  collectionSlug: string | undefined;
  busy: boolean;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
  /** Relation lines (`<li>`) shown under the values. */
  children?: ReactNode;
}

function FindingCard({ proposal, collectionName, collectionSlug, busy, onDecide, children }: FindingCardProps) {
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
      <div className={cn("flex items-center justify-between gap-2 px-4 py-1.5 text-xs font-medium", collectionTint(collectionId, collectionSlug))}>
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
        {Children.count(children) > 0 ? (
          <ul aria-label="Connections" className="mt-1 grid gap-0.5 border-t pt-2">{children}</ul>
        ) : null}
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

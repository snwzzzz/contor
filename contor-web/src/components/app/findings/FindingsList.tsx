"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check, CheckCheck, ListPlus, PencilLine, ScanSearch, X } from "lucide-react";
import { Children, useState, type ReactNode } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FilterChip } from "@/components/app/FilterChip";
import { PictureAvatar } from "@/components/app/mail/PictureAvatar";
import { domainOf } from "@/components/app/mail/mail-format";
import { ProposalDetail } from "@/components/app/proposals/ProposalDetail";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useListRelationTypes } from "@/lib/api/generated/endpoints/relations/relations";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import type { RelationTypeDto } from "@/lib/api/generated/model/relationTypeDto";
import { CARD_HOVER, STRETCHED_BUTTON } from "@/lib/card-hover";
import { collectionTint } from "@/lib/collection-tint";
import { useSenderPictures, isFreeMailDomain } from "@/lib/hooks/useSenderPictures";
import { formatValue, humanize, proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";
import { RelationLine, type RelationAnchor, type RelationPayload } from "./RelationLine";

const PENDING_FINDINGS = { status: "PENDING", group: "FINDINGS", size: 60 } as const;
const MAX_VALUES = 4;

function collectionOf(proposal: ProposalDto): number {
  return (proposal.payload as ProposalPayload).collectionId ?? -1;
}

const isRelation = (proposal: ProposalDto) => proposal.kind === "CREATE_RELATION";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const WEBSITE = /^(?:https?:\/\/)?(?:www\.)?([a-z0-9-]+(?:\.[a-z0-9-]+)+)(?:[/:?#]\S*)?$/i;

/** Where a finding's picture can come from: its first email address (contact photo), else a company domain (logo). */
function pictureKeys(proposal: ProposalDto): { address?: string; domain?: string } {
  const texts = Object.values((proposal.payload as ProposalPayload).values ?? {}).filter((value): value is string => typeof value === "string");
  const address = texts.find((text) => EMAIL.test(text.trim()))?.trim().toLowerCase();
  const mailDomain = domainOf(address);
  const website = texts.map((text) => WEBSITE.exec(text.trim())?.[1]).find((domain) => domain !== undefined && !EMAIL.test(domain));
  const domain = mailDomain && !isFreeMailDomain(mailDomain) ? mailDomain : website?.toLowerCase();
  return { address, domain };
}

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
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const keys = new Map((data?.items ?? []).filter((proposal) => !isRelation(proposal)).map((proposal) => [proposal.id, pictureKeys(proposal)]));
  const pictures = useSenderPictures([...keys.values()].map((key) => key.domain).filter((domain) => domain !== undefined));
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
  const selected = records.find((proposal) => proposal.id === selectedId);

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
        // As many columns as fit; the cards of a row stretch to the same height.
        <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,20rem),1fr))] gap-4">
          {visible.map((proposal) => (
            <FindingCard
              key={proposal.id}
              proposal={proposal}
              collectionName={names.get(collectionOf(proposal))}
              collectionSlug={slugs.get(collectionOf(proposal))}
              picture={pictures.contactPhoto(keys.get(proposal.id)?.address) ?? pictures.logo(keys.get(proposal.id)?.domain)}
              busy={busyId === proposal.id}
              onOpen={() => setSelectedId(proposal.id)}
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
      {selected ? (
        <ProposalDetail
          proposal={selected}
          description={{
            label: `${selected.kind === "UPDATE_RECORD" ? "Update in" : "New in"} ${names.get(collectionOf(selected)) ?? "a collection"}`,
            icon: selected.kind === "UPDATE_RECORD" ? PencilLine : ListPlus,
            group: "Records",
          }}
          collection={collections?.find((collection) => collection.id === collectionOf(selected))}
          busy={busyId === selected.id}
          approveLabel="Keep"
          rejectLabel="Dismiss"
          onClose={() => setSelectedId(null)}
          onDecide={(decision, payload) =>
            decide.mutate({ proposalId: selected.id, data: payload ? { decision, payload } : { decision } }, { onSuccess: () => setSelectedId(null) })
          }
        />
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
  /** Contact photo or company logo, if there is one. */
  picture: string | undefined;
  busy: boolean;
  onOpen: () => void;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
  /** Relation lines (`<li>`) shown under the values. */
  children?: ReactNode;
}

/**
 * One extracted record: picture (contact photo, company logo or initial), its collection as a colored chip with
 * New/Update, the values that say more than the title, the reasoning and how sure the assistant is.
 */
function FindingCard({ proposal, collectionName, collectionSlug, picture, busy, onOpen, onDecide, children }: FindingCardProps) {
  const payload = proposal.payload as ProposalPayload;
  const title = proposalTitle(payload);
  const collectionId = collectionOf(proposal);
  const update = proposal.kind === "UPDATE_RECORD";
  const confidence = proposal.confidence != null ? Math.round(proposal.confidence * 100) : null;
  const values = Object.entries(payload.values ?? {})
    .filter(([, value]) => value !== null && value !== "" && formatValue(value) !== title)
    .slice(0, MAX_VALUES);

  return (
    <article
      aria-label={title}
      data-collection={collectionId}
      className={cn("relative flex h-full flex-col overflow-hidden rounded-xl border bg-card", CARD_HOVER, busy && "opacity-60")}
    >
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start gap-3">
          <PictureAvatar label={title} src={picture} size={40} className="shrink-0" />
          <div className="grid min-w-0 flex-1 gap-1">
            <div className="flex min-w-0 items-center gap-1.5 text-xs">
              <span className={cn("truncate rounded-full px-2 py-0.5 font-medium", collectionTint(collectionId, collectionSlug))}>
                {collectionName ?? "Collection"}
              </span>
              <span className={cn("shrink-0 rounded-full px-1.5 py-0.5 font-medium", update ? "bg-warning/15 text-warning" : "bg-primary/10 text-primary")}>
                {update ? "Update" : "New"}
              </span>
            </div>
            <h2 className="line-clamp-2 leading-snug font-medium">
              {/* Stretched over the whole card; the buttons and connections sit above it. */}
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

        {values.length > 0 ? (
          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm">
            {values.map(([key, value]) => (
              <div key={key} className="contents">
                <dt className="text-muted-foreground">{humanize(key)}</dt>
                <dd className="truncate">{formatValue(value)}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        {proposal.reasoning ? (
          <p className="line-clamp-2 rounded-lg bg-muted/50 px-3 py-2 text-xs leading-relaxed text-muted-foreground">{proposal.reasoning}</p>
        ) : null}
        {Children.count(children) > 0 ? (
          <ul aria-label="Connections" className="relative z-10 grid gap-0.5 border-t pt-2">{children}</ul>
        ) : null}
      </div>
      <div className="relative z-10 mt-auto flex justify-end gap-2 border-t px-3 py-2">
        <AppButton size="sm" variant="ghost" disabled={busy} aria-label={`Dismiss ${title}`} onClick={() => onDecide("REJECT")}>
          <X aria-hidden="true" data-icon="inline-start" />
          Dismiss
        </AppButton>
        <AppButton size="sm" loading={busy} aria-label={`Keep ${title}`} onClick={() => onDecide("APPROVE")}>
          {busy ? null : <Check aria-hidden="true" data-icon="inline-start" />}
          Keep
        </AppButton>
      </div>
    </article>
  );
}

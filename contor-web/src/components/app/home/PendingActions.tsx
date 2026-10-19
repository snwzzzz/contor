"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check } from "lucide-react";
import Link from "next/link";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { ImportanceBadge } from "@/components/app/proposals/ImportanceBadge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useDecideProposal, useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalAction } from "@/lib/api/generated/model/proposalAction";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { describeProposal } from "@/lib/proposal-describe";
import { proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";
import { HomeSection, QuietEmpty, SectionLink } from "./HomeSection";
import { Pager, usePages } from "./Pager";

/** The twelve most important, three per page. */
const TOP_PENDING = { status: "PENDING", group: "ACTIONS", sort: "IMPORTANCE", size: 12 } as const;
const PER_PAGE = 3;
const PROPOSALS_HREF = "/proposals";

/** Kinds that need more than a yes (editing a draft, choosing follow-ups) are decided on the proposals page. */
const REVIEW_ONLY: ReadonlySet<ProposalAction> = new Set(["DRAFT_REPLY", "INSIGHT"]);

/** Draft replies carry the email subject instead of a title. */
type HomePayload = ProposalPayload & { subject?: string };

/** The most important proposals that wait for a decision, as rows with inline Approve and a link to review them. */
export function PendingActions() {
  const queryClient = useQueryClient();
  const { data, isPending, error } = useListProposals(TOP_PENDING);
  const { data: collections } = useListCollections();
  // Approving creates or changes records: refresh everything that may show them (counts, status line, agenda).
  const decide = useDecideProposal({
    mutation: { onSuccess: () => queryClient.invalidateQueries() },
  });
  const slugs = new Map(collections?.map((collection) => [collection.id, collection.slug]));
  const pages = usePages(data?.items ?? [], PER_PAGE);

  return (
    <HomeSection title="Pending actions" action={<SectionLink href={PROPOSALS_HREF}>View all</SectionLink>}>
      {error ? (
        <ErrorMessage message={error.message} />
      ) : isPending ? (
        <div className="grid gap-3 py-2" aria-label="Loading proposals">
          <Skeleton className="h-10" />
          <Skeleton className="h-10 w-4/5" />
        </div>
      ) : pages.items.length === 0 ? (
        <QuietEmpty icon={Check} title="Nothing waiting for you" description="New proposals from your mail appear here." />
      ) : (
        <>
          <ul className="-mx-2">
            {pages.items.map((proposal) => (
              <li key={proposal.id}>
                <ProposalRow
                  proposal={proposal}
                  slug={slugs.get((proposal.payload as HomePayload).collectionId ?? -1)}
                  approving={decide.isPending && decide.variables?.proposalId === proposal.id}
                  onApprove={() =>
                    decide.mutate({
                      proposalId: proposal.id,
                      data: { decision: "APPROVE" },
                    })
                  }
                />
              </li>
            ))}
          </ul>
          <Pager label="Proposals" {...pages} />
        </>
      )}
    </HomeSection>
  );
}

interface ProposalRowProps {
  proposal: ProposalDto;
  slug: string | undefined;
  approving: boolean;
  onApprove: () => void;
}

function ProposalRow({ proposal, slug, approving, onApprove }: ProposalRowProps) {
  const payload = proposal.payload as HomePayload;
  const description = describeProposal(proposal, slug);
  return (
    <article className={cn("flex items-start gap-3 rounded-md px-2 py-2.5", ROW_HOVER)}>
      <description.icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" strokeWidth={1.75} />
      <div className="grid min-w-0 flex-1 gap-0.5">
        <div className="flex min-w-0 items-center gap-2">
          <h3 className="truncate text-sm font-medium">{payload.subject || proposalTitle(payload)}</h3>
          <ImportanceBadge importance={proposal.importance} reason={proposal.importanceReason} />
        </div>
        <p className="line-clamp-2 text-xs text-muted-foreground">
          <span>{description.label}</span>
          {proposal.reasoning ? (
            <>
              <span aria-hidden="true"> · </span>
              <span>{proposal.reasoning}</span>
            </>
          ) : null}
        </p>
      </div>
      <div className="flex shrink-0 gap-1.5">
        {REVIEW_ONLY.has(proposal.kind) ? null : (
          <AppButton size="xs" loading={approving} onClick={onApprove}>
            Approve
          </AppButton>
        )}
        <Button asChild size="xs" variant="outline">
          <Link href={PROPOSALS_HREF}>Review</Link>
        </Button>
      </div>
    </article>
  );
}

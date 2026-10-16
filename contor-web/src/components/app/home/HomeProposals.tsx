"use client";

import Link from "next/link";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { ImportanceBadge } from "@/components/app/proposals/ImportanceBadge";
import { Skeleton } from "@/components/ui/skeleton";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useListProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import { CARD_HOVER } from "@/lib/card-hover";
import { tintClass } from "@/lib/collection-tint";
import { describeProposal } from "@/lib/proposal-describe";
import { proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";
import { HomeSection, SECTION_LINK } from "./HomeSection";

const TOP_PENDING = { status: "PENDING", group: "ACTIONS", sort: "IMPORTANCE", size: 3 } as const;
const PROPOSALS_HREF = "/proposals";

/** Draft replies carry the email subject instead of a title. */
type HomePayload = ProposalPayload & { subject?: string };

/** The most important proposals that wait for a decision; deciding happens on the proposals page. */
export function HomeProposals() {
  const { data, isPending, error } = useListProposals(TOP_PENDING);
  const { data: collections } = useListCollections();
  const slugs = new Map(collections?.map((collection) => [collection.id, collection.slug]));
  const proposals = data?.items ?? [];

  return (
    <HomeSection
      title="Actions"
      action={
        <Link href={PROPOSALS_HREF} className={SECTION_LINK}>
          View all
        </Link>
      }
    >
      {error ? (
        <ErrorMessage message={error.message} />
      ) : isPending ? (
        <div className="grid gap-3 sm:grid-cols-3" aria-label="Loading proposals">
          <Skeleton className="h-40 rounded-xl" />
          <Skeleton className="h-40 rounded-xl" />
          <Skeleton className="h-40 rounded-xl" />
        </div>
      ) : proposals.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">Nothing waiting for you</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-3">
          {proposals.map((proposal) => {
            const payload = proposal.payload as HomePayload;
            const description = describeProposal(proposal, slugs.get(payload.collectionId ?? -1));
            return (
              <li key={proposal.id}>
                {/* A simpler version of the proposal card: kind, title, why; deciding happens on the proposals page. */}
                <Link
                  href={PROPOSALS_HREF}
                  className={cn(
                    "flex h-full min-h-40 flex-col gap-3 rounded-xl border bg-card p-4 text-card-foreground shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring",
                    CARD_HOVER,
                  )}
                >
                  <span className="flex items-center gap-2">
                    <span
                      aria-hidden="true"
                      className={cn("flex size-8 shrink-0 items-center justify-center rounded-lg", description.tint ? tintClass(description.tint) : "bg-muted")}
                    >
                      <description.icon className="size-4" />
                    </span>
                    <span className="truncate text-xs text-muted-foreground">{description.label}</span>
                    <ImportanceBadge importance={proposal.importance} reason={proposal.importanceReason} className="ml-auto" />
                  </span>
                  <span className="grid gap-1">
                    <span className="line-clamp-2 text-base font-semibold leading-snug">{payload.subject || proposalTitle(payload)}</span>
                    {proposal.reasoning ? <span className="line-clamp-3 text-sm text-muted-foreground">{proposal.reasoning}</span> : null}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </HomeSection>
  );
}

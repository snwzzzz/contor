"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Lightbulb } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { useDecideProposal } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { CARD_HOVER } from "@/lib/card-hover";
import { tintClass } from "@/lib/collection-tint";
import { formatValue } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";
import { ImportanceBadge } from "./ImportanceBadge";

/** The INSIGHT payload (documented with the spec's ProposalAction). */
interface InsightPayload {
  title?: string;
  observation?: string;
  whyItMatters?: string;
  evidenceEmailIds?: number[];
  followUps?: InsightFollowUp[];
}

interface InsightFollowUp {
  collectionId: number;
  title: string;
  values?: Record<string, unknown>;
}

function followUpDate(followUp: InsightFollowUp): string | null {
  const value = followUp.values?.due_date ?? followUp.values?.starts_at;
  return value ? formatValue(value) : null;
}

/**
 * Something the assistant noticed across several emails: what, why it matters, the emails it is based on and what it
 * would add to your to-dos or calendar. Keep creates the follow-ups left ticked; Dismiss makes sure it never comes back.
 */
export function InsightCard({ proposal }: { proposal: ProposalDto }) {
  const queryClient = useQueryClient();
  const payload = proposal.payload as InsightPayload;
  const title = payload.title || "Insight";
  const followUps = payload.followUps ?? [];
  const [dropped, setDropped] = useState<ReadonlySet<number>>(new Set());
  // Keeping creates records; dismissing changes the proposals: refresh everything that may show them.
  const decide = useDecideProposal({ mutation: { onSuccess: () => queryClient.invalidateQueries() } });

  function toggle(index: number) {
    setDropped((current) => {
      const next = new Set(current);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  }

  function keep() {
    const kept = followUps.filter((_, index) => !dropped.has(index));
    decide.mutate({ proposalId: proposal.id, data: { decision: "APPROVE", payload: { ...proposal.payload, followUps: kept } } });
  }

  return (
    <article aria-label={title} className={cn("relative flex h-full flex-col overflow-hidden rounded-xl border bg-card", CARD_HOVER, decide.isPending && "opacity-60")}>
      <div className="grid flex-1 content-start gap-3 p-4">
        <div className="flex items-start gap-3">
          <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-lg", tintClass(4))} aria-hidden="true">
            <Lightbulb className="size-4" />
          </span>
          <div className="grid min-w-0 flex-1 gap-0.5">
            <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              Insight
              <ImportanceBadge importance={proposal.importance} reason={proposal.importanceReason} />
            </span>
            <h2 className="line-clamp-2 leading-snug font-medium">{title}</h2>
          </div>
        </div>
        {payload.observation ? <p className="text-sm leading-relaxed">{payload.observation}</p> : null}
        {payload.whyItMatters ? <p className="text-xs leading-relaxed text-muted-foreground">{payload.whyItMatters}</p> : null}
        {followUps.length > 0 ? (
          <fieldset className="grid gap-1.5">
            <legend className="mb-1 text-xs font-medium text-muted-foreground">Keeping it adds</legend>
            {followUps.map((followUp, index) => (
              <label key={`${followUp.collectionId}-${followUp.title}`} className="flex items-start gap-2 text-sm">
                <input
                  type="checkbox"
                  className="mt-0.5 size-4 accent-primary"
                  checked={!dropped.has(index)}
                  aria-label={followUp.title}
                  onChange={() => toggle(index)}
                />
                <span>
                  {followUp.title}
                  {followUpDate(followUp) ? <span className="text-muted-foreground"> · {followUpDate(followUp)}</span> : null}
                </span>
              </label>
            ))}
          </fieldset>
        ) : null}
        {(payload.evidenceEmailIds ?? []).length > 0 ? (
          <p className="flex flex-wrap gap-x-3 gap-y-1 text-xs">
            {(payload.evidenceEmailIds ?? []).map((emailId, index) => (
              <Link key={emailId} href={`/inbox?email=${emailId}`} className="text-primary underline-offset-2 hover:underline">
                Source email {index + 1}
              </Link>
            ))}
          </p>
        ) : null}
      </div>
      <div className="relative z-10 mt-auto flex justify-end gap-2 border-t px-3 py-2">
        <AppButton
          size="sm"
          variant="ghost"
          disabled={decide.isPending}
          aria-label={`Dismiss ${title}`}
          onClick={() => decide.mutate({ proposalId: proposal.id, data: { decision: "REJECT" } })}
        >
          Dismiss
        </AppButton>
        <AppButton size="sm" loading={decide.isPending} aria-label={`Keep ${title}`} onClick={keep}>
          Keep
        </AppButton>
      </div>
    </article>
  );
}

"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Reply } from "lucide-react";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { useDecideProposal } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { ImportanceBadge } from "@/components/app/proposals/ImportanceBadge";
import { CARD_HOVER, STRETCHED_BUTTON } from "@/lib/card-hover";
import { tintClass } from "@/lib/collection-tint";
import { placeholdersIn, recipientLabel, type ReplyDraftPayload, type ReplyText } from "@/lib/reply-draft";
import { cn } from "@/lib/utils";
import { ReplyBody } from "./ReplyBody";
import { ReplyDraftDialog } from "./ReplyDraftDialog";

interface ReplySuggestionCardProps {
  /** A pending DRAFT_REPLY proposal. */
  proposal: ProposalDto;
  /** Small heading above the subject. */
  label?: string;
  /** Show the whole text instead of its first lines (thread view). */
  full?: boolean;
  /** Makes the card open its detail sidebar when clicked. */
  onOpen?: () => void;
}

/**
 * A reply the assistant suggests: recipients, subject and text with the placeholders highlighted. "Edit & keep" opens
 * the editor and approves the proposal with the edited text (kept as a draft, never sent); "Dismiss" rejects it.
 */
export function ReplySuggestionCard({ proposal, label = "Draft reply", full = false, onOpen }: ReplySuggestionCardProps) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  // Keeping or dismissing changes the proposals, the thread and the inbox markers: refresh them all.
  const decide = useDecideProposal({ mutation: { onSuccess: () => queryClient.invalidateQueries() } });
  const payload = proposal.payload as unknown as ReplyDraftPayload;
  const text: ReplyText = { to: payload.to ?? [], cc: payload.cc ?? [], subject: payload.subject ?? "", body: payload.body ?? "" };
  const subject = text.subject || "(No subject)";
  const left = placeholdersIn(text.body).length;

  const keep = (edited: ReplyText) =>
    decide.mutate(
      {
        proposalId: proposal.id,
        data: { decision: "APPROVE", payload: { ...proposal.payload, ...edited, placeholders: placeholdersIn(edited.body) } },
      },
      { onSuccess: () => setEditing(false) },
    );

  return (
    <article aria-label={subject} className={cn("relative flex flex-col overflow-hidden rounded-xl border bg-card", !full && "h-full", onOpen && CARD_HOVER, decide.isPending && "opacity-60")}>
      <div className="grid flex-1 content-start gap-3 p-4">
        <div className="flex items-start gap-3">
          <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-lg", tintClass(5))} aria-hidden="true">
            <Reply className="size-4" />
          </span>
          <div className="grid min-w-0 flex-1 gap-0.5">
            <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              {label}
              <ImportanceBadge importance={proposal.importance} reason={proposal.importanceReason} />
            </span>
            <h2 className="line-clamp-2 leading-snug font-medium">
              {onOpen ? (
                <button type="button" aria-label={`Show details for ${subject}`} onClick={onOpen} className={STRETCHED_BUTTON}>
                  {subject}
                </button>
              ) : (
                subject
              )}
            </h2>
          </div>
        </div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
          <dt className="text-muted-foreground">To</dt>
          <dd className="truncate">{text.to.map(recipientLabel).join(", ")}</dd>
          {text.cc.length > 0 ? (
            <>
              <dt className="text-muted-foreground">Cc</dt>
              <dd className="truncate">{text.cc.map(recipientLabel).join(", ")}</dd>
            </>
          ) : null}
        </dl>
        <ReplyBody body={text.body} className={cn("rounded-lg bg-muted/40 px-3 py-2", !full && "line-clamp-5")} />
        {left > 0 ? (
          <p className="text-xs text-muted-foreground">
            {left === 1 ? "1 placeholder" : `${left} placeholders`} to fill in before sending.
          </p>
        ) : null}
        {proposal.reasoning ? <p className="text-xs leading-relaxed text-muted-foreground">{proposal.reasoning}</p> : null}
      </div>
      <div className="relative z-10 mt-auto flex justify-end gap-2 border-t px-3 py-2">
        <AppButton
          size="sm"
          variant="ghost"
          disabled={decide.isPending}
          aria-label={`Dismiss ${subject}`}
          onClick={() => decide.mutate({ proposalId: proposal.id, data: { decision: "REJECT" } })}
        >
          Dismiss
        </AppButton>
        <AppButton size="sm" disabled={decide.isPending} aria-label={`Edit & keep ${subject}`} onClick={() => setEditing(true)}>
          Edit &amp; keep
        </AppButton>
      </div>
      <ReplyDraftDialog open={editing} onOpenChange={setEditing} initial={text} busy={decide.isPending} onKeep={keep} />
    </article>
  );
}

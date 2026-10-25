"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Copy, ExternalLink, PencilLine, Sparkles, Trash2 } from "lucide-react";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { buttonVariants } from "@/components/ui/button";
import { useGetAiSettings } from "@/lib/api/generated/endpoints/ai/ai";
import { getGetEmailThreadQueryKey } from "@/lib/api/generated/endpoints/mail/mail";
import { useDiscardReplyDraft, useSuggestReply, useUpdateReplyDraft } from "@/lib/api/generated/endpoints/reply-drafts/reply-drafts";
import type { EmailThreadDto } from "@/lib/api/generated/model/emailThreadDto";
import type { RecipientDto } from "@/lib/api/generated/model/recipientDto";
import type { ReplyDraftDto } from "@/lib/api/generated/model/replyDraftDto";
import { draftAsText, mailtoLink, recipientLabel, type ReplyText } from "@/lib/reply-draft";
import { cn } from "@/lib/utils";
import { ReplyBody } from "./ReplyBody";
import { ReplyDraftDialog } from "./ReplyDraftDialog";
import { ReplySuggestionCard } from "./ReplySuggestionCard";

interface ThreadRepliesProps {
  /** The email the thread is shown for (on-demand suggestions answer it). */
  emailId: number;
  thread: EmailThreadDto;
}

/**
 * The assistant's replies at the bottom of a thread: a pending suggestion (edit & keep / dismiss), kept drafts (copy,
 * open in the mail app, edit, discard; never sent), or "Suggest a reply" when reply drafts are on.
 */
export function ThreadReplies({ emailId, thread }: ThreadRepliesProps) {
  const queryClient = useQueryClient();
  const { data: settings } = useGetAiSettings();
  const suggest = useSuggestReply({
    mutation: { onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetEmailThreadQueryKey(emailId) }) },
  });
  const email = thread.messages.find((message) => message.id === emailId);
  const canSuggest = settings?.mailAccess === true && settings.replies === "SUGGEST" && email !== undefined && email.folder !== "sent";
  const running = thread.replySuggestion === "RUNNING" || suggest.isPending;
  const pending = thread.pendingReplyProposal;

  if (!pending && thread.replyDrafts.length === 0 && !canSuggest) {
    return null;
  }
  return (
    <section aria-label="Replies" className="grid gap-2">
      {thread.replyDrafts.map((draft) => (
        <KeptDraft key={draft.id} draft={draft} />
      ))}
      {pending ? <ReplySuggestionCard proposal={pending} label="The assistant suggests a reply" full /> : null}
      {!pending && canSuggest ? (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-dashed px-4 py-3 text-sm">
          <span className="text-muted-foreground">
            {running
              ? "The assistant is writing a reply…"
              : thread.replySuggestion === "FAILED"
                ? "The assistant could not write a reply. Try again?"
                : "Want a reply draft for this email? Nothing is sent."}
          </span>
          <AppButton size="sm" variant="outline" loading={running} onClick={() => suggest.mutate({ emailId })}>
            <Sparkles className="size-3.5" aria-hidden="true" />
            Suggest a reply
          </AppButton>
        </div>
      ) : null}
    </section>
  );
}

function recipientsOf(recipients: ReplyText["to"]): RecipientDto[] {
  return recipients.map((recipient) => ({ name: recipient.name ?? undefined, address: recipient.address }));
}

function textOf(draft: ReplyDraftDto): ReplyText {
  return { to: draft.toRecipients, cc: draft.ccRecipients, subject: draft.subject, body: draft.body };
}

/** A kept draft: never sent. Copy it or open it in the mail app (only when it fits a mailto: link). */
function KeptDraft({ draft }: { draft: ReplyDraftDto }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [copied, setCopied] = useState(false);
  const refresh = () => queryClient.invalidateQueries();
  const update = useUpdateReplyDraft({ mutation: { onSuccess: refresh } });
  const discard = useDiscardReplyDraft({ mutation: { onSuccess: refresh } });
  const text = textOf(draft);
  const mailto = mailtoLink(text);

  const copy = async () => {
    await navigator.clipboard.writeText(draftAsText(text));
    setCopied(true);
  };
  const keep = (edited: ReplyText) =>
    update.mutate(
      { replyDraftId: draft.id, data: { toRecipients: recipientsOf(edited.to), ccRecipients: recipientsOf(edited.cc), subject: edited.subject, body: edited.body } },
      { onSuccess: () => setEditing(false) },
    );

  return (
    <article aria-label="Draft — not sent" className={cn("overflow-hidden rounded-lg border border-primary/40 bg-surface-email shadow-xs", discard.isPending && "opacity-60")}>
      <div className="grid gap-2 px-5 py-3.5">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <span className="text-sm font-medium">Draft — not sent</span>
          <span className="text-xs text-muted-foreground">To {text.to.map(recipientLabel).join(", ")}</span>
        </div>
        <p className="text-sm font-medium">{text.subject}</p>
        <ReplyBody body={text.body} />
      </div>
      <div className="flex flex-wrap justify-end gap-2 border-t px-3 py-2">
        <AppButton size="sm" variant="ghost" disabled={discard.isPending} onClick={() => discard.mutate({ replyDraftId: draft.id })}>
          <Trash2 className="size-3.5" aria-hidden="true" />
          Discard
        </AppButton>
        <AppButton size="sm" variant="ghost" onClick={() => setEditing(true)}>
          <PencilLine className="size-3.5" aria-hidden="true" />
          Edit
        </AppButton>
        <AppButton size="sm" variant="outline" onClick={copy}>
          <Copy className="size-3.5" aria-hidden="true" />
          {copied ? "Copied" : "Copy"}
        </AppButton>
        {mailto ? (
          <a href={mailto} className={buttonVariants({ size: "sm" })}>
            <ExternalLink className="size-3.5" aria-hidden="true" />
            Open in mail app
          </a>
        ) : (
          <span className="self-center text-xs text-muted-foreground">Too long to open in the mail app: copy it.</span>
        )}
      </div>
      <ReplyDraftDialog open={editing} onOpenChange={setEditing} initial={text} title="Edit draft" busy={update.isPending} onKeep={keep} />
    </article>
  );
}

"use client";

import { ChevronDown } from "lucide-react";
import { useState } from "react";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { AppButton } from "@/components/app/AppButton";
import { useGetEmailThread } from "@/lib/api/generated/endpoints/mail/mail";
import type { EmailDto } from "@/lib/api/generated/model/emailDto";
import type { EmailSummaryDto } from "@/lib/api/generated/model/emailSummaryDto";
import { cn } from "@/lib/utils";
import { ThreadReplies } from "@/components/app/replies/ThreadReplies";
import { EmailBody, Recipients } from "./EmailReader";
import { ThreadSkeleton } from "./InboxSkeleton";
import { PictureAvatar } from "./PictureAvatar";

const SUGGESTION_POLL_MS = 3000;
const WHEN = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

interface EmailThreadProps {
  /** The email the conversation is shown for; its message and the newest one start expanded. */
  emailId: number;
  /** The email's list row, shown at once while the conversation loads. */
  preview?: EmailSummaryDto;
  /** Sender picture URL for an address (contact photo or domain logo). */
  picture: (address: string | undefined) => string | undefined;
}

/**
 * The conversation of an email, structured like Outlook's reading pane: the subject in a box on top, then each message
 * of the history in its own box, oldest first, your own replies (Sent Items) marked as yours. The subject box stays put;
 * only the history below it scrolls, so messages vanish under the subject. Fills the height of its container. The email itself and the newest message
 * are open, earlier ones are one line until clicked. Use `key={emailId}` to start over for another email.
 */
export function EmailThread({ emailId, preview, picture }: EmailThreadProps) {
  // While the assistant writes a reply on demand, look again every few seconds.
  const { data, isLoading, error } = useGetEmailThread(emailId, {
    query: { refetchInterval: (query) => (query.state.data?.replySuggestion === "RUNNING" ? SUGGESTION_POLL_MS : false) },
  });
  const [toggled, setToggled] = useState<ReadonlySet<number>>(new Set());
  const [allOpen, setAllOpen] = useState(false);

  if (isLoading) {
    return preview ? <ThreadPreview email={preview} picture={picture(preview.fromAddress)} /> : <ThreadSkeleton />;
  }
  if (error || !data) {
    return <ErrorMessage message={error?.message ?? "The conversation could not be loaded."} />;
  }

  const { messages } = data;
  const newest = messages.at(-1)?.id;
  const subject = (messages.find((message) => message.id === emailId) ?? messages.at(-1))?.subject || "(No subject)";
  // Open by default: the email itself and the newest message; a click flips a message away from its default.
  const isOpen = (id: number) => allOpen || (id === emailId || id === newest) !== toggled.has(id);
  const flip = (id: number) =>
    setToggled((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <section aria-label="Conversation" className="flex min-h-0 min-w-0 flex-1 flex-col gap-2">
      <header className="flex shrink-0 flex-wrap items-center justify-between gap-2 rounded-lg border bg-surface-email px-5 py-3.5 shadow-xs">
        <h2 className="text-lg leading-snug font-semibold tracking-tight">{subject}</h2>
        {messages.length > 1 ? (
          <AppButton size="sm" variant="ghost" onClick={() => setAllOpen((open) => !open)}>
            {allOpen ? "Collapse earlier" : `Expand all ${messages.length}`}
          </AppButton>
        ) : null}
      </header>
      {/* Only the history scrolls; the gutter stays reserved so the boxes keep their width when a scrollbar appears. */}
      <ol aria-label="Messages" className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto [scrollbar-gutter:stable]">
        {messages.map((message) => (
          <li key={message.id}>
            <ThreadMessage
              message={message}
              current={message.id === emailId}
              open={isOpen(message.id)}
              picture={picture(message.fromAddress)}
              onToggle={() => flip(message.id)}
            />
          </li>
        ))}
        <li className="empty:hidden">
          <ThreadReplies emailId={emailId} thread={data} />
        </li>
      </ol>
    </section>
  );
}

/** The list row's data in the reading pane's layout until the conversation arrived. */
function ThreadPreview({ email, picture }: { email: EmailSummaryDto; picture: string | undefined }) {
  const own = email.folder === "sent";
  const sender = own ? "You" : email.fromName || email.fromAddress || "Unknown sender";
  return (
    <section aria-label="Conversation" aria-busy="true" className="flex min-h-0 min-w-0 flex-1 flex-col gap-2">
      <header className="flex shrink-0 items-center rounded-lg border bg-surface-email px-5 py-3.5 shadow-xs">
        <h2 className="text-lg leading-snug font-semibold tracking-tight">{email.subject || "(No subject)"}</h2>
      </header>
      <article className="flex shrink-0 items-start gap-3 rounded-lg border border-primary/40 bg-surface-email px-5 py-3.5 shadow-xs">
        <PictureAvatar label={sender} src={own ? undefined : picture} size={40} className="self-start" />
        <span className="grid min-w-0 flex-1 gap-0.5">
          <span className="flex items-baseline justify-between gap-3">
            <span className="truncate text-sm font-medium">{sender}</span>
            <time dateTime={email.receivedAt} className="shrink-0 text-xs text-muted-foreground">
              {WHEN.format(new Date(email.receivedAt))}
            </time>
          </span>
          {email.snippet ? <span className="line-clamp-2 text-xs text-muted-foreground">{email.snippet}</span> : null}
        </span>
      </article>
      {/* The body and the earlier messages are on their way. */}
      <ThreadSkeleton subject={false} messages={2} />
    </section>
  );
}

interface ThreadMessageProps {
  message: EmailDto;
  current: boolean;
  open: boolean;
  picture: string | undefined;
  onToggle: () => void;
}

function ThreadMessage({ message, current, open, picture, onToggle }: ThreadMessageProps) {
  const own = message.folder === "sent";
  const sender = own ? "You" : message.fromName || message.fromAddress || "Unknown sender";
  const label = `${sender}, ${WHEN.format(new Date(message.receivedAt))}`;

  return (
    <article
      aria-label={label}
      aria-current={current ? "true" : undefined}
      className={cn("relative shrink-0 overflow-hidden rounded-lg border bg-surface-email shadow-xs", current && "border-primary/40")}
    >
      {current ? <span aria-hidden="true" className="absolute inset-y-0 left-0 w-0.5 bg-primary" /> : null}
      <button
        type="button"
        aria-expanded={open}
        onClick={onToggle}
        className="flex w-full items-start gap-3 px-5 py-3.5 text-left transition-colors hover:bg-muted/30"
      >
        <PictureAvatar label={sender} src={own ? undefined : picture} size={40} className="self-start" />
        <span className="grid min-w-0 flex-1 gap-0.5">
          <span className="flex items-baseline justify-between gap-3">
            <span className="flex min-w-0 items-baseline gap-2">
              <span className="truncate text-sm font-medium">{sender}</span>
              {!own && message.fromName && message.fromAddress ? (
                <span className="hidden truncate text-xs text-muted-foreground sm:inline">{message.fromAddress}</span>
              ) : null}
            </span>
            <time dateTime={message.receivedAt} className="shrink-0 text-xs text-muted-foreground">
              {WHEN.format(new Date(message.receivedAt))}
            </time>
          </span>
          {open ? (
            <span className="grid gap-0.5">
              <Recipients label="To" recipients={message.toRecipients} />
              <Recipients label="Cc" recipients={message.ccRecipients} />
            </span>
          ) : message.snippet ? (
            <span className="line-clamp-1 text-xs text-muted-foreground">{message.snippet}</span>
          ) : null}
        </span>
        <ChevronDown className={cn("mt-1 size-4 shrink-0 text-muted-foreground transition-transform", open && "rotate-180")} aria-hidden="true" />
      </button>
      {open ? (
        <div className="px-5 pb-5 sm:pl-[4.75rem]">
          <EmailBody email={message} />
        </div>
      ) : null}
    </article>
  );
}

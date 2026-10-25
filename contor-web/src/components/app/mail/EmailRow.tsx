"use client";

import { CornerUpLeft, MessagesSquare, Paperclip } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { EmailSummaryDto } from "@/lib/api/generated/model/emailSummaryDto";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import { cn } from "@/lib/utils";
import { AccountAvatar } from "./AccountAvatar";
import { listTime } from "./mail-dates";
import { recipientsLabel } from "./mail-format";
import { PictureAvatar } from "./PictureAvatar";

export interface EmailRowProps {
  email: EmailSummaryDto;
  selected: boolean;
  /** Mailbox of the email, shown in the unified view of several accounts. */
  account?: MailAccountDto;
  /** Sender picture URL (contact photo or domain logo). */
  picture?: string;
  /** Reference time for the short time label ("12:46", "Tue 21:05", "7 Sept"). */
  now: Date;
  onSelect: (emailId: number) => void;
  /** The pointer or focus came to rest on the row (the list prefetches its conversation). */
  onPreviewStart?: (emailId: number) => void;
  /** The pointer or focus left the row. */
  onPreviewEnd?: () => void;
}

/**
 * One email in the list as a box, structured like Outlook's rows: picture, sender and time, subject with
 * reply-draft/thread/answered/attachment marks, one line of preview, labels. Unread mail has a bar on the left and a bold subject in
 * the primary color.
 */
export function EmailRow({ email, selected, account, picture, now, onSelect, onPreviewStart, onPreviewEnd }: EmailRowProps) {
  // Sent mail shows who it went to instead of the sender (always you).
  const sender = email.folder === "sent" ? `To: ${recipientsLabel(email.toRecipients)}` : email.fromName || email.fromAddress || "";
  const unread = !email.read;
  return (
    <button
      type="button"
      onClick={() => onSelect(email.id)}
      onMouseEnter={onPreviewStart ? () => onPreviewStart(email.id) : undefined}
      onMouseLeave={onPreviewEnd}
      onFocus={onPreviewStart ? () => onPreviewStart(email.id) : undefined}
      onBlur={onPreviewEnd}
      aria-current={selected ? "true" : undefined}
      data-unread={unread ? "true" : undefined}
      className={cn(
        "relative flex w-full gap-3 overflow-hidden rounded-lg border bg-surface-email px-3.5 py-3 text-left transition-colors",
        selected ? "border-primary/60 bg-primary/5" : "hover:bg-muted/40",
      )}
    >
      {unread ? <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-primary" /> : null}
      <PictureAvatar label={sender || "?"} src={picture} size={36} className="mt-0.5 self-start" />
      {/* minmax(0, 1fr): a long subject truncates instead of widening the row past its box (the date and marks would clip). */}
      <div className="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)] gap-0.5">
        <div className="flex items-baseline justify-between gap-2">
          <span className={cn("min-w-0 truncate text-sm", unread && "font-semibold")}>{sender}</span>
          <time dateTime={email.receivedAt} className={cn("shrink-0 text-xs", unread ? "font-medium text-primary" : "text-muted-foreground")}>
            {listTime(email.receivedAt, now)}
          </time>
        </div>

        <div className="flex items-center gap-2">
          <h3 className={cn("min-w-0 truncate text-sm", unread ? "font-semibold text-primary" : "text-foreground/90")}>
            {email.subject || "(No subject)"}
          </h3>
          <span className="ml-auto flex shrink-0 items-center gap-2 text-muted-foreground">
            {email.threadSize != null && email.threadSize > 1 ? (
              <span aria-label={`${email.threadSize} messages`} title={`${email.threadSize} messages in this conversation`} className="flex items-center gap-0.5 text-xs tabular-nums">
                <MessagesSquare className="size-3.5" aria-hidden="true" />
                {email.threadSize}
              </span>
            ) : null}
            {email.replyState ? (
              <span className="rounded bg-primary/10 px-1.5 py-px text-[10px] font-medium text-primary">
                {email.replyState === "DRAFT_READY" ? "Draft ready" : "Reply suggested"}
              </span>
            ) : null}
            {email.answered ? <CornerUpLeft aria-label="Answered" className="size-3.5 text-success" /> : null}
            {email.hasAttachments ? <Paperclip className="size-3.5" /> : null}
          </span>
        </div>

        {email.snippet ? <p className="line-clamp-1 text-xs text-muted-foreground">{email.snippet}</p> : null}

        {email.labels.length > 0 || account ? (
          <div className="mt-1 flex flex-wrap items-center gap-1">
            {account ? (
              <Badge variant="outline" className="max-w-44 gap-1 truncate text-[10px] font-normal">
                <AccountAvatar account={account} size={14} />
                {account.emailAddress}
              </Badge>
            ) : null}
            {email.labels.map((label) => (
              <Badge key={label.labelId} variant="secondary" className="text-[10px]">
                {label.name}
              </Badge>
            ))}
          </div>
        ) : null}
      </div>
    </button>
  );
}

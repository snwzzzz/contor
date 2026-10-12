"use client";

import { Paperclip, SunMedium } from "lucide-react";
import { useTheme } from "next-themes";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import type { EmailDto } from "@/lib/api/generated/model/emailDto";
import type { RecipientDto } from "@/lib/api/generated/model/recipientDto";
import { cn } from "@/lib/utils";
import { EmailHtmlBody } from "./EmailHtmlBody";
import { splitQuoted } from "./mail-format";
import { PictureAvatar } from "./PictureAvatar";

const RECEIVED = new Intl.DateTimeFormat("en-GB", {
  weekday: "short",
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

type View = "formatted" | "text";

/**
 * The body of one email: formatted HTML with a switch to the plain text when the mail has both; plain text folds the
 * quoted history of earlier messages behind "Show quoted text". In dark mode HTML mail is darkened, with a switch back
 * to its original colors.
 */
export function EmailBody({ email }: { email: EmailDto }) {
  const { resolvedTheme } = useTheme();
  const [view, setView] = useState<View>("formatted");
  const [showQuoted, setShowQuoted] = useState(false);
  const [originalColors, setOriginalColors] = useState(false);
  const hasBoth = Boolean(email.bodyHtml && email.bodyText);
  const showHtml = Boolean(email.bodyHtml) && (view === "formatted" || !email.bodyText);
  const text = email.bodyText ? splitQuoted(email.bodyText) : null;
  const darkTheme = resolvedTheme === "dark";

  return (
    <div className="flex min-w-0 flex-col gap-4">
      {hasBoth || (darkTheme && showHtml) ? (
        <div className="flex flex-wrap items-center gap-2">
          {hasBoth ? (
            <div role="group" aria-label="Show as" className="flex w-fit rounded-lg border bg-muted/40 p-0.5">
              <ViewButton current={view} value="formatted" label="Formatted" onSelect={setView} />
              <ViewButton current={view} value="text" label="Plain text" onSelect={setView} />
            </div>
          ) : null}
          {darkTheme && showHtml ? (
            <button
              type="button"
              aria-pressed={originalColors}
              title="Show the mail on a white page, as the sender wrote it"
              onClick={() => setOriginalColors((original) => !original)}
              className={cn(
                "inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground",
                originalColors && "bg-muted text-foreground",
              )}
            >
              <SunMedium className="size-3.5" aria-hidden="true" />
              Original colors
            </button>
          ) : null}
        </div>
      ) : null}

      <div className="min-w-0">
        {showHtml && email.bodyHtml ? (
          <EmailHtmlBody html={email.bodyHtml} dark={darkTheme && !originalColors} paper={darkTheme && originalColors} />
        ) : text ? (
          <div className="text-sm leading-relaxed">
            <div className="whitespace-pre-wrap">{text.fresh}</div>
            {text.quoted ? (
              <>
                <button
                  type="button"
                  aria-expanded={showQuoted}
                  onClick={() => setShowQuoted((open) => !open)}
                  className="mt-3 rounded-md border px-2 py-0.5 text-xs text-muted-foreground hover:bg-muted"
                >
                  {showQuoted ? "Hide quoted text" : "Show quoted text"}
                </button>
                {showQuoted ? <div className="mt-3 border-l-2 pl-3 whitespace-pre-wrap text-muted-foreground">{text.quoted}</div> : null}
              </>
            ) : null}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground italic">No message body.</p>
        )}
      </div>
    </div>
  );
}

interface EmailReaderProps {
  email: EmailDto;
  /** Sender picture URL (contact photo or domain logo). */
  picture?: string;
  className?: string;
}

/** One email to read: subject, sender with picture, recipients, labels, then the body. */
export function EmailReader({ email, picture, className }: EmailReaderProps) {
  const sender = email.fromName || email.fromAddress || "Unknown sender";

  return (
    <article className={cn("flex min-w-0 flex-col gap-5", className)}>
      <header className="grid gap-4">
        <div className="grid gap-2">
          <h2 className="text-xl leading-snug font-semibold tracking-tight">{email.subject || "(No subject)"}</h2>
          {email.labels.length > 0 || email.hasAttachments ? (
            <div className="flex flex-wrap items-center gap-1.5">
              {email.labels.map((label) => (
                <Badge key={label.labelId} variant="secondary" className="text-xs">
                  {label.name}
                </Badge>
              ))}
              {email.hasAttachments ? (
                <Badge variant="outline" className="gap-1 text-xs font-normal">
                  <Paperclip className="size-3" aria-hidden="true" />
                  Has attachments
                </Badge>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="flex items-start gap-3">
          <PictureAvatar label={sender} src={picture} size={40} className="self-start" />
          <div className="grid min-w-0 flex-1 gap-0.5 text-sm">
            <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
              <span className="flex min-w-0 flex-wrap items-baseline gap-x-2">
                <span className="font-medium">{sender}</span>
                {email.fromName && email.fromAddress ? <span className="truncate text-muted-foreground">{email.fromAddress}</span> : null}
              </span>
              <time dateTime={email.receivedAt} className="shrink-0 text-xs text-muted-foreground">
                {RECEIVED.format(new Date(email.receivedAt))}
              </time>
            </div>
            <Recipients label="To" recipients={email.toRecipients} />
            <Recipients label="Cc" recipients={email.ccRecipients} />
          </div>
        </div>
      </header>

      <EmailBody email={email} />
    </article>
  );
}

export function Recipients({ label, recipients }: { label: string; recipients: RecipientDto[] }) {
  if (recipients.length === 0) return null;
  return (
    <p className="text-xs text-muted-foreground">
      <span className="mr-1 font-medium text-foreground/80">{label}</span>
      {recipients.map((recipient, index) => (
        <span key={`${recipient.address}-${index}`}>
          {index > 0 ? ", " : null}
          <span title={recipient.address}>{recipient.name || recipient.address}</span>
        </span>
      ))}
    </p>
  );
}

interface ViewButtonProps {
  current: View;
  value: View;
  label: string;
  onSelect: (view: View) => void;
}

function ViewButton({ current, value, label, onSelect }: ViewButtonProps) {
  return (
    <button
      type="button"
      aria-pressed={current === value}
      onClick={() => onSelect(value)}
      className={cn(
        "rounded-md px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground",
        current === value && "bg-background text-foreground shadow-xs",
      )}
    >
      {label}
    </button>
  );
}

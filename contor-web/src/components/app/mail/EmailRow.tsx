"use client";

import { Paperclip } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { EmailLabelDto } from "@/lib/api/generated/model/emailLabelDto";
import type { EmailSummaryDto } from "@/lib/api/generated/model/emailSummaryDto";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import { cn } from "@/lib/utils";
import { AccountAvatar } from "./AccountAvatar";
import { PictureAvatar } from "./PictureAvatar";

export interface EmailRowProps {
  email: EmailSummaryDto;
  selected: boolean;
  /** Mailbox of the email, shown in the unified view of several accounts. */
  account?: MailAccountDto;
  /** Sender picture URL (contact photo or domain logo). */
  picture?: string;
  onSelect: (emailId: number) => void;
}

/** One email in a list: sender picture, sender, date, subject, preview and labels. */
export function EmailRow({ email, selected, account, picture, onSelect }: EmailRowProps) {
  const sender = email.fromName || email.fromAddress || "";
  return (
    <button
      type="button"
      onClick={() => onSelect(email.id)}
      aria-current={selected ? "true" : undefined}
      className={cn(
        "flex w-full gap-3 rounded-lg border p-3.5 text-left transition-colors",
        selected ? "border-primary bg-muted/60" : "border-border hover:bg-muted/40",
        !email.read && "font-medium",
      )}
    >
      <PictureAvatar label={sender || "?"} src={picture} size={32} className="mt-0.5 self-start" />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
          <span className="min-w-0 truncate">{sender}</span>
          {account ? (
            <Badge variant="outline" className="ml-auto max-w-44 gap-1 truncate text-[10px] font-normal">
              <AccountAvatar account={account} size={14} />
              {account.emailAddress}
            </Badge>
          ) : null}
          <span className="shrink-0">{new Date(email.receivedAt).toLocaleDateString()}</span>
        </div>

        <div className="mt-1 flex items-center justify-between gap-2">
          <h3 className="line-clamp-1 text-sm font-semibold">{email.subject || "(No subject)"}</h3>
          {email.hasAttachments ? <Paperclip className="size-3.5 shrink-0 text-muted-foreground" /> : null}
        </div>

        {email.snippet ? (
          <p className="mt-1 line-clamp-2 text-xs font-normal text-muted-foreground">{email.snippet}</p>
        ) : null}

        {email.labels.length > 0 ? (
          <div className="mt-2 flex flex-wrap gap-1">
            {email.labels.map((lbl: EmailLabelDto) => (
              <Badge key={lbl.labelId} variant="secondary" className="text-[10px]">
                {lbl.name}
              </Badge>
            ))}
          </div>
        ) : null}
      </div>
    </button>
  );
}

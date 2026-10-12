"use client";

import type { EmailSummaryDto } from "@/lib/api/generated/model/emailSummaryDto";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import { EmailRow } from "./EmailRow";
import { groupByDate } from "./mail-dates";
import { counterpartAddress } from "./mail-format";

interface EmailListProps {
  /** Newest first. */
  emails: readonly EmailSummaryDto[];
  selectedId: number | null;
  onSelect: (emailId: number) => void;
  /** Pointer or focus rests on a row / left it (prefetching). */
  onPreviewStart?: (emailId: number) => void;
  onPreviewEnd?: () => void;
  /** Sender picture URL for an address (contact photo or domain logo). */
  picture: (address: string | undefined) => string | undefined;
  /** The mailbox to show on a row (unified view of several accounts), if any. */
  accountOf?: (email: EmailSummaryDto) => MailAccountDto | undefined;
}

/** Email boxes under sticky period headings (Today, Yesterday, This week, …), like Outlook's message list. */
export function EmailList({ emails, selectedId, onSelect, onPreviewStart, onPreviewEnd, picture, accountOf }: EmailListProps) {
  const now = new Date();
  return (
    <div className="grid gap-3">
      {groupByDate(emails, now).map((group) => (
        <div key={group.label} className="grid gap-2">
          <h3 className="sticky top-0 z-10 bg-background/95 py-1 text-xs font-semibold text-muted-foreground backdrop-blur">
            {group.label}
          </h3>
          {group.items.map((email) => (
            <EmailRow
              key={email.id}
              email={email}
              selected={email.id === selectedId}
              account={accountOf?.(email)}
              picture={picture(counterpartAddress(email))}
              now={now}
              onSelect={onSelect}
              onPreviewStart={onPreviewStart}
              onPreviewEnd={onPreviewEnd}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

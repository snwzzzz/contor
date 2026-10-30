"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { AccountAvatar } from "@/components/app/mail/AccountAvatar";
import { providerLabel } from "@/components/app/mail/mail-providers";
import { Skeleton } from "@/components/ui/skeleton";
import { useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import type { MailAccountState } from "@/lib/api/generated/model/mailAccountState";
import { ROW_HOVER } from "@/lib/card-hover";
import { settingsHref } from "@/lib/navigation";
import { relativeTime } from "@/lib/relative-time";
import { cn } from "@/lib/utils";
import { HomeSection, SectionLink } from "./HomeSection";
import { Pager, usePages } from "./Pager";

const PER_PAGE = 3;

/** Status dot and the note for mailboxes that do not sync right now. */
const STATE: Record<MailAccountState, { dot: string; note?: string }> = {
  ACTIVE: { dot: "bg-success" },
  NEEDS_REAUTH: { dot: "bg-warning", note: "Needs sign-in" },
  DISABLED: { dot: "bg-muted-foreground/50", note: "Disconnected" },
};

/** The connected mailboxes: picture, address (opens the inbox), sync state with the last sync; "Manage" opens the account settings. */
export function HomeMailboxes() {
  const pathname = usePathname();
  const { data: accounts, isPending, error } = useListMailAccounts();
  // Fixed per mount: rows are only rendered on the client, after the accounts arrived.
  const [now] = useState(() => new Date());
  const manageHref = settingsHref(pathname, "accounts");
  const pages = usePages(accounts ?? [], PER_PAGE);

  return (
    <HomeSection
      title="Connected mailboxes"
      action={
        <SectionLink href={manageHref} scroll={false}>
          Manage
        </SectionLink>
      }
    >
      {error ? (
        <ErrorMessage message={error.message} />
      ) : isPending ? (
        <div aria-label="Loading mailboxes" className="grid gap-2 py-1">
          <Skeleton className="h-8" />
          <Skeleton className="h-8 w-4/5" />
        </div>
      ) : !accounts || accounts.length === 0 ? (
        <p className="py-2 text-sm text-muted-foreground">
          No mailbox yet.{" "}
          <Link href={manageHref} scroll={false} className="text-foreground underline underline-offset-4">
            Connect a mailbox
          </Link>
        </p>
      ) : (
        <>
          <ul className="-mx-2 grid">
            {pages.items.map((account) => (
              <li key={account.id}>
                <MailboxRow account={account} now={now} />
              </li>
            ))}
          </ul>
          <Pager label="Mailboxes" {...pages} />
        </>
      )}
    </HomeSection>
  );
}

function MailboxRow({ account, now }: { account: MailAccountDto; now: Date }) {
  const state = STATE[account.status];
  const status = state.note ?? (account.lastSyncedAt ? `Synced ${relativeTime(account.lastSyncedAt, now)}` : "Not synced yet");
  return (
    <div className={cn("flex items-center gap-3 rounded-md px-2 py-1.5", ROW_HOVER)}>
      <AccountAvatar account={account} size={24} />
      <div className="grid min-w-0 flex-1">
        <Link
          href="/inbox"
          className="truncate rounded-sm text-sm underline-offset-4 outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
        >
          {account.emailAddress}
        </Link>
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", state.dot)} />
          {account.provider === "DEMO" ? null : (
            <>
              <span>{providerLabel(account.provider)}</span>
              <span aria-hidden="true">·</span>
            </>
          )}
          <span className="truncate">{status}</span>
        </span>
      </div>
    </div>
  );
}

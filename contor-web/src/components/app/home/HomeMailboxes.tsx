"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { AccountAvatar } from "@/components/app/mail/AccountAvatar";
import { Skeleton } from "@/components/ui/skeleton";
import { useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import type { MailAccountState } from "@/lib/api/generated/model/mailAccountState";
import { settingsHref } from "@/lib/navigation";
import { HomeSection, SECTION_LINK } from "./HomeSection";

/** Shown next to mailboxes that do not sync right now. */
const STATE_NOTE: Partial<Record<MailAccountState, string>> = {
  NEEDS_REAUTH: "Needs sign-in",
  DISABLED: "Disconnected",
};

/** Compact list of the connected mailboxes; each opens the inbox, "Manage" opens the account settings. */
export function HomeMailboxes() {
  const pathname = usePathname();
  const { data: accounts, isPending, error } = useListMailAccounts();
  const manageHref = settingsHref(pathname, "accounts");

  return (
    <HomeSection
      title="Connected mailboxes"
      action={
        <Link href={manageHref} scroll={false} className={SECTION_LINK}>
          Manage
        </Link>
      }
    >
      {error ? (
        <ErrorMessage message={error.message} />
      ) : isPending ? (
        <Skeleton className="h-7" aria-label="Loading mailboxes" />
      ) : !accounts || accounts.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No mailbox yet.{" "}
          <Link href={manageHref} scroll={false} className={SECTION_LINK}>
            Connect a mailbox
          </Link>
        </p>
      ) : (
        <ul className="-mx-2 grid">
          {accounts.map((account) => (
            <li key={account.id} className="flex h-7 items-center gap-2 rounded-md px-2 hover:bg-accent">
              <AccountAvatar account={account} size={16} />
              <Link
                href="/inbox"
                className="min-w-0 truncate text-sm text-primary underline-offset-4 outline-none hover:underline focus-visible:underline"
              >
                {account.emailAddress}
              </Link>
              {STATE_NOTE[account.status] ? (
                <span className="ml-auto shrink-0 text-xs text-muted-foreground">{STATE_NOTE[account.status]}</span>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </HomeSection>
  );
}

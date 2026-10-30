"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AccountAvatar } from "@/components/app/mail/AccountAvatar";
import { useMailboxUpdate } from "@/components/app/mail/useMailboxUpdate";
import { useGetAiSettings } from "@/lib/api/generated/endpoints/ai/ai";
import { useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import { settingsHref } from "@/lib/navigation";
import { LoadingList } from "@/components/app/LoadingSkeletons";

/**
 * Which mailboxes the AI reads (the analysis and new mail), one checkbox per connected mailbox, saved right away.
 * Used in the setup wizard and in Settings → Autonomy. A hidden mailbox says whether the AI still reads it.
 */
export function MailboxAiPicker() {
  const pathname = usePathname();
  const { data: accounts } = useListMailAccounts();
  const { data: settings } = useGetAiSettings();
  const update = useMailboxUpdate();
  const readHidden = settings?.readHiddenMailboxes ?? true;

  if (!accounts) {
    return <LoadingList label="Loading mailboxes" rows={2} avatar />;
  }
  const connected = accounts.filter((account) => account.status !== "DISABLED");
  if (connected.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No mailbox connected yet.{" "}
        <Link href={settingsHref(pathname, "accounts")} scroll={false} className="font-medium text-foreground underline-offset-4 hover:underline">
          Connect a mailbox
        </Link>
      </p>
    );
  }

  return (
    <ul aria-label="Mailboxes the AI reads" className="grid gap-4">
      {connected.map((account) => {
        const skipped = account.hidden && !readHidden;
        return (
          <li key={account.id}>
            {/* Checkbox and avatar centered on the address and the name below it. */}
            <label className="flex items-center gap-3">
              <input
                type="checkbox"
                className="size-4 shrink-0 accent-primary disabled:cursor-not-allowed disabled:opacity-50"
                checked={account.aiEnabled && !skipped}
                disabled={skipped || (update.isPending && update.variables?.accountId === account.id)}
                onChange={(event) => update.mutate({ accountId: account.id, data: { aiEnabled: event.target.checked } })}
              />
              <AccountAvatar account={account} size={24} />
              <span className="grid min-w-0 gap-0.5">
                <span className="truncate text-sm font-medium">{account.emailAddress}</span>
                {account.hidden ? (
                  <span className="text-sm text-muted-foreground">
                    {readHidden ? "Hidden in contor; the AI still reads it." : "Hidden in contor; the AI skips hidden mailboxes (Settings › General)."}
                  </span>
                ) : account.displayName ? (
                  <span className="truncate text-sm text-muted-foreground">{account.displayName}</span>
                ) : null}
              </span>
            </label>
          </li>
        );
      })}
    </ul>
  );
}

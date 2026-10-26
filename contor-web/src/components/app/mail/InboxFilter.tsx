"use client";

import { ListFilter } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import { AccountAvatar } from "./AccountAvatar";
import { providerLabel } from "./mail-providers";

const ALL = "all";

interface InboxFilterProps {
  accounts: readonly MailAccountDto[];
  /** `null` = all mailboxes. */
  accountId: number | null;
  onAccountChange: (accountId: number | null) => void;
  unanswered: boolean;
  onUnansweredChange: (unanswered: boolean) => void;
  /** Sent mail is never "answered": hides that filter. */
  hideUnanswered?: boolean;
}

/** One small button with a menu for the inbox filters (mailbox, not answered); the count shows how many are active. */
export function InboxFilter({ accounts, accountId, onAccountChange, unanswered, onUnansweredChange, hideUnanswered = false }: InboxFilterProps) {
  const active = (accountId !== null ? 1 : 0) + (unanswered ? 1 : 0);
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <AppButton size="sm" variant={active > 0 ? "secondary" : "outline"} className="h-9 shrink-0">
          <ListFilter aria-hidden="true" data-icon="inline-start" />
          Filter
          {active > 0 ? (
            <span className="rounded-full bg-primary px-1.5 text-[10px] leading-4 font-semibold text-primary-foreground tabular-nums">{active}</span>
          ) : null}
        </AppButton>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        {accounts.length > 0 ? (
          <>
            <DropdownMenuLabel className="text-xs text-muted-foreground">Mailbox</DropdownMenuLabel>
            <DropdownMenuRadioGroup
              value={accountId === null ? ALL : String(accountId)}
              onValueChange={(value) => onAccountChange(value === ALL ? null : Number(value))}
            >
              <DropdownMenuRadioItem value={ALL}>All accounts</DropdownMenuRadioItem>
              {accounts.map((account) => (
                <DropdownMenuRadioItem key={account.id} value={String(account.id)} className="gap-2">
                  <AccountAvatar account={account} size={18} />
                  <span className="truncate">{account.emailAddress}</span>
                  <span className="ml-auto shrink-0 text-xs text-muted-foreground">{providerLabel(account.provider)}</span>
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
            <DropdownMenuSeparator />
          </>
        ) : null}
        {hideUnanswered ? null : (
          <DropdownMenuCheckboxItem checked={unanswered} onCheckedChange={(checked) => onUnansweredChange(checked === true)}>
            Not answered
          </DropdownMenuCheckboxItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

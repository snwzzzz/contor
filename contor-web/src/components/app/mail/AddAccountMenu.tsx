"use client";

import { ChevronDown, Plus } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { MailProviderDto } from "@/lib/api/generated/model/mailProviderDto";
import type { MailProviderKind } from "@/lib/api/generated/model/mailProviderKind";
import { ProviderLogo } from "./ProviderLogo";

interface AddAccountMenuProps {
  providers: readonly MailProviderDto[];
  onSelect: (provider: MailProviderKind) => void;
  /** A sign-in is being started: the button shows a spinner. */
  loading?: boolean;
  variant?: "default" | "outline";
}

/**
 * "Add account" with a menu of the mail providers, each with its logo. Providers the server has no OAuth client for
 * stay listed but disabled, so it is clear what could be set up (README, Mail setup).
 */
export function AddAccountMenu({ providers, onSelect, loading = false, variant = "default" }: AddAccountMenuProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <AppButton variant={variant} loading={loading}>
          {loading ? null : <Plus aria-hidden="true" data-icon="inline-start" />}
          Add account
          <ChevronDown aria-hidden="true" data-icon="inline-end" className="text-muted-foreground" />
        </AppButton>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="text-xs text-muted-foreground">Connect a mailbox</DropdownMenuLabel>
        {providers.map((provider) => (
          <DropdownMenuItem
            key={provider.kind}
            disabled={!provider.configured}
            onSelect={() => onSelect(provider.kind)}
            className="gap-2.5"
          >
            <ProviderLogo kind={provider.kind} className="size-5" />
            <span className="flex-1">{provider.displayName}</span>
            {provider.configured ? null : <span className="text-xs text-muted-foreground">Not set up</span>}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

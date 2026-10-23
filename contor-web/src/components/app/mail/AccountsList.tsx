"use client";

import { useQueryClient } from "@tanstack/react-query";
import {
  AlertCircle,
  CheckCircle2,
  FlaskConical,
  Mail,
  RefreshCw,
  RotateCcw,
  Unlink,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  getListMailAccountsQueryKey,
  useConnectMailAccount,
  useDisconnectMailAccount,
  useListMailAccounts,
  useSyncMailAccount,
} from "@/lib/api/generated/endpoints/mail/mail";
import { useGetAiSettings } from "@/lib/api/generated/endpoints/ai/ai";
import { settingsHref } from "@/lib/navigation";
import { AccountAvatar } from "./AccountAvatar";
import { useMailboxUpdate } from "./useMailboxUpdate";

const STATUS_LABEL = {
  ACTIVE: "Active",
  NEEDS_REAUTH: "Needs sign-in",
  DISABLED: "Disconnected",
} as const;

interface AccountsListProps {
  /** The Microsoft sign-in just completed (`?connected=true` from the OAuth callback). */
  connected?: boolean;
  /** Why the Microsoft sign-in failed (`?mailError=` from the OAuth callback). */
  mailError?: string;
}

export function AccountsList({
  connected = false,
  mailError,
}: AccountsListProps) {
  const queryClient = useQueryClient();
  const pathname = usePathname();
  const { data: accounts, isLoading, error } = useListMailAccounts();
  const { data: aiSettings } = useGetAiSettings();
  const readHidden = aiSettings?.readHiddenMailboxes ?? true;
  const update = useMailboxUpdate();

  const connectMutation = useConnectMailAccount({
    mutation: {
      onSuccess: (data) => {
        if (data.authorizeUrl) {
          window.location.assign(data.authorizeUrl);
        }
      },
    },
  });

  const syncMutation = useSyncMailAccount({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({
          queryKey: getListMailAccountsQueryKey(),
        });
      },
    },
  });

  const disconnectMutation = useDisconnectMailAccount({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({
          queryKey: getListMailAccountsQueryKey(),
        });
      },
    },
  });

  function handleConnect() {
    connectMutation.mutate({});
  }

  /** Signs in again with the same mailbox; the callback re-activates the existing account. */
  function handleReconnect(emailAddress: string) {
    connectMutation.mutate({ data: { emailHint: emailAddress } });
  }

  function handleSync(accountId: number) {
    syncMutation.mutate({ accountId });
  }

  function handleDisconnect(accountId: number) {
    disconnectMutation.mutate({ accountId });
  }

  const notice = mailError ? (
    <ErrorMessage message={mailError} />
  ) : connected ? (
    <Alert role="status">
      <CheckCircle2 aria-hidden="true" />
      <AlertDescription>
        Mailbox connected. The first sync has run.
      </AlertDescription>
    </Alert>
  ) : null;

  if (isLoading) {
    return (
      <div className="text-sm text-muted-foreground">
        Loading connected accounts...
      </div>
    );
  }

  if (error) {
    return <ErrorMessage message={error.message} />;
  }

  if (!accounts || accounts.length === 0) {
    return (
      <div className="space-y-6">
        {notice}
        <EmptyState
          icon={Mail}
          title="No accounts connected"
          description="Connect your Microsoft 365 or Outlook account to sync emails."
          action={
            <AppButton
              onClick={handleConnect}
              loading={connectMutation.isPending}
            >
              Connect Outlook
            </AppButton>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {notice}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-medium">Mailboxes</h2>
          <p className="text-sm text-muted-foreground">
            Manage your connected Microsoft Graph accounts and email
            synchronization.
          </p>
        </div>
        <AppButton
          onClick={handleConnect}
          loading={connectMutation.isPending}
          variant="outline"
        >
          Connect Another Account
        </AppButton>
      </div>

      <div className="grid gap-4">
        {accounts.map((account) => {
          const isActive = account.status === "ACTIVE";
          const isDisabled = account.status === "DISABLED";
          // the dev-only demo mailbox is imported once and never synced
          const isDemo = account.provider === "DEMO";
          const isSyncing =
            syncMutation.isPending &&
            syncMutation.variables?.accountId === account.id;
          const isDisconnecting =
            disconnectMutation.isPending &&
            disconnectMutation.variables?.accountId === account.id;
          const isReconnecting =
            connectMutation.isPending &&
            connectMutation.variables?.data?.emailHint === account.emailAddress;

          return (
            <Card key={account.id}>
              {/* The badge sits on the address line; the details stack under the address. */}
              <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
                <div className="flex min-w-0 items-center gap-3">
                  <AccountAvatar account={account} size={36} />
                  <div className="min-w-0 space-y-1">
                    <CardTitle className="truncate text-base">
                      {account.emailAddress}
                    </CardTitle>
                    {account.displayName ? (
                      <CardDescription>{account.displayName}</CardDescription>
                    ) : null}
                    {isDemo ? (
                      <CardDescription>
                        Fictional demo mailbox (dev only), never synced
                      </CardDescription>
                    ) : null}
                    <p className="text-xs text-muted-foreground">
                      {account.lastSyncedAt
                        ? `Last synced: ${new Date(account.lastSyncedAt).toLocaleString()}`
                        : "Not synced yet"}
                    </p>
                  </div>
                </div>
                <Badge
                  variant={
                    isDemo || isDisabled
                      ? "secondary"
                      : isActive
                        ? "default"
                        : "destructive"
                  }
                  className="mt-0.5 shrink-0 gap-1"
                >
                  {isDemo ? (
                    <FlaskConical className="size-3" />
                  ) : isActive ? (
                    <CheckCircle2 className="size-3" />
                  ) : (
                    <AlertCircle className="size-3" />
                  )}
                  {isDemo && isActive ? "Demo" : STATUS_LABEL[account.status]}
                </Badge>
              </CardHeader>
              <CardContent className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 pt-4">
                {/* One row: the checkbox and the buttons share a center line. */}
                <div className="grid gap-2">
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      className="size-4 shrink-0 accent-primary disabled:cursor-not-allowed disabled:opacity-50"
                      checked={account.hidden}
                      disabled={update.isPending && update.variables?.accountId === account.id}
                      onChange={(event) => update.mutate({ accountId: account.id, data: { hidden: event.target.checked } })}
                    />
                    <span className="grid gap-0.5">
                      <span>Hide in contor</span>
                      {account.hidden ? (
                        <span className="text-xs text-muted-foreground">
                          {readHidden ? "Its mail is not shown anywhere, but the AI still reads it. " : "Its mail is not shown anywhere, and the AI skips it. "}
                          <Link href={settingsHref(pathname, "general")} replace scroll={false} className="font-medium text-foreground underline-offset-4 hover:underline">
                            Settings › General
                          </Link>
                        </span>
                      ) : null}
                    </span>
                  </label>
                </div>
                <div className="flex gap-2">
                  {isDemo ? null : isActive ? (
                    <AppButton
                      size="sm"
                      variant="outline"
                      onClick={() => handleSync(account.id)}
                      loading={isSyncing}
                    >
                      <RefreshCw className="size-3.5" />
                      Sync Now
                    </AppButton>
                  ) : (
                    <AppButton
                      size="sm"
                      variant="outline"
                      onClick={() => handleReconnect(account.emailAddress)}
                      loading={isReconnecting}
                    >
                      <RotateCcw className="size-3.5" />
                      Reconnect
                    </AppButton>
                  )}
                  {isDisabled ? null : (
                    <AppButton
                      size="sm"
                      variant="destructive"
                      onClick={() => handleDisconnect(account.id)}
                      loading={isDisconnecting}
                    >
                      <Unlink className="size-3.5 text-destructive" />
                      Disconnect
                    </AppButton>
                  )}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

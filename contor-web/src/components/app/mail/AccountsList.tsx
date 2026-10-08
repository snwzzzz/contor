"use client";

import { useQueryClient } from "@tanstack/react-query";
import {
  AlertCircle,
  CheckCircle2,
  Mail,
  RefreshCw,
  RotateCcw,
  Unlink,
} from "lucide-react";
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
import { AccountAvatar } from "./AccountAvatar";

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
  const { data: accounts, isLoading, error } = useListMailAccounts();

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
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <div className="flex min-w-0 items-center gap-3">
                  <AccountAvatar account={account} size={36} />
                  <div className="min-w-0 space-y-1">
                    <CardTitle className="truncate text-base">
                      {account.emailAddress}
                    </CardTitle>
                    {account.displayName ? (
                      <CardDescription>{account.displayName}</CardDescription>
                    ) : null}
                  </div>
                </div>
                <Badge
                  variant={
                    isActive
                      ? "default"
                      : isDisabled
                        ? "secondary"
                        : "destructive"
                  }
                  className="gap-1"
                >
                  {isActive ? (
                    <CheckCircle2 className="size-3" />
                  ) : (
                    <AlertCircle className="size-3" />
                  )}
                  {STATUS_LABEL[account.status]}
                </Badge>
              </CardHeader>
              <CardContent className="flex items-center justify-between pt-4">
                <div className="text-xs text-muted-foreground">
                  {account.lastSyncedAt
                    ? `Last synced: ${new Date(account.lastSyncedAt).toLocaleString()}`
                    : "Not synced yet"}
                </div>
                <div className="flex gap-2">
                  {isActive ? (
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

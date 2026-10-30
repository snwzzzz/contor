"use client";

import { useQueryClient } from "@tanstack/react-query";
import { RefreshCw, Table2 } from "lucide-react";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { useListMailAccounts, useSyncMailAccount } from "@/lib/api/generated/endpoints/mail/mail";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";
import { collectionHref, SETTINGS_NAV, settingsHref, type SettingsSection } from "@/lib/navigation";
import { COMMAND, COMMAND_LABEL, CommandLink } from "./PageCommands";

/** Reloads everything shown on the page. */
export function RefreshCommand() {
  const queryClient = useQueryClient();
  const [refreshing, setRefreshing] = useState(false);

  async function refresh() {
    setRefreshing(true);
    try {
      await queryClient.invalidateQueries();
    } finally {
      setRefreshing(false);
    }
  }

  return (
    <AppButton variant="ghost" size="sm" className={COMMAND} loading={refreshing} onClick={refresh}>
      {refreshing ? null : <RefreshCw aria-hidden="true" data-icon="inline-start" />}
      <span className={COMMAND_LABEL}>Refresh</span>
    </AppButton>
  );
}

/** Asks every active mailbox to sync now, then refreshes the page. */
export function SyncMailCommand() {
  const queryClient = useQueryClient();
  const { data: accounts } = useListMailAccounts();
  const syncAccount = useSyncMailAccount();
  const [syncing, setSyncing] = useState(false);
  const active = (accounts ?? []).filter((account) => account.status === "ACTIVE");

  async function sync() {
    setSyncing(true);
    try {
      await Promise.all(active.map((account) => syncAccount.mutateAsync({ accountId: account.id })));
      await queryClient.invalidateQueries();
    } catch {
      // A failed sync is already shown as a toast (query client); nothing else to do here.
    } finally {
      setSyncing(false);
    }
  }

  return (
    <AppButton variant="ghost" size="sm" className={COMMAND} loading={syncing} disabled={active.length === 0} onClick={sync}>
      {syncing ? null : <RefreshCw aria-hidden="true" data-icon="inline-start" />}
      <span className={COMMAND_LABEL}>Sync mail</span>
    </AppButton>
  );
}

/** Opens a section of the settings modal over the current page (its icon and, by default, its name). */
export function SettingsCommand({ section, label }: { section: SettingsSection; label?: string }) {
  const pathname = usePathname();
  const entry = SETTINGS_NAV.find((item) => item.section === section);
  if (!entry) return null;
  return <CommandLink href={settingsHref(pathname, section)} icon={entry.icon} label={label ?? entry.title} overlay />;
}

/** The generic table of a system collection (`events`, `tasks`): all fields, filters and reports. */
export function CollectionTableCommand({ slug }: { slug: string }) {
  const { collection } = useSystemCollection(slug);
  if (!collection) return null;
  return <CommandLink href={collectionHref(collection.id)} icon={Table2} label="Table view" />;
}

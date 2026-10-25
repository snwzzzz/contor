import type { Metadata } from "next";
import { InboxView } from "@/components/app/mail/InboxView";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { RefreshCommand, SettingsCommand, SyncMailCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Inbox" };

const SECTION = navItem("/inbox");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Inbox"
        description="Synced emails, newest first."
        actions={
          <PageCommands>
            <RefreshCommand />
            <SyncMailCommand />
            <SettingsCommand section="accounts" label="Mailboxes" />
            <CommandLink href="/proposals" icon={navItem("/proposals").icon} label="Proposals" />
          </PageCommands>
        }
      />
      <InboxView />
    </>
  );
}

import { Tags } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { SettingsCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Rules & Labels" };

const SECTION = navItem("/rules");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Rules & Labels"
        description="Deterministic automation and labels for mail."
        actions={
          <PageCommands>
            <CommandLink href="/inbox" icon={navItem("/inbox").icon} label="Inbox" />
            <SettingsCommand section="accounts" label="Mailboxes" />
          </PageCommands>
        }
      />
      <EmptyState icon={Tags} title="No rules yet" description="Rule and label management arrives in a later phase." />
    </>
  );
}

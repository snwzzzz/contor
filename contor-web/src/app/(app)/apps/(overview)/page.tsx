import type { Metadata } from "next";
import { AddAppCommand } from "@/components/app/apps/AppCommands";
import { AppsOverview } from "@/components/app/apps/AppsOverview";
import { RefreshCommand, SettingsCommand } from "@/components/app/PageCommandButtons";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Apps" };

const SECTION = navItem("/apps");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Apps"
        description="The HTTP APIs you connected. contor calls them now and then, only reads, and the assistant learns from what comes back."
        actions={
          <PageCommands>
            <RefreshCommand />
            <AddAppCommand />
            <CommandLink href="/proposals" icon={navItem("/proposals").icon} label="Proposals" />
            <SettingsCommand section="autonomy" />
          </PageCommands>
        }
      />
      <AppsOverview />
    </>
  );
}

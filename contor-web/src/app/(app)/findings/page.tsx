import { Database } from "lucide-react";
import type { Metadata } from "next";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { RefreshCommand, SettingsCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";
import { FindingsList } from "@/components/app/findings/FindingsList";

export const metadata: Metadata = { title: "Findings" };

const SECTION = navItem("/findings");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Findings"
        description="People, organizations and records the assistant found in your mail. Keep what is right."
        actions={
          <PageCommands>
            <RefreshCommand />
            <CommandLink href="/proposals" icon={navItem("/proposals").icon} label="Proposals" />
            <CommandLink href="/collections" icon={Database} label="Collections" />
            <SettingsCommand section="autonomy" />
          </PageCommands>
        }
      />
      <FindingsList />
    </>
  );
}

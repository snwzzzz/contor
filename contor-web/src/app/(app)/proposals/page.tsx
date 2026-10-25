import type { Metadata } from "next";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { RefreshCommand, SettingsCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";
import { ProposalList } from "@/components/app/proposals/ProposalList";

export const metadata: Metadata = { title: "Proposals" };

const SECTION = navItem("/proposals");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Proposals"
        description="Things the assistant wants to do for you: calendar entries, to-dos, new categories."
        actions={
          <PageCommands>
            <RefreshCommand />
            <CommandLink href="/findings" icon={navItem("/findings").icon} label="Findings" />
            <SettingsCommand section="autonomy" />
            <SettingsCommand section="ai" />
          </PageCommands>
        }
      />
      <ProposalList />
    </>
  );
}

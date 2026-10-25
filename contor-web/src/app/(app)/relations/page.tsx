import { Database, Network } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Relations" };

const SECTION = navItem("/relations");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Relations"
        description="How records are connected."
        actions={
          <PageCommands>
            <CommandLink href="/collections" icon={Database} label="Collections" />
            <CommandLink href="/queries" icon={navItem("/queries").icon} label="Queries" />
          </PageCommands>
        }
      />
      <EmptyState
        icon={Network}
        title="Graph view coming later"
        description="A graph view of people, organizations and your records arrives in a later phase."
      />
    </>
  );
}

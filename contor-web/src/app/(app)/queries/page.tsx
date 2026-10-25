import { Database, ListFilter } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Queries" };

const SECTION = navItem("/queries");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Queries"
        description="Saved views across your collections."
        actions={
          <PageCommands>
            <CommandLink href="/collections" icon={Database} label="Collections" />
            <CommandLink href="/relations" icon={navItem("/relations").icon} label="Relations" />
          </PageCommands>
        }
      />
      <EmptyState icon={ListFilter} title="No queries yet" description="Saved queries and the query builder arrive in a later phase." />
    </>
  );
}

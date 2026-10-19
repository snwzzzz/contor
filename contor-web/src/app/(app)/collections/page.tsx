import { Database, FolderPlus } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem, NEW_COLLECTION_HREF } from "@/lib/navigation";

export const metadata: Metadata = { title: "Collections" };

export default function Page() {
  return (
    <>
      <PageHeader
        title="Collections"
        description="Your own data structures: deliveries, orders, people and more."
        actions={
          <PageCommands>
            <CommandLink href={NEW_COLLECTION_HREF} icon={FolderPlus} label="New collection" />
            <CommandLink href="/relations" icon={navItem("/relations").icon} label="Relations" />
            <CommandLink href="/queries" icon={navItem("/queries").icon} label="Queries" />
          </PageCommands>
        }
      />
      <EmptyState
        icon={Database}
        title="Pick a collection"
        description="Choose a collection in the sidebar. The collection overview arrives in the next step."
      />
    </>
  );
}

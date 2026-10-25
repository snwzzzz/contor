import { FolderPlus } from "lucide-react";
import type { Metadata } from "next";
import { CollectionsOverview } from "@/components/app/collections/CollectionsOverview";
import { RefreshCommand } from "@/components/app/PageCommandButtons";
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
            <RefreshCommand />
            <CommandLink href={NEW_COLLECTION_HREF} icon={FolderPlus} label="New collection" />
            <CommandLink href="/relations" icon={navItem("/relations").icon} label="Relations" />
            <CommandLink href="/queries" icon={navItem("/queries").icon} label="Queries" />
          </PageCommands>
        }
      />
      <CollectionsOverview />
    </>
  );
}

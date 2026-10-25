import { Plus } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { COLLECTIONS_CRUMB, PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "New collection" };

export default function Page() {
  return (
    <>
      <PageHeader trail={[COLLECTIONS_CRUMB]} title="New collection" description="Define a collection and its fields." />
      <EmptyState icon={Plus} title="Coming in the next step" description="The collection and schema editor arrives in the next step." />
    </>
  );
}

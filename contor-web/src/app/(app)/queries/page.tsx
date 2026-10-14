import { ListFilter } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Queries" };

const SECTION = navItem("/queries");

export default function Page() {
  return (
    <>
      <PageHeader icon={SECTION.icon} tint={SECTION.tint} title="Queries" description="Saved views across your collections." />
      <EmptyState icon={ListFilter} title="No queries yet" description="Saved queries and the query builder arrive in a later phase." />
    </>
  );
}

import { Tags } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Rules & Labels" };

const SECTION = navItem("/rules");

export default function Page() {
  return (
    <>
      <PageHeader icon={SECTION.icon} tint={SECTION.tint} title="Rules & Labels" description="Deterministic automation and labels for mail." />
      <EmptyState icon={Tags} title="No rules yet" description="Rule and label management arrives in a later phase." />
    </>
  );
}

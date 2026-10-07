import { Tags } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Rules & Labels" };

export default function Page() {
  return (
    <>
      <PageHeader title="Rules & Labels" description="Deterministic automation and labels for mail." />
      <EmptyState icon={Tags} title="No rules yet" description="Rule and label management arrives in a later phase." />
    </>
  );
}

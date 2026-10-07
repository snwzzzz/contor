import { Network } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Relations" };

export default function Page() {
  return (
    <>
      <PageHeader title="Relations" description="How records are connected." />
      <EmptyState icon={Network} title="Graph view coming later" description="A graph view of people, organizations and your records arrives in a later phase." />
    </>
  );
}

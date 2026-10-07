import { Sparkles } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Proposals" };

export default function Page() {
  return (
    <>
      <PageHeader title="Proposals" description="Changes the AI suggests, waiting for your decision." />
      <EmptyState icon={Sparkles} title="Nothing to review" description="Proposals appear once mail processing runs (next phase). You approve, edit or reject each one here." />
    </>
  );
}

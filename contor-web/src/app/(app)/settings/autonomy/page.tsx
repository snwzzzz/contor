import { Bot } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Autonomy" };

export default function Page() {
  return (
    <>
      <PageHeader title="Autonomy" description="How much the AI may do without asking." />
      <EmptyState icon={Bot} title="Everything is a suggestion" description="Until autonomy settings arrive, every AI action needs your approval." />
    </>
  );
}

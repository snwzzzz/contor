import { KeyRound } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Accounts" };

export default function Page() {
  return (
    <>
      <PageHeader title="Accounts" description="Connected mailboxes." />
      <EmptyState icon={KeyRound} title="No accounts connected" description="Connecting Outlook (Microsoft Graph) arrives in the next phase." />
    </>
  );
}

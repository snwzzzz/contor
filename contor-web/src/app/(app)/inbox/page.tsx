import { Inbox } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Inbox" };

export default function Page() {
  return (
    <>
      <PageHeader title="Inbox" description="Synced emails, newest first." />
      <EmptyState icon={Inbox} title="No mailbox connected yet" description="Outlook sync arrives in the next phase. Emails, labels and AI processing state will show up here." />
    </>
  );
}

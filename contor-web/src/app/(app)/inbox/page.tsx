import type { Metadata } from "next";
import { InboxView } from "@/components/app/mail/InboxView";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Inbox" };

export default function Page() {
  return (
    <>
      <PageHeader title="Inbox" description="Synced emails, newest first." />
      <InboxView />
    </>
  );
}

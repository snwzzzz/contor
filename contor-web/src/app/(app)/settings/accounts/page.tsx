import type { Metadata } from "next";
import { AccountsList } from "@/components/app/mail/AccountsList";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Accounts" };

export default function Page() {
  return (
    <>
      <PageHeader title="Accounts" description="Connected mailboxes." />
      <AccountsList />
    </>
  );
}

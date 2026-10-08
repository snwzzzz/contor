import type { Metadata } from "next";
import { AccountsList } from "@/components/app/mail/AccountsList";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Accounts" };

/** `connected` / `mailError` are set by the backend's Microsoft OAuth callback when it forwards back here. */
export default async function Page({ searchParams }: PageProps<"/settings/accounts">) {
  const { connected, mailError } = await searchParams;

  return (
    <>
      <PageHeader title="Accounts" description="Connected mailboxes." />
      <AccountsList
        connected={connected === "true"}
        mailError={typeof mailError === "string" ? mailError : undefined}
      />
    </>
  );
}
